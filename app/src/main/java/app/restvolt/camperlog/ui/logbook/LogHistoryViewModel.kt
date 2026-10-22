package app.restvolt.camperlog.ui.logbook

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.quickSupplyService
import app.restvolt.camperlog.domain.removeLinkedLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand des Verlaufs einer Bordbuch-Art: ihre Einträge, neueste zuerst, und die Stationen desselben Fahrzeugs. */
data class LogHistoryUiState(val entries: List<LogEntry> = emptyList(), val stations: List<Station> = emptyList())

/** Rückmeldungen, die der Verlauf als Snackbar anzeigt. */
sealed interface LogHistoryMessage {
    /** [entry] wurde entfernt; bei einem stationsverknüpften Eintrag [station] davor, für ein exaktes [LogHistoryViewModel.undoDelete]. */
    data class Deleted(val entry: LogEntry, val station: Station?) : LogHistoryMessage
    data class Failed(@StringRes val text: Int) : LogHistoryMessage
}

/** Hält die Einträge eines Fahrzeugs und einer Art aktuell; löscht Einträge mit Rückgängig-Option. */
class LogHistoryViewModel(
    private val logs: LogRepository,
    private val stations: StationRepository,
    private val vehicleId: Long,
    val type: LogType,
) : ViewModel() {

    val uiState: StateFlow<LogHistoryUiState> =
        combine(logs.observeEntries(vehicleId, type), stations.observeForVehicle(vehicleId)) { entries, stationList ->
            LogHistoryUiState(entries, stationList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogHistoryUiState())

    private val _message = MutableStateFlow<LogHistoryMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar des Verlaufs; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<LogHistoryMessage?> = _message.asStateFlow()

    /** Löscht [entry] und bietet über [LogHistoryMessage.Deleted] das Rückgängigmachen an. */
    fun delete(entry: LogEntry) {
        viewModelScope.launch {
            _message.value = try {
                LogHistoryMessage.Deleted(entry, deleteLinkedStationService(entry))
            } catch (_: SQLException) {
                LogHistoryMessage.Failed(R.string.logbook_delete_failed)
            }
        }
    }

    /**
     * Löscht [entry]; ist er mit einer Station verknüpft, wird statt eines direkten Löschens ihr
     * Dienst entfernt (siehe [LogbookViewModel.undoSupply]), was den Eintrag per Bordbuch-Abgleich
     * selbst entfernt und die Station bei anderen verknüpften Diensten unangetastet lässt.
     *
     * @return die Station vor dem Entfernen des Dienstes, für ein exaktes Rückgängig, oder `null`
     * bei einem unverknüpften Eintrag
     */
    private suspend fun deleteLinkedStationService(entry: LogEntry): Station? {
        val stationId = entry.stationId ?: run { logs.delete(entry.id); return null }
        val station = stations.observeStation(stationId).first() ?: run { logs.delete(entry.id); return null }
        removeLinkedLogEntry(stations, logs, station, entry)
        return station
    }

    /** Stellt einen über [delete] entfernten Eintrag samt seiner Station wieder her. */
    fun undoDelete(deleted: LogHistoryMessage.Deleted) {
        viewModelScope.launch {
            try {
                val station = deleted.station
                if (station == null) logs.restore(deleted.entry.copy(stationId = null)) else restoreLinkedStationService(station, deleted.entry)
            } catch (_: SQLException) {
                _message.value = LogHistoryMessage.Failed(R.string.logbook_restore_failed)
            }
        }
    }

    /**
     * Stellt eine durch [deleteLinkedStationService] geänderte Station und den davon entfernten
     * Eintrag [entry] wieder her. Wurde die Station dabei komplett gelöscht, legt [StationRepository.restore]
     * sie unverändert mit ihrer bisherigen id neu an; blieb sie nur um den Dienst verkürzt bestehen,
     * wird [entry] zunächst unverknüpft wiederhergestellt und die Station anschließend erneut
     * gespeichert – der Bordbuch-Abgleich verknüpft ihn dabei wieder, ohne andere verknüpfte Dienste
     * derselben Station zu berühren.
     */
    private suspend fun restoreLinkedStationService(station: Station, entry: LogEntry) {
        if (stations.observeStation(station.id).first() == null) {
            stations.restore(station)
            logs.restore(entry)
        } else if (quickSupplyService(entry.type) == null) {
            // Typ-synchronisierter Eintrag (Tanken): die Station blieb unverändert bestehen
            logs.restore(entry)
        } else {
            logs.restore(entry.copy(stationId = null))
            stations.save(station)
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: LogHistoryMessage) {
        _message.compareAndSet(shown, null)
    }
}
