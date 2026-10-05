package app.restvolt.camperlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Zustand der Detailansicht. [Loaded.vehicle] ist nur gesetzt, wenn es mehr als ein Fahrzeug gibt. */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val tour: Tour, val vehicle: Vehicle? = null) : DetailUiState
}

/** Beobachtet eine einzelne Tour, damit Änderungen aus dem Formular sofort sichtbar sind. */
class TourDetailViewModel(repository: TourRepository, vehicles: VehicleRepository, tourId: Long) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = combine(repository.observeTour(tourId), vehicles.observeVehicles()) { tour, list ->
        when {
            tour == null -> DetailUiState.NotFound
            list.size > 1 -> DetailUiState.Loaded(tour, list.firstOrNull { it.id == tour.vehicleId })
            else -> DetailUiState.Loaded(tour)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)
}
