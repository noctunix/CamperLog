package app.restvolt.camperlog.ui.data

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.BackupVehicle
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.backup.readBackup
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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

/** Meldung für die Snackbar des Daten-Screens. */
sealed interface DataMessage {
    /** Fester Text ohne Platzhalter. */
    data class Text(@StringRes val text: Int) : DataMessage

    /** Import abgeschlossen. */
    data class Imported(val result: ImportResult) : DataMessage

    /** Gewählte Datei ist keine gültige Sicherung. */
    data class LoadFailed(val failure: BackupReadResult.Failure) : DataMessage
}

/** Datei, die der Screen mit einer anderen App teilen soll; [uri] wie von [DataFiles] geliefert. */
sealed interface ShareRequest {
    val uri: String

    data class Csv(override val uri: String) : ShareRequest

    data class StationsCsv(override val uri: String) : ShareRequest

    data class Backup(override val uri: String) : ShareRequest
}

/**
 * Datenverwaltung: Export und Import der gespeicherten Touren, Fahrzeuge, Reparaturen, des
 * Bordbuchs, der Kurse und der Hauptwährung. Alle Vorgänge laufen im [viewModelScope] und
 * überstehen so Konfigurationswechsel; Ergebnisse erscheinen in [message] bzw. [share], bis der
 * Screen sie quittiert.
 */
class DataViewModel(
    private val repository: TourRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val vehicles: VehicleRepository,
    private val logs: LogRepository,
    private val stations: StationRepository,
    private val importer: BackupImporter,
    private val files: DataFiles,
    private val clock: () -> Instant = Instant::now,
    private val background: CoroutineDispatcher = Dispatchers.IO,
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
            _share.value = ShareRequest.Csv(files.writeCsvExport(tours, allStations, vehicleNames, defaultVehicleName))
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
            _share.value = ShareRequest.StationsCsv(files.writeStationsCsvExport(allStations, tourNames, vehicleNames, defaultVehicleName))
            null
        }
    }

    /** Speichert eine Sicherung in die vom Nutzer gewählte Datei [target]. */
    fun saveBackup(target: String) = launchTask(R.string.backup_failed) {
        files.writeBackup(target, backupJson())
        DataMessage.Text(R.string.backup_saved)
    }

    /** Schreibt eine Sicherung in den Export-Cache und fordert das Teilen an. */
    fun shareBackup() = launchTask(R.string.backup_failed) {
        _share.value = ShareRequest.Backup(files.writeBackupExport(backupJson()))
        null
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

    /** Vollständige Sicherung des aktuellen Stands als JSON. */
    suspend fun backupJson(): String {
        val tours = repository.allTours()
        val allVehicles = vehicles.allVehicles()
        val allStations = stations.allStations()
        val repairsByVehicle = vehicles.allRepairs().groupBy { it.vehicleId }
        val logEntriesByVehicle = logs.allEntries().groupBy { it.vehicleId }
        val vehicleUuidById = allVehicles.associate { it.id to it.uuid }
        val tourUuidById = tours.associate { it.id to it.uuid }
        val stationUuidById = allStations.associate { it.id to it.uuid }
        val backup = Backup(
            exportedAt = clock(),
            mainCurrency = exchangeRates.observeMainCurrency().first(),
            rates = exchangeRates.observeRates().first(),
            tours = tours,
            tourVehicleUuid = tours.mapNotNull { tour -> vehicleUuidById[tour.vehicleId]?.let { tour.uuid to it } }.toMap(),
            vehicles = allVehicles.map { vehicle ->
                BackupVehicle(
                    vehicle = vehicle,
                    repairs = repairsByVehicle[vehicle.id].orEmpty(),
                    logEntries = logEntriesByVehicle[vehicle.id].orEmpty(),
                )
            },
            currentVehicleUuid = vehicles.observeCurrentVehicle().first().uuid,
            stations = allStations,
            stationVehicleUuid = allStations.mapNotNull { station -> vehicleUuidById[station.vehicleId]?.let { station.uuid to it } }.toMap(),
            stationTourUuid = allStations.mapNotNull { station ->
                station.tourId?.let { tourUuidById[it] }?.let { station.uuid to it }
            }.toMap(),
            logEntryStationUuid = logEntriesByVehicle.values.flatten().mapNotNull { entry ->
                entry.stationId?.let { stationUuidById[it] }?.let { entry.uuid to it }
            }.toMap(),
        )
        return withContext(background) { encodeBackup(backup) }
    }

    /**
     * Liest und prüft die Sicherung [source]; bei Erfolg steht sie danach in [pendingImport] bereit,
     * sonst meldet [message] den Fehler samt Tournummer. Unlesbare Dateien ergeben einen eigenen Hinweis.
     */
    fun loadBackup(source: String) = launchTask(R.string.import_unreadable) {
        val result = withContext(background) {
            (files.open(source) ?: throw IOException("Datei nicht lesbar")).use(::readBackup)
        }
        when (result) {
            is BackupReadResult.Failure -> {
                _pendingImport.value = null
                DataMessage.LoadFailed(result)
            }
            is BackupReadResult.Success -> {
                _pendingImport.value = PendingImport(result.backup, repository.allTours().size)
                null
            }
        }
    }

    /**
     * Spielt die vorgemerkte Sicherung im Modus [mode] ein. Läuft im [viewModelScope], damit ein
     * Konfigurationswechsel den Import weder abbricht noch doppelt startet. Bei Erfolg wird die
     * Sicherung verworfen und [message] gesetzt, bei einem Fehler bleibt sie mit [PendingImport.failed] stehen.
     */
    fun startImport(mode: ImportMode) {
        val pending = _pendingImport.value ?: return
        if (pending.running) return
        _pendingImport.value = pending.copy(running = true, failed = false)
        viewModelScope.launch {
            try {
                val result = importer.import(pending.backup, mode)
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
        if (_pendingImport.value?.running != true) _pendingImport.value = null
    }
}
