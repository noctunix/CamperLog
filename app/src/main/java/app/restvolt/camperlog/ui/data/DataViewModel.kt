package app.restvolt.camperlog.ui.data

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.BackupZipReadResult
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.backup.backupZipSizeEstimate
import app.restvolt.camperlog.backup.buildBackup
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.backup.readBackup
import app.restvolt.camperlog.backup.readBackupZip
import app.restvolt.camperlog.backup.writeBackupZip
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.data.commitStagedAttachmentFiles
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.shouldIncludeFilesInAutoBackup
import app.restvolt.camperlog.share.CsvVocabulary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.Locale

/**
 * Geprüfte Sicherung, die auf die Entscheidung des Nutzers wartet; [existingTours] zählt die gespeicherten Touren.
 * [running] ist gesetzt, solange der Import läuft, [failed] nach einem gescheiterten Versuch.
 * [stagingDir] und [stagedFiles] sind nur bei einer ZIP-Sicherung gesetzt (siehe [DataFiles.newImportStagingDir]);
 * ihre Dateien werden erst nach einem erfolgreichen Import an ihren endgültigen Platz verschoben.
 */
data class PendingImport(
    val backup: Backup,
    val existingTours: Int,
    val running: Boolean = false,
    val failed: Boolean = false,
    val stagingDir: File? = null,
    val stagedFiles: Map<String, File> = emptyMap(),
)

/** Meldung für die Snackbar des Daten-Screens. */
sealed interface DataMessage {
    /** Fester Text ohne Platzhalter. */
    data class Text(@StringRes val text: Int) : DataMessage

    /** Import abgeschlossen. */
    data class Imported(val result: ImportResult) : DataMessage

    /** Gewählte Datei ist keine gültige Sicherung. */
    data class LoadFailed(val failure: BackupReadResult.Failure) : DataMessage

    /** Sicherung erfolgreich in den Sicherungsordner geschrieben; [folderName] für die Meldung. */
    data class BackedUpToFolder(val folderName: String) : DataMessage
}

/** Datei, die der Screen mit einer anderen App teilen soll; [uri] wie von [DataFiles] geliefert. */
sealed interface ShareRequest {
    val uri: String

    data class Csv(override val uri: String) : ShareRequest

    data class StationsCsv(override val uri: String) : ShareRequest

    data class Backup(override val uri: String) : ShareRequest

    /** Wie [Backup], aber als ZIP samt Fotos und Dokumenten. */
    data class BackupZip(override val uri: String) : ShareRequest
}

/**
 * Datenverwaltung: Export und Import der gespeicherten Touren, Fahrzeuge, Reparaturen, des
 * Bordbuchs, der Kurse, der Hauptwährung, der Fahrzeugdokumente, der Tagebucheinträge und ihrer
 * Anhänge. Alle Vorgänge
 * laufen im [viewModelScope] und überstehen so Konfigurationswechsel; Ergebnisse erscheinen in
 * [message] bzw. [share], bis der Screen sie quittiert.
 */
