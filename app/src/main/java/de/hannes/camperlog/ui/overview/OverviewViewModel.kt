package de.hannes.camperlog.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hannes.camperlog.domain.TourRepository
import de.hannes.camperlog.domain.TourTotals
import de.hannes.camperlog.domain.YearTotals
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Gesamt- und Jahreskennzahlen; [years] ist absteigend sortiert. */
data class OverviewUiState(
    val isLoading: Boolean = true,
    val total: TourTotals? = null,
    val years: List<YearTotals> = emptyList(),
)

/** Stellt die per SQL aggregierten Kennzahlen bereit. */
class OverviewViewModel(repository: TourRepository) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> =
        combine(repository.observeTotals(), repository.observeYearTotals()) { total, years ->
            OverviewUiState(isLoading = false, total = total, years = years)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())
}
