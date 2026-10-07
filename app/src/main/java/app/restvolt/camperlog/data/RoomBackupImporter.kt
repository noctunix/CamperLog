package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.BackupVehicle
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Spielt Sicherungen in einer einzigen Datenbank-Transaktion ein: Bei einem Fehler bleibt alles beim Alten.
 * Betrifft nur die Datenbank - liegen zu importierenden Anhängen Dateien aus einer ZIP-Sicherung bei,
 * verschiebt sie der Aufrufer erst nach einem erfolgreichen Aufruf von [import] an ihren endgültigen
 * Platz (siehe `commitStagedAttachmentFiles`, aufgerufen von `DataViewModel.startImport`).
 *
 * Zeitstempel und Datumswerte aus der Zukunft – etwa von einem Gerät mit falsch gestellter Uhr – werden
 * auf den Importzeitpunkt begrenzt. Sonst würden solche Einträge bei jedem Zusammenführen gegen
 * spätere lokale Änderungen gewinnen. [VehicleDocument.expiryDate] ist davon ausgenommen: ein
 * Ablaufdatum in der Zukunft ist dort der Normalfall, nicht ein Zeichen einer falschen Uhr.
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
        val stationDao = database.stationDao()
        val rateDao = database.exchangeRateDao()
        val documentDao = database.vehicleDocumentDao()
        val diaryEntryDao = database.diaryEntryDao()
        val attachmentDao = database.attachmentDao()

        val importedVehicles = backup.vehicles.map { it.notAfter(now, today) }

        if (mode == ImportMode.REPLACE) {
            attachmentDao.deleteAll()
            documentDao.deleteAll()
            diaryEntryDao.deleteAll()
            stationDao.deleteAll()
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
            val placeholderId = if (importedVehicles.isNotEmpty()) untouchedSoleVehicleId(vehicleDao, logDao, stationDao) else null
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
            // Beim Einspielen auf einem neuen Gerät bliebe sonst das automatisch angelegte, leere
            // Fahrzeug als zweites neben dem aus der Sicherung stehen.
            if (placeholderId != null && placeholderId !in localIdByVehicleUuid.values) {
                vehicleDao.deleteById(placeholderId)
                val currentId = backup.currentVehicleUuid?.let { localIdByVehicleUuid[it] } ?: localIdByVehicleUuid.values.first()
                vehicleDao.setCurrentVehicleId(currentId)
            }
        }

        // --- Reparaturen ---
        val importedRepairs = importedVehicles.flatMap { backupVehicle ->
            val localVehicleId = checkNotNull(localIdByVehicleUuid[backupVehicle.vehicle.uuid])
            backupVehicle.repairs.map { it.copy(vehicleId = localVehicleId) }
        }
        var addedRepairs = 0
        var updatedRepairs = 0
        val repairLocalIdByUuid = HashMap<String, Long>()
        if (mode == ImportMode.REPLACE) {
            importedRepairs.forEach { repair ->
                val id = vehicleDao.insertRepair(repair.copy(id = 0).toEntity())
                repairLocalIdByUuid[repair.uuid] = id
                addedRepairs++
            }
        } else {
            val storedRepairs = vehicleDao.getRepairVersions().associateBy(RepairVersionRow::uuid)
            for (repair in importedRepairs) {
                val existing = storedRepairs[repair.uuid]
                when {
                    existing == null -> {
                        val id = vehicleDao.insertRepair(repair.copy(id = 0).toEntity())
                        repairLocalIdByUuid[repair.uuid] = id
                        addedRepairs++
                    }
                    repair.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        vehicleDao.updateRepair(repair.copy(id = existing.id).toEntity())
                        repairLocalIdByUuid[repair.uuid] = existing.id
                        updatedRepairs++
                    }
                    else -> repairLocalIdByUuid[repair.uuid] = existing.id
                }
            }
        }

        // --- Aktuelles Fahrzeug: bleibt beim Zusammenführen unverändert, außer das leere Fahrzeug wurde ersetzt ---
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
        val tourLocalIdByUuid = HashMap<String, Long>()
        var added = 0
        var updated = 0
        for (tour in importedTours) {
            val existing = storedTours[tour.uuid]
            val localId = when {
                existing == null -> {
                    val withId = tour.copy(id = 0)
                    val id = tourDao.insertWithCosts(withId.toEntity(), withId.toCostEntities(), withId.toCountryEntities())
                    added++
                    id
                }
                tour.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                    val replacement = tour.copy(id = existing.id)
                    tourDao.updateWithCosts(replacement.toEntity(), replacement.toCostEntities(), replacement.toCountryEntities())
                    updated++
                    existing.id
                }
                else -> existing.id
            }
            tourLocalIdByUuid[tour.uuid] = localId
        }

        // --- Stationen: Fahrzeug kommt aus stationVehicleUuid, sonst (aus einer alten Tour abgeleitet) vom Fahrzeug ihrer Tour ---
        val tourVehicleIdByUuid = importedTours.associate { it.uuid to it.vehicleId }
        val importedStations = backup.stations.map { station ->
            val vehicleId = backup.stationVehicleUuid[station.uuid]?.let { uuid -> checkNotNull(localIdByVehicleUuid[uuid]) }
                ?: backup.stationTourUuid[station.uuid]?.let { uuid -> checkNotNull(tourVehicleIdByUuid[uuid]) }
                ?: error("Station ${station.uuid} ohne Fahrzeugbezug")
            val tourId = backup.stationTourUuid[station.uuid]?.let { uuid -> tourLocalIdByUuid[uuid] }
            station.notAfter(now, today).copy(vehicleId = vehicleId, tourId = tourId)
        }
        var addedStations = 0
        var updatedStations = 0
        val stationLocalIdByUuid = HashMap<String, Long>()
        if (mode == ImportMode.REPLACE) {
            importedStations.forEach { station ->
                val withId = station.copy(id = 0)
                stationLocalIdByUuid[station.uuid] = stationDao.insertWithCosts(withId.toEntity(), withId.toCostEntities())
                addedStations++
            }
        } else {
            val storedStations = stationDao.getVersions().associateBy(StationVersionRow::uuid)
            for (station in importedStations) {
                val existing = storedStations[station.uuid]
                val localId = when {
                    existing == null -> {
                        val withId = station.copy(id = 0)
                        val id = stationDao.insertWithCosts(withId.toEntity(), withId.toCostEntities())
                        addedStations++
                        id
                    }
                    station.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        val withId = station.copy(id = existing.id)
                        stationDao.updateWithCosts(withId.toEntity(), withId.toCostEntities())
                        updatedStations++
                        existing.id
                    }
                    else -> existing.id
                }
                stationLocalIdByUuid[station.uuid] = localId
            }
        }

        // --- Bordbuch-Einträge: unveränderlich außer der Stationsverknüpfung, daher nur anlegen, wenn die uuid noch unbekannt ist ---
        val importedLogEntries = importedVehicles.flatMap { backupVehicle ->
            val localVehicleId = checkNotNull(localIdByVehicleUuid[backupVehicle.vehicle.uuid])
            backupVehicle.logEntries.map { entry ->
                val stationId = backup.logEntryStationUuid[entry.uuid]?.let { uuid -> stationLocalIdByUuid[uuid] }
                entry.copy(vehicleId = localVehicleId, stationId = stationId)
            }
        }
        var addedLogEntries = 0
        val logEntryLocalIdByUuid = HashMap<String, Long>()
        if (mode == ImportMode.REPLACE) {
            importedLogEntries.forEach { entry ->
                val id = logDao.insert(entry.copy(id = 0).toEntity())
                logEntryLocalIdByUuid[entry.uuid] = id
                addedLogEntries++
            }
        } else {
            val storedLogEntryUuids = logDao.getUuids().toHashSet()
            for (entry in importedLogEntries) {
                if (storedLogEntryUuids.add(entry.uuid)) {
                    val id = logDao.insert(entry.copy(id = 0).toEntity())
                    logEntryLocalIdByUuid[entry.uuid] = id
                    addedLogEntries++
                }
            }
        }

        // --- Fahrzeugdokumente: wie Fahrzeuge über ihre UUID abgeglichen ---
        val importedDocuments = backup.documents.mapNotNull { backupDocument ->
            localIdByVehicleUuid[backupDocument.vehicleUuid]?.let { localVehicleId ->
                backupDocument.document.copy(vehicleId = localVehicleId).notAfter(now)
            }
        }
        var addedDocuments = 0
        var updatedDocuments = 0
        val documentLocalIdByUuid = HashMap<String, Long>()
        if (mode == ImportMode.REPLACE) {
            importedDocuments.forEach { document ->
                val id = documentDao.insert(document.copy(id = 0).toEntity())
                documentLocalIdByUuid[document.uuid] = id
                addedDocuments++
            }
        } else {
            val storedDocuments = documentDao.getVersions().associateBy(VehicleDocumentVersionRow::uuid)
            for (document in importedDocuments) {
                val existing = storedDocuments[document.uuid]
                when {
                    existing == null -> {
                        val id = documentDao.insert(document.copy(id = 0).toEntity())
                        documentLocalIdByUuid[document.uuid] = id
                        addedDocuments++
                    }
                    document.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        documentDao.update(document.copy(id = existing.id).toEntity())
                        documentLocalIdByUuid[document.uuid] = existing.id
                        updatedDocuments++
                    }
                    else -> documentLocalIdByUuid[document.uuid] = existing.id
                }
            }
        }

        // --- Tagebucheinträge: wie Fahrzeugdokumente über ihre UUID abgeglichen ---
        val importedDiaryEntries = backup.diaryEntries.mapNotNull { backupEntry ->
            tourLocalIdByUuid[backupEntry.tourUuid]?.let { localTourId ->
                backupEntry.entry.copy(tourId = localTourId).notAfter(now, today)
            }
        }
        var addedDiaryEntries = 0
        var updatedDiaryEntries = 0
        if (mode == ImportMode.REPLACE) {
            importedDiaryEntries.forEach { entry ->
                diaryEntryDao.insert(entry.copy(id = 0).toEntity())
                addedDiaryEntries++
            }
        } else {
            val storedDiaryEntries = diaryEntryDao.getVersions().associateBy(DiaryEntryVersionRow::uuid)
            for (entry in importedDiaryEntries) {
                val existing = storedDiaryEntries[entry.uuid]
                when {
                    existing == null -> {
                        diaryEntryDao.insert(entry.copy(id = 0).toEntity())
                        addedDiaryEntries++
                    }
                    entry.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                        diaryEntryDao.update(entry.copy(id = existing.id).toEntity())
                        updatedDiaryEntries++
                    }
                }
            }
        }

        // --- Anhänge: unveränderlich wie Bordbuch-Einträge, daher nur anlegen, wenn die uuid noch unbekannt ist ---
        val importedAttachments = backup.attachments.mapNotNull { backupAttachment ->
            val ownerId = backupAttachment.attachment.ownerId(
                backupAttachment.ownerUuid, stationLocalIdByUuid, repairLocalIdByUuid, logEntryLocalIdByUuid, documentLocalIdByUuid,
            ) ?: return@mapNotNull null
            backupAttachment.attachment.copy(ownerId = ownerId, createdAt = minOf(backupAttachment.attachment.createdAt, now))
        }
        var addedAttachments = 0
        if (mode == ImportMode.REPLACE) {
            importedAttachments.forEach { attachmentDao.insert(it.copy(id = 0).toEntity()); addedAttachments++ }
        } else {
            val storedAttachmentUuids = attachmentDao.getVersions().mapTo(HashSet()) { it.uuid }
            for (attachment in importedAttachments) {
                if (storedAttachmentUuids.add(attachment.uuid)) {
                    attachmentDao.insert(attachment.copy(id = 0).toEntity())
                    addedAttachments++
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
            addedStations = addedStations,
            updatedStations = updatedStations,
            addedDocuments = addedDocuments,
            updatedDocuments = updatedDocuments,
            addedDiaryEntries = addedDiaryEntries,
            updatedDiaryEntries = updatedDiaryEntries,
            addedAttachments = addedAttachments,
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

private fun Station.notAfter(now: Instant, today: LocalDate): Station = copy(
    date = minOf(date, today),
    createdAt = minOf(createdAt, now),
    updatedAt = minOf(updatedAt, now),
)

/** Begrenzt Anlage- und Änderungszeit wie [Tour.notAfter]; [VehicleDocument.expiryDate] bleibt unverändert, siehe Klassen-KDoc. */
private fun VehicleDocument.notAfter(now: Instant): VehicleDocument = copy(
    createdAt = minOf(createdAt, now),
    updatedAt = minOf(updatedAt, now),
)

private fun DiaryEntry.notAfter(now: Instant, today: LocalDate): DiaryEntry = copy(
    date = minOf(date, today),
    createdAt = minOf(createdAt, now),
    updatedAt = minOf(updatedAt, now),
)

/** Lokale id des Eintrags, zu dem [Attachment.ownerType] von [this] gehört, oder `null`, wenn dessen uuid beim Import verworfen wurde. */
private fun Attachment.ownerId(
    ownerUuid: String,
    stationLocalIdByUuid: Map<String, Long>,
    repairLocalIdByUuid: Map<String, Long>,
    logEntryLocalIdByUuid: Map<String, Long>,
    documentLocalIdByUuid: Map<String, Long>,
): Long? = when (ownerType) {
    AttachmentOwnerType.STATION -> stationLocalIdByUuid[ownerUuid]
    AttachmentOwnerType.REPAIR -> repairLocalIdByUuid[ownerUuid]
    AttachmentOwnerType.LOG_ENTRY -> logEntryLocalIdByUuid[ownerUuid]
    AttachmentOwnerType.VEHICLE_DOCUMENT -> documentLocalIdByUuid[ownerUuid]
}

/**
 * Die id des einzigen Fahrzeugs, falls es unberührt ist: ohne Angaben, Touren, Reparaturen,
 * Bordbuch-Einträge und Stationen – so, wie die App es beim ersten Start selbst anlegt.
 */
private suspend fun untouchedSoleVehicleId(vehicles: VehicleDao, logs: LogDao, stations: StationDao): Long? {
    val only = vehicles.getAll().singleOrNull() ?: return null
    val blank = Vehicle(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    val untouched = only.toDomain().copy(id = 0, uuid = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH) == blank
    return only.id.takeIf {
        untouched && vehicles.countToursForVehicle(it) == 0 && vehicles.countRepairsForVehicle(it) == 0 &&
            logs.countForVehicle(it) == 0 && stations.getAll().none { row -> row.station.vehicleId == it }
    }
}
