package app.restvolt.camperlog.ui.data

import androidx.lifecycle.ViewModel
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.backup.readBackup
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/** Geprüfte Sicherung, die auf die Entscheidung des Nutzers wartet; [existingTours] zählt die gespeicherten Touren. */
data class PendingImport(val backup: Backup, val existingTours: Int)

/** Datenverwaltung: Export und Import der gespeicherten Touren, Kurse und Hauptwährung. */
class DataViewModel(
    private val repository: TourRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val importer: BackupImporter,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)

    /** Eingelesene Sicherung für Vorschau und Bestätigung; übersteht Konfigurationswechsel. */
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

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

    /**
     * Liest und prüft eine Sicherung aus [open]; bei Erfolg steht sie danach in [pendingImport] bereit,
     * sonst kommt der Fehler samt Tournummer zurück.
     * Ein `null`-Strom gilt als unlesbare Datei.
     *
     * @throws IOException wenn die Datei nicht gelesen werden kann
     */
    suspend fun loadBackup(open: () -> InputStream?): BackupReadResult.Failure? {
        val result = withContext(Dispatchers.IO) {
            (open() ?: throw IOException("Datei nicht lesbar")).use(::readBackup)
        }
        return when (result) {
            is BackupReadResult.Failure -> result.also { _pendingImport.value = null }
            is BackupReadResult.Success -> {
                _pendingImport.value = PendingImport(result.backup, repository.allTours().size)
                null
            }
        }
    }

    /** Spielt die vorgemerkte Sicherung im Modus [mode] ein und verwirft sie danach. */
    suspend fun importPending(mode: ImportMode): ImportResult? {
        val pending = _pendingImport.value ?: return null
        val result = importer.import(pending.backup, mode)
        _pendingImport.value = null
        return result
    }

    /** Verwirft die vorgemerkte Sicherung, ohne etwas zu ändern. */
    fun cancelImport() {
        _pendingImport.value = null
    }
}
