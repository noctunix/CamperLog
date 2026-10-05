package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.BackupVehicle
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.Tour
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Spielt Sicherungen in einer einzigen Datenbank-Transaktion ein: Bei einem Fehler bleibt alles beim Alten.
 *
 * Zeitstempel und Datumswerte aus der Zukunft – etwa von einem Gerät mit falsch gestellter Uhr – werden
 * auf den Importzeitpunkt begrenzt. Sonst würden solche Einträge bei jedem Zusammenführen gegen
 * spätere lokale Änderungen gewinnen.
 */
class RoomBackupImporter(
    private val database: CamperLogDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) : BackupImporter {

    override suspend fun import(backup: Backup, mode: ImportMode): ImportResult = database.withTransaction {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val vehicleDao = database.vehicleDao()
        val logDao = database.logDao()
        val tourDao = database.tourDao()
        val rateDao = database.exchangeRateDao()

        val importedVehicles = backup.vehicles.map { it.notAfter(now, today) }

        if (mode == ImportMode.REPLACE) {
            tourDao.deleteAll()
            vehicleDao.deleteAllRepairs()
            logDao.deleteAll()
            vehicleDao.deleteAllVehicles()
            rateDao.deleteAllRates()
            rateDao.setMainCurrency(backup.mainCurrency.currencyCode)
        }

        // --- Fahrzeuge ---
        val localIdByVehicleUuid = LinkedHashMap<String, Long>()
        var addedVehicles = 0
        var updatedVehicles = 0
        if (mode == ImportMode.REPLACE) {
            for (backupVehicle in importedVehicles) {
                val id = vehicleDao.insert(backupVehicle.vehicle.copy(id = 0).toEntity())
                localIdByVehicleUuid[backupVehicle.vehicle.uuid] = id
                addedVehicles++
            }
            if (importedVehicles.isEmpty()) {
                // Sicherungen der Formatversion 1 kennen noch keine Fahrzeuge; es muss trotzdem genau
                // eines übrig bleiben, dem alle Touren zugeordnet werden.
                vehicleDao.insertDefaultIfNone(newUuid(), now.toEpochMilli())
            }
        } else {
            val storedVehicles = vehicleDao.getVersions().associateBy(VehicleVersionRow::uuid)
            for (backupVehicle in importedVehicles) {
                val vehicle = backupVehicle.vehicle
                val existing = storedVehicles[vehicle.uuid]
                when {
                    existing == null -> {
                        val id = vehicleDao.insert(vehicle.copy(id = 0).toEntity())
                        localIdByVehicleUuid[vehicle.uuid] = id
                        addedVehicles++
                    }
                    vehicle.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        vehicleDao.update(vehicle.copy(id = existing.id).toEntity())
                        localIdByVehicleUuid[vehicle.uuid] = existing.id
                        updatedVehicles++
                    }
                    else -> localIdByVehicleUuid[vehicle.uuid] = existing.id
                }
            }
        }

        // --- Reparaturen ---
        val importedRepairs = importedVehicles.flatMap { backupVehicle ->
            val localVehicleId = checkNotNull(localIdByVehicleUuid[backupVehicle.vehicle.uuid])
            backupVehicle.repairs.map { it.copy(vehicleId = localVehicleId) }
        }
        var addedRepairs = 0
        var updatedRepairs = 0
        if (mode == ImportMode.REPLACE) {
            importedRepairs.forEach { vehicleDao.insertRepair(it.copy(id = 0).toEntity()); addedRepairs++ }
        } else {
            val storedRepairs = vehicleDao.getRepairVersions().associateBy(RepairVersionRow::uuid)
            for (repair in importedRepairs) {
                val existing = storedRepairs[repair.uuid]
                when {
                    existing == null -> {
                        vehicleDao.insertRepair(repair.copy(id = 0).toEntity())
                        addedRepairs++
                    }
                    repair.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        vehicleDao.updateRepair(repair.copy(id = existing.id).toEntity())
                        updatedRepairs++
                    }
                }
            }
        }

        // --- Bordbuch-Einträge: unveränderlich, daher nur anlegen, wenn die uuid noch unbekannt ist ---
        val importedLogEntries = importedVehicles.flatMap { backupVehicle ->
            val localVehicleId = checkNotNull(localIdByVehicleUuid[backupVehicle.vehicle.uuid])
            backupVehicle.logEntries.map { it.copy(vehicleId = localVehicleId) }
        }
        var addedLogEntries = 0
        if (mode == ImportMode.REPLACE) {
            importedLogEntries.forEach { logDao.insert(it.copy(id = 0).toEntity()); addedLogEntries++ }
        } else {
            val storedLogEntryUuids = logDao.getUuids().toHashSet()
            for (entry in importedLogEntries) {
                if (storedLogEntryUuids.add(entry.uuid)) {
                    logDao.insert(entry.copy(id = 0).toEntity())
                    addedLogEntries++
                }
            }
        }

        // --- Aktuelles Fahrzeug: bleibt beim Zusammenführen unverändert ---
        if (mode == ImportMode.REPLACE) {
            val currentId = backup.currentVehicleUuid?.let { localIdByVehicleUuid[it] }
                ?: localIdByVehicleUuid.values.firstOrNull()
                ?: vehicleDao.lowestVehicleId()
            currentId?.let { vehicleDao.setCurrentVehicleId(it) }
        }

        // --- Touren: ohne Fahrzeugbezug (Formatversion 1) bekommen das aktuelle Fahrzeug ---
        val needsDefaultVehicle = backup.tours.any { backup.tourVehicleUuid[it.uuid] == null }
        val defaultVehicleId = if (needsDefaultVehicle) vehicleDao.resolveCurrentVehicleId(now, newUuid) else null
        val importedTours = backup.tours.map { tour ->
            val targetVehicleId = backup.tourVehicleUuid[tour.uuid]
                ?.let { uuid -> checkNotNull(localIdByVehicleUuid[uuid]) }
                ?: checkNotNull(defaultVehicleId)
            tour.notAfter(now).copy(vehicleId = targetVehicleId)
        }
        val importedRates = backup.rates.map { if (it.date > today) it.copy(date = today) else it }

        val storedTours = tourDao.getVersions().associateBy(TourVersionRow::uuid)
        var added = 0
        var updated = 0
        for (tour in importedTours) {
            val existing = storedTours[tour.uuid]
            when {
                existing == null -> {
                    tourDao.insertWithCosts(tour.copy(id = 0).toEntity(), tour.toCostEntities())
                    added++
                }
                tour.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                    val replacement = tour.copy(id = existing.id)
                    tourDao.updateWithCosts(replacement.toEntity(), replacement.toCostEntities())
                    updated++
                }
            }
        }

        val storedRateDates = rateDao.getRates().associate { it.currency to LocalDate.parse(it.rateDate) }
        val newerRates = importedRates.filter { rate -> storedRateDates[rate.currency.currencyCode]?.let { rate.date > it } ?: true }
        newerRates.map(ExchangeRate::toEntity).forEach { rateDao.upsertRate(it) }

        // Kostensummen, die nicht mehr in 64 Bit passen, würden jede spätere Übersicht scheitern lassen.
        // SQLite wirft dann hier, und die Transaktion wird vollständig zurückgerollt.
        tourDao.getCostSums()

        ImportResult(
            addedTours = added,
            updatedTours = updated,
            unchangedTours = backup.tours.size - added - updated,
            importedRates = newerRates.size,
            addedVehicles = addedVehicles,
            updatedVehicles = updatedVehicles,
            addedRepairs = addedRepairs,
            updatedRepairs = updatedRepairs,
            addedLogEntries = addedLogEntries,
        )
    }
}

private fun Tour.notAfter(now: Instant): Tour = copy(
    createdAt = minOf(createdAt, now),
    updatedAt = minOf(updatedAt, now),
)

private fun BackupVehicle.notAfter(now: Instant, today: LocalDate): BackupVehicle = copy(
    vehicle = vehicle.copy(createdAt = minOf(vehicle.createdAt, now), updatedAt = minOf(vehicle.updatedAt, now)),
    repairs = repairs.map {
        it.copy(date = minOf(it.date, today), createdAt = minOf(it.createdAt, now), updatedAt = minOf(it.updatedAt, now))
    },
    logEntries = logEntries.map { it.copy(date = minOf(it.date, today), createdAt = minOf(it.createdAt, now)) },
)
