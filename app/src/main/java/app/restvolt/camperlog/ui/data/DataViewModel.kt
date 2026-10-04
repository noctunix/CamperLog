package app.restvolt.camperlog.ui.data

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/**
 * Geprüfte Sicherung, die auf die Entscheidung des Nutzers wartet; [existingTours] zählt die gespeicherten Touren.
 * [running] ist gesetzt, solange der Import läuft, [failed] nach einem gescheiterten Versuch.
 */
data class PendingImport(
    val backup: Backup,
    val existingTours: Int,
    val running: Boolean = false,
    val failed: Boolean = false,
)

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

    private val _importResult = MutableStateFlow<ImportResult?>(null)

    /** Ergebnis des letzten Imports, bis der Screen es mit [importResultShown] quittiert. */
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

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

    /**
     * Spielt die vorgemerkte Sicherung im Modus [mode] ein. Läuft im [viewModelScope], damit ein
     * Konfigurationswechsel den Import weder abbricht noch doppelt startet. Bei Erfolg wird die
     * Sicherung verworfen und [importResult] gesetzt, bei einem Fehler bleibt sie mit [PendingImport.failed] stehen.
     */
    fun startImport(mode: ImportMode) {
        val pending = _pendingImport.value ?: return
        if (pending.running) return
        _pendingImport.value = pending.copy(running = true, failed = false)
        viewModelScope.launch {
            try {
                val result = importer.import(pending.backup, mode)
                _pendingImport.value = null
                _importResult.value = result
            } catch (_: SQLException) {
                _pendingImport.value = pending.copy(running = false, failed = true)
            } catch (_: IOException) {
                _pendingImport.value = pending.copy(running = false, failed = true)
            }
        }
    }

    /** Quittiert die Anzeige von [importResult]. */
    fun importResultShown() {
        _importResult.value = null
    }

    /** Verwirft die vorgemerkte Sicherung, ohne etwas zu ändern; während eines laufenden Imports wirkungslos. */
    fun cancelImport() {
        if (_pendingImport.value?.running != true) _pendingImport.value = null
    }
}
