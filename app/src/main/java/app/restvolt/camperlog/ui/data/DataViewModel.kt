package app.restvolt.camperlog.ui.data

import androidx.lifecycle.ViewModel
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

/** Datenverwaltung: Export und Import der gespeicherten Touren, Kurse und Hauptwährung. */
class DataViewModel(
    private val repository: TourRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    /** Alle Touren in chronologischer Reihenfolge für den CSV-Export. */
    suspend fun toursForExport(): List<Tour> = repository.allTours()

    /** Vollständige Sicherung des aktuellen Stands als JSON. */
    suspend fun backupJson(): String {
        val backup = Backup(
            exportedAt = clock(),
            mainCurrency = exchangeRates.observeMainCurrency().first(),
            rates = exchangeRates.observeRates().first(),
            tours = repository.allTours(),
        )
        return withContext(Dispatchers.Default) { encodeBackup(backup) }
    }
}
