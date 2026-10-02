package de.hannes.camperlog.ui.tours

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Tourenliste. [tours] ist bereits nach Suche und Jahr gefiltert. */
data class ToursUiState(
    val isLoading: Boolean = true,
    val hasAnyTour: Boolean = false,
    val tours: List<Tour> = emptyList(),
    val years: List<Int> = emptyList(),
    val query: String = "",
    val selectedYear: Int? = null,
)

/** Rückmeldungen, die die Tourenliste als Snackbar anzeigt. */
sealed interface ToursMessage {
    data class Deleted(val tour: Tour) : ToursMessage
    data object Saved : ToursMessage
    data class Failed(@StringRes val text: Int) : ToursMessage
}

/** Liefert die gefilterte Tourenliste und löscht Touren. */
class ToursViewModel(private val repository: TourRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedYear = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ToursUiState> =
        combine(repository.observeTours(), query, selectedYear) { tours, query, year ->
            val years = tours.map(Tour::year).distinct().sortedDescending()
            val activeYear = year?.takeIf { it in years }
            ToursUiState(
                isLoading = false,
                hasAnyTour = tours.isNotEmpty(),
                tours = tours.filter { tour ->
                    tour.destination.contains(query.trim(), ignoreCase = true) &&
                        (activeYear == null || tour.year == activeYear)
                },
                years = years,
                query = query,
                selectedYear = activeYear,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToursUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Setzt den Jahresfilter; `null` zeigt alle Jahre. */
    fun onYearSelected(year: Int?) {
        selectedYear.value = year
    }

    private val _message = MutableStateFlow<ToursMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar der Liste; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<ToursMessage?> = _message.asStateFlow()

    /** Löscht [tour] und bietet über [ToursMessage.Deleted] das Rückgängigmachen an. */
    fun delete(tour: Tour) {
        viewModelScope.launch {
            _message.value = try {
                repository.delete(tour.id)
                ToursMessage.Deleted(tour)
            } catch (_: SQLException) {
                ToursMessage.Failed(R.string.tours_delete_failed)
            }
        }
    }

    /** Stellt eine über [delete] entfernte Tour unverändert wieder her. */
    fun undoDelete(tour: Tour) {
        viewModelScope.launch {
            try {
                repository.restore(tour)
            } catch (_: SQLException) {
                _message.value = ToursMessage.Failed(R.string.tours_restore_failed)
            }
        }
    }

    /** Nach dem Anlegen einer Tour: Filter zurücksetzen, damit die neue Tour sichtbar ist, und bestätigen. */
    fun onTourCreated() {
        query.value = ""
        selectedYear.value = null
        _message.value = ToursMessage.Saved
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: ToursMessage) {
        _message.compareAndSet(shown, null)
    }

    /** Alle Touren in chronologischer Reihenfolge für den CSV-Export. */
    suspend fun toursForExport(): List<Tour> = repository.allTours()
}
