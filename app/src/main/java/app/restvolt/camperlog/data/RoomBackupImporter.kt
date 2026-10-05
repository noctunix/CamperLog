package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
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
 * Zeitstempel und Kursdaten aus der Zukunft – etwa von einem Gerät mit falsch gestellter Uhr – werden
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
        val vehicles = database.vehicleDao()
        // Das Sicherungsformat dieser Version kennt noch keine Fahrzeuge; ihre Touren bekommen alle
        // das aktuelle Fahrzeug zugewiesen, damit Sicherungen aus Version 1 importierbar bleiben.
        val defaultVehicleId = if (backup.tours.any { it.vehicleId == 0L }) vehicles.resolveCurrentVehicleId(now, newUuid) else null
        val importedTours = backup.tours.map { tour ->
            tour.notAfter(now).let { if (it.vehicleId == 0L) it.copy(vehicleId = checkNotNull(defaultVehicleId)) else it }
        }
        val importedRates = backup.rates.map { if (it.date > today) it.copy(date = today) else it }
        val tours = database.tourDao()
        val rates = database.exchangeRateDao()
        if (mode == ImportMode.REPLACE) {
            tours.deleteAll()
            rates.deleteAllRates()
            rates.setMainCurrency(backup.mainCurrency.currencyCode)
        }

        val stored = tours.getVersions().associateBy(TourVersionRow::uuid)
        var added = 0
        var updated = 0
        for (tour in importedTours) {
            val existing = stored[tour.uuid]
            when {
                existing == null -> {
                    tours.insertWithCosts(tour.copy(id = 0).toEntity(), tour.toCostEntities())
                    added++
                }
                tour.updatedAt.toEpochMilli() > existing.updatedAtMillis -> {
                    val replacement = tour.copy(id = existing.id)
                    tours.updateWithCosts(replacement.toEntity(), replacement.toCostEntities())
                    updated++
                }
            }
        }

        val storedRateDates = rates.getRates().associate { it.currency to LocalDate.parse(it.rateDate) }
        val newerRates = importedRates.filter { rate -> storedRateDates[rate.currency.currencyCode]?.let { rate.date > it } ?: true }
        newerRates.map(ExchangeRate::toEntity).forEach { rates.upsertRate(it) }

        // Kostensummen, die nicht mehr in 64 Bit passen, würden jede spätere Übersicht scheitern lassen.
        // SQLite wirft dann hier, und die Transaktion wird vollständig zurückgerollt.
        tours.getCostSums()

        ImportResult(
            addedTours = added,
            updatedTours = updated,
            unchangedTours = backup.tours.size - added - updated,
            importedRates = newerRates.size,
        )
    }
}

private fun Tour.notAfter(now: Instant): Tour = copy(
    createdAt = minOf(createdAt, now),
    updatedAt = minOf(updatedAt, now),
)
