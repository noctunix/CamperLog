package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.domain.ExchangeRate
import java.time.LocalDate

/** Spielt Sicherungen in einer einzigen Datenbank-Transaktion ein: Bei einem Fehler bleibt alles beim Alten. */
class RoomBackupImporter(private val database: CamperLogDatabase) : BackupImporter {

    override suspend fun import(backup: Backup, mode: ImportMode): ImportResult = database.withTransaction {
        val tours = database.tourDao()
        val rates = database.exchangeRateDao()
        if (mode == ImportMode.REPLACE) {
            tours.deleteAll()
            rates.deleteAllRates()
            rates.upsertSettings(SettingsEntity(mainCurrency = backup.mainCurrency.currencyCode))
        }

        val stored = tours.getVersions().associateBy(TourVersionRow::uuid)
        var added = 0
        var updated = 0
        for (tour in backup.tours) {
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
        val newerRates = backup.rates.filter { rate -> storedRateDates[rate.currency.currencyCode]?.let { rate.date > it } ?: true }
        newerRates.map(ExchangeRate::toEntity).forEach { rates.upsertRate(it) }

        ImportResult(
            addedTours = added,
            updatedTours = updated,
            unchangedTours = backup.tours.size - added - updated,
            importedRates = newerRates.size,
        )
    }
}
