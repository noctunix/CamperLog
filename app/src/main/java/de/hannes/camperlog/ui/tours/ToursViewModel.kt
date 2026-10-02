package de.hannes.camperlog.ui.tours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun delete(tour: Tour) {
        viewModelScope.launch { repository.delete(tour.id) }
    }

    /** Alle Touren in chronologischer Reihenfolge für den CSV-Export. */
    suspend fun toursForExport(): List<Tour> = repository.allTours()
}
