package app.restvolt.camperlog.ui.logbook

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.quickSupplyService
import app.restvolt.camperlog.domain.quickSupplyStation
import app.restvolt.camperlog.domain.stationAfterRemovingQuickSupply
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Bordbuch-Kachel: Art und ihr jüngstes Datum, sofern schon erfasst. */
data class LogTileState(val type: LogType, val lastDate: LocalDate?)

/** Zustand des Bordbuch-Reiters: Fahrzeugwechsler und eine Kachel je [LogType] in fester Reihenfolge. */
data class LogbookUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
    val tiles: List<LogTileState> = LogType.entries.map { LogTileState(it, null) },
)

/** Rückmeldungen, die der Bordbuch-Reiter als Snackbar anzeigt. */
sealed interface LogbookMessage {
    data class Recorded(val entry: LogEntry, val wasToday: Boolean) : LogbookMessage
    data class Failed(@StringRes val text: Int) : LogbookMessage
}

/**
 * Hält die Bordbuch-Kacheln des aktuellen Fahrzeugs aktuell und legt neue Einträge an. Kassette,
 * Grauwasser und Gasflasche legen dafür intern eine minimale Ver-/Entsorgungs-Station an bzw.
 * erweitern eine bestehende desselben Tages (siehe [quickSupplyStation]); Dieselheizung und
 * Gasheizung bleiben eigenständige, unverknüpfte Einträge. [today] liefert das heutige Datum und
 * ist für Tests ersetzbar.
 */
class LogbookViewModel(
    private val logs: LogRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LogbookUiState> =
        combine(vehicles.observeVehicles(), vehicles.observeCurrentVehicle()) { list, current -> list to current }
            .flatMapLatest { (list, current) ->
                logs.observeLatest(current.id).map { latest ->
                    LogbookUiState(list, current.id, LogType.entries.map { LogTileState(it, latest[it]) })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUiState())

    fun onSelectVehicle(id: Long) {
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }

    private val _message = MutableStateFlow<LogbookMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar des Reiters; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<LogbookMessage?> = _message.asStateFlow()

    /** Legt für [type] einen Eintrag mit dem heutigen Datum an. */
    fun recordToday(type: LogType) = record(type, today())

    /** Legt für [type] einen Eintrag mit [date] an, z. B. aus dem Datumswähler. */
    fun recordDate(type: LogType, date: LocalDate) = record(type, date)

    private fun record(type: LogType, date: LocalDate) {
        val vehicleId = uiState.value.currentVehicleId
        viewModelScope.launch {
            _message.value = try {
                val service = quickSupplyService(type)
                val entry = if (service != null) recordSupply(vehicleId, service, type, date) else logs.add(vehicleId, type, date)
                LogbookMessage.Recorded(entry, wasToday = date == today())
            } catch (_: SQLException) {
                LogbookMessage.Failed(R.string.logbook_record_failed)
            }
        }
    }

    /** Trägt [service] an [date] in die Ver-/Entsorgungs-Station des Tages ein und liefert den dabei entstandenen verknüpften Eintrag. */
    private suspend fun recordSupply(vehicleId: Long, service: StationService, type: LogType, date: LocalDate): LogEntry {
        val existing = stations.findSupplyStation(vehicleId, date)
        val tourId = stations.defaultTourId(vehicleId, date)
        val stationId = stations.save(quickSupplyStation(existing, vehicleId, service, date, tourId))
        return checkNotNull(logs.linkedEntry(stationId, type)) { "Speichern der Station muss den verknüpften Eintrag anlegen" }
    }

    /** Macht einen über [recordToday]/[recordDate] angelegten Eintrag wieder rückgängig. */
    fun undoRecord(entry: LogEntry) {
        viewModelScope.launch {
            try {
                val stationId = entry.stationId
                if (stationId == null) {
                    logs.delete(entry.id)
                } else {
                    undoSupply(stationId, entry.type)
                }
            } catch (_: SQLException) {
                _message.value = LogbookMessage.Failed(R.string.logbook_record_undo_failed)
            }
        }
    }

    /**
     * Entfernt den zu [type] gehörenden Dienst von der Station [stationId]. Erst speichern lässt den
     * Bordbuch-Abgleich beim Speichern den verknüpften Eintrag selbst löschen (ein reines Löschen der
     * Station würde ihn über den Fremdschlüssel nur entknüpfen statt ihn zu entfernen); bleibt die
     * Station danach ganz ohne Inhalt, wird die leere Hülle anschließend gelöscht.
     */
    private suspend fun undoSupply(stationId: Long, type: LogType) {
        val station = stations.observeStation(stationId).first() ?: return
        val service = checkNotNull(quickSupplyService(type)) { "Ein stationsverknüpfter Eintrag muss einen synchronisierten Typ haben" }
        val updated = stationAfterRemovingQuickSupply(station, service)
        stations.save(updated ?: station.copy(services = station.services - service))
        if (updated == null) stations.delete(stationId)
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: LogbookMessage) {
        _message.compareAndSet(shown, null)
    }
}