class DataViewModel(
    private val repository: TourRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val vehicles: VehicleRepository,
    private val logs: LogRepository,
    private val stations: StationRepository,
    private val documents: VehicleDocumentRepository,
    private val diaryEntries: DiaryEntryRepository,
    private val checklistTemplates: ChecklistTemplateRepository,
    private val checklists: ChecklistRepository,
    private val tracks: TrackRepository,
    private val attachments: AttachmentRepository,
    private val attachmentFileStore: AttachmentFileStore,
    private val importer: BackupImporter,
    private val files: DataFiles,
    private val folderWriter: BackupFolderWriter,
    /** Nach jeder erfolgreich geschriebenen Sicherung aufgerufen, um die Sicherungs-Erinnerung neu zu "scharfen". */
    private val onBackupSaved: (Instant) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
    private val background: CoroutineDispatcher = Dispatchers.IO,
    /** Vokabular der CSV-Exporte, nach der App-Sprache des Geräts. */
    private val vocabulary: () -> CsvVocabulary = { CsvVocabulary.fromLocale(Locale.getDefault()) },
) : ViewModel() {

    private val _busy = MutableStateFlow(false)

    /** Läuft gerade ein Export, eine Sicherung oder das Einlesen einer Datei? */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<DataMessage?>(null)

    /** Anzuzeigende Meldung, bis [messageShown] aufgerufen wird. */
    val message: StateFlow<DataMessage?> = _message.asStateFlow()

    private val _share = MutableStateFlow<ShareRequest?>(null)

    /** Zu teilende Datei, bis [shareHandled] aufgerufen wird. */
    val share: StateFlow<ShareRequest?> = _share.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)

    /** Eingelesene Sicherung für Vorschau und Bestätigung; übersteht Konfigurationswechsel. */
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    /** Führt [action] aus, solange nichts anderes läuft, und meldet ihr Ergebnis oder bei Fehlern [failure]. */
    private fun launchTask(@StringRes failure: Int, action: suspend () -> DataMessage?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = try {
                action()
            } catch (_: IOException) {
                DataMessage.Text(failure)
            } catch (_: SQLException) {
                DataMessage.Text(failure)
            } finally {
                _busy.value = false
            }
            result?.let { _message.value = it }
        }
    }

    /**
     * Schreibt alle Touren als CSV und fordert das Teilen an; ohne Touren gibt es nur einen Hinweis.
     * [defaultVehicleName] gilt für Touren eines Fahrzeugs mit leerem Namen.
     */
    fun exportCsv(defaultVehicleName: String) = launchTask(R.string.export_failed) {
        val tours = repository.allTours()
        if (tours.isEmpty()) {
            DataMessage.Text(R.string.export_nothing)
        } else {
            val vehicleNames = vehicles.allVehicles().associate { it.id to it.name }
            val allStations = stations.allStations()
            _share.value = ShareRequest.Csv(files.writeCsvExport(tours, allStations, vehicleNames, defaultVehicleName, vocabulary()))
            null
        }
    }

    /**
     * Schreibt alle Stationen als eigene CSV und fordert das Teilen an; ohne Stationen gibt es nur
     * einen Hinweis. [defaultVehicleName] gilt für Stationen eines Fahrzeugs mit leerem Namen.
     */
    fun exportStationsCsv(defaultVehicleName: String) = launchTask(R.string.export_stations_failed) {
        val allStations = stations.allStations()
        if (allStations.isEmpty()) {
            DataMessage.Text(R.string.export_stations_nothing)
        } else {
            val vehicleNames = vehicles.allVehicles().associate { it.id to it.name }
            val tourNames = repository.allTours().associate { it.id to it.destination }
            _share.value = ShareRequest.StationsCsv(files.writeStationsCsvExport(allStations, tourNames, vehicleNames, defaultVehicleName, vocabulary()))
            null
        }
    }

    /** Speichert eine Sicherung in die vom Nutzer gewählte Datei [target]; als ZIP, sofern [includeFiles]. */
    fun saveBackup(target: String, includeFiles: Boolean) = launchTask(R.string.backup_failed) {
        val backup = currentBackup()
        val json = withContext(background) { encodeBackup(backup) }
        if (includeFiles) {
            files.writeBackupZip(target, zipWriter(json, backup))
        } else {
            files.writeBackup(target, json)
        }
        onBackupSaved(clock())
        DataMessage.Text(R.string.backup_saved)
    }

    /** Schreibt eine Sicherung in den Export-Cache und fordert das Teilen an; als ZIP, sofern [includeFiles]. */
    fun shareBackup(includeFiles: Boolean) = launchTask(R.string.backup_failed) {
        val backup = currentBackup()
        val json = withContext(background) { encodeBackup(backup) }
        _share.value = if (includeFiles) {
            ShareRequest.BackupZip(files.writeBackupZipExport(zipWriter(json, backup)))
        } else {
            ShareRequest.Backup(files.writeBackupExport(json))
        }
        onBackupSaved(clock())
        null
    }

    /**
     * Schreibt eine neue, zeitgestempelte Sicherung in den gewählten Sicherungsordner [folderUri]; als
     * ZIP, sofern [includeFilesOverride] das vorgibt, sonst nach der Standardregel - solange die
     * Gesamtgröße unter [app.restvolt.camperlog.domain.AUTO_BACKUP_FILES_SIZE_LIMIT_BYTES] bleibt, sonst
     * als reines JSON. Ist der Ordner nicht mehr zugreifbar, meldet [DataMessage.Text] mit
     * [R.string.data_backup_folder_failed], ohne [onBackupSaved] aufzurufen.
     */
    fun backUpToFolder(folderUri: String, includeFilesOverride: Boolean? = null) = launchTask(R.string.backup_failed) {
        val folderName = folderWriter.folderDisplayName(folderUri)
        if (folderName == null || !folderWriter.isAccessible(folderUri)) {
            DataMessage.Text(R.string.data_backup_folder_failed)
        } else {
            val backup = currentBackup()
            val json = withContext(background) { encodeBackup(backup) }
            val includeFiles = includeFilesOverride ?: shouldIncludeFilesInAutoBackup(backupZipSizeEstimate(json, backup.attachments))
            val written = if (includeFiles) {
                folderWriter.writeTimestampedBackupZip(folderUri, zipWriter(json, backup))
            } else {
                folderWriter.writeTimestampedBackup(folderUri, json)
            }
            if (written != null) {
                onBackupSaved(clock())
                DataMessage.BackedUpToFolder(folderName)
            } else {
                DataMessage.Text(R.string.data_backup_folder_failed)
            }
        }
    }

    /** Quittiert [share]; ohne passende App ([started] = false) folgt ein Hinweis. */
    fun shareHandled(started: Boolean) {
        _share.value = null
        if (!started) _message.value = DataMessage.Text(R.string.no_share_app)
    }

    /** Quittiert die Anzeige von [message]. */
    fun messageShown() {
        _message.value = null
    }

    /** Vollständige Sicherung des aktuellen Stands als JSON (ohne Dateien). */
    suspend fun backupJson(): String = withContext(background) { encodeBackup(currentBackup()) }

    /**
     * Geschätzte Gesamtgröße einer vollständigen ZIP-Sicherung (JSON plus alle Anhangsdateien), zur
     * Anzeige vor dem Export; `null`, wenn sie sich wegen eines Lese- oder Zugriffsfehlers nicht
     * bestimmen lässt - das ist nur eine Anzeigehilfe, kein Grund, den Screen scheitern zu lassen.
     */
    suspend fun backupSizeEstimate(): Long? = withContext(background) {
        try {
            val backup = currentBackup()
            backupZipSizeEstimate(encodeBackup(backup), backup.attachments)
        } catch (_: SQLException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun currentBackup(): Backup =
        buildBackup(repository, exchangeRates, vehicles, logs, stations, documents, diaryEntries, checklistTemplates, checklists, tracks, attachments, clock())

    /** Schreibt [json] und die Dateien der [backup]-Anhänge direkt in den gelieferten Ausgabestrom. */
    private fun zipWriter(json: String, backup: Backup): (OutputStream) -> Unit = { output ->
        writeBackupZip(output, json, backup.attachments, includeFiles = true) { fileName ->
            attachmentFileStore.file(fileName).takeIf { it.exists() }?.inputStream()
        }
    }

    /**
     * Liest und prüft die Sicherung [source] (JSON oder ZIP, anhand der ersten Bytes unterschieden);
     * bei Erfolg steht sie danach in [pendingImport] bereit, sonst meldet [message] den Fehler samt
     * Tournummer. Unlesbare Dateien ergeben einen eigenen Hinweis.
     */
    fun loadBackup(source: String) = launchTask(R.string.import_unreadable) {
        when (val loaded = withContext(background) { loadBackupSource(source) }) {
            is LoadedBackup.Failure -> {
                _pendingImport.value = null
                DataMessage.LoadFailed(loaded.failure)
            }
            is LoadedBackup.Loaded -> {
                _pendingImport.value = PendingImport(
                    backup = loaded.backup,
                    existingTours = repository.allTours().size,
                    stagingDir = loaded.stagingDir,
                    stagedFiles = loaded.stagedFiles,
                )
                null
            }
        }
    }

    private sealed interface LoadedBackup {
        data class Loaded(val backup: Backup, val stagingDir: File?, val stagedFiles: Map<String, File>) : LoadedBackup
        data class Failure(val failure: BackupReadResult.Failure) : LoadedBackup
    }

    /** `PK` als erste zwei Bytes kennzeichnet eine ZIP-Datei; alles andere wird als reines JSON gelesen. */
    private fun loadBackupSource(source: String): LoadedBackup {
        val isZip = (files.open(source) ?: throw IOException("Datei nicht lesbar")).use { stream ->
            val header = ByteArray(2)
            val read = stream.read(header)
            read == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
        return if (isZip) {
            val stagingDir = files.newImportStagingDir()
            when (val result = (files.open(source) ?: throw IOException("Datei nicht lesbar")).use { readBackupZip(it, stagingDir) }) {
                is BackupZipReadResult.Success -> LoadedBackup.Loaded(result.backup, stagingDir, result.stagedFiles)
                is BackupZipReadResult.Failure -> {
                    stagingDir.deleteRecursively()
                    LoadedBackup.Failure(result.failure)
                }
            }
        } else {
            when (val result = (files.open(source) ?: throw IOException("Datei nicht lesbar")).use(::readBackup)) {
                is BackupReadResult.Success -> LoadedBackup.Loaded(result.backup, null, emptyMap())
                is BackupReadResult.Failure -> LoadedBackup.Failure(result)
            }
        }
    }

    /**
     * Spielt die vorgemerkte Sicherung im Modus [mode] ein. Läuft im [viewModelScope], damit ein
     * Konfigurationswechsel den Import weder abbricht noch doppelt startet. Bei Erfolg werden die
     * Dateien einer ZIP-Sicherung (siehe [PendingImport.stagedFiles]) an ihren endgültigen Platz
     * verschoben, die Sicherung verworfen und [message] gesetzt; bei einem Fehler bleibt sie mit
     * [PendingImport.failed] stehen, ihre Zwischenablage bleibt für einen erneuten Versuch liegen.
     */
    fun startImport(mode: ImportMode) {
        val pending = _pendingImport.value ?: return
        if (pending.running) return
        _pendingImport.value = pending.copy(running = true, failed = false)
        viewModelScope.launch {
            try {
                val result = importer.import(pending.backup, mode)
                if (pending.stagedFiles.isNotEmpty()) {
                    withContext(background) { commitStagedAttachmentFiles(attachmentFileStore, pending.stagedFiles) }
                }
                pending.stagingDir?.deleteRecursively()
                _pendingImport.value = null
                _message.value = DataMessage.Imported(result)
            } catch (_: SQLException) {
                _pendingImport.value = pending.copy(running = false, failed = true)
            } catch (_: IOException) {
                _pendingImport.value = pending.copy(running = false, failed = true)
            }
        }
    }

    /** Verwirft die vorgemerkte Sicherung, ohne etwas zu ändern; während eines laufenden Imports wirkungslos. */
    fun cancelImport() {
        if (_pendingImport.value?.running != true) {
            _pendingImport.value?.stagingDir?.deleteRecursively()
            _pendingImport.value = null
        }
    }
}
