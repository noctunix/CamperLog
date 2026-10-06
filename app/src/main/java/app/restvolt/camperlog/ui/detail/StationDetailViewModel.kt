package app.restvolt.camperlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Zustand der Stationsdetailansicht (6.6). [Loaded.tour] ist nur gesetzt, wenn die Station zu einer Tour gehört. */
sealed interface StationDetailUiState {
    data object Loading : StationDetailUiState
    data object NotFound : StationDetailUiState
    data class Loaded(val station: Station, val tour: Tour? = null) : StationDetailUiState
}

/** Beobachtet eine einzelne Station mit ihrer Tour, damit Änderungen aus dem Formular sofort sichtbar sind. */
class StationDetailViewModel(stations: StationRepository, tours: TourRepository, stationId: Long) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StationDetailUiState> = stations.observeStation(stationId).flatMapLatest { station ->
        when {
            station == null -> flowOf(StationDetailUiState.NotFound)
            station.tourId != null -> tours.observeTour(station.tourId).map { tour -> StationDetailUiState.Loaded(station, tour) }
            else -> flowOf(StationDetailUiState.Loaded(station))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationDetailUiState.Loading)
}
