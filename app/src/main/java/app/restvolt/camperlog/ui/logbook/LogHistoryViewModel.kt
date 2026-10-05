package app.restvolt.camperlog.ui.logbook

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand des Verlaufs einer Bordbuch-Art: ihre Einträge, neueste zuerst. */
data class LogHistoryUiState(val entries: List<LogEntry> = emptyList())

/** Rückmeldungen, die der Verlauf als Snackbar anzeigt. */
sealed interface LogHistoryMessage {
    data class Deleted(val entry: LogEntry) : LogHistoryMessage
    data class Failed(@StringRes val text: Int) : LogHistoryMessage
}

/** Hält die Einträge eines Fahrzeugs und einer Art aktuell; löscht Einträge mit Rückgängig-Option. */
class LogHistoryViewModel(
    private val logs: LogRepository,
    private val vehicleId: Long,
    val type: LogType,
) : ViewModel() {

    val uiState: StateFlow<LogHistoryUiState> =
        logs.observeEntries(vehicleId, type).map { LogHistoryUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogHistoryUiState())

    private val _message = MutableStateFlow<LogHistoryMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar des Verlaufs; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<LogHistoryMessage?> = _message.asStateFlow()

    /** Löscht [entry] und bietet über [LogHistoryMessage.Deleted] das Rückgängigmachen an. */
    fun delete(entry: LogEntry) {
        viewModelScope.launch {
            _message.value = try {
                logs.delete(entry.id)
                LogHistoryMessage.Deleted(entry)
            } catch (_: SQLException) {
                LogHistoryMessage.Failed(R.string.logbook_delete_failed)
            }
        }
    }

    /** Stellt einen über [delete] entfernten Eintrag unverändert wieder her. */
    fun undoDelete(entry: LogEntry) {
        viewModelScope.launch {
            try {
                logs.restore(entry)
            } catch (_: SQLException) {
                _message.value = LogHistoryMessage.Failed(R.string.logbook_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: LogHistoryMessage) {
        _message.compareAndSet(shown, null)
    }
}
