package app.restvolt.camperlog.ui.detail

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zustand der Detailansicht. [Loaded.vehicle] ist nur gesetzt, wenn es mehr als ein Fahrzeug gibt.
 * [Loaded.stations] ist die Zeitleiste, aufsteigend nach `(date, time NULLS LAST, createdAt)`.
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val tour: Tour, val vehicle: Vehicle? = null, val stations: List<Station> = emptyList()) : DetailUiState
}

/** Rückmeldung zum Löschen einer Station aus der Zeitleiste; die Detailseite zeigt sie als Snackbar. */
sealed interface StationMessage {
    data class Deleted(val station: Station) : StationMessage
    data class Failed(@StringRes val text: Int) : StationMessage
}

/** Beobachtet eine Tour mit ihrer Stationen-Zeitleiste, damit Änderungen aus Formular und Löschen sofort sichtbar sind. */
class TourDetailViewModel(
    private val repository: TourRepository,
    vehicles: VehicleRepository,
    private val stations: StationRepository,
    tourId: Long,
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeTour(tourId),
        vehicles.observeVehicles(),
        stations.observeForTour(tourId),
    ) { tour, vehicleList, stationList ->
        when {
            tour == null -> DetailUiState.NotFound
            vehicleList.size > 1 -> DetailUiState.Loaded(tour, vehicleList.firstOrNull { it.id == tour.vehicleId }, stationList)
            else -> DetailUiState.Loaded(tour, stations = stationList)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _message = MutableStateFlow<StationMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar der Detailseite; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<StationMessage?> = _message.asStateFlow()

    /** Löscht [station] und bietet über [StationMessage.Deleted] das Rückgängigmachen an (8.3). */
    fun deleteStation(station: Station) {
        viewModelScope.launch {
            _message.value = try {
                stations.delete(station.id)
                StationMessage.Deleted(station)
            } catch (_: SQLException) {
                StationMessage.Failed(R.string.station_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteStation] entfernte Station unverändert wieder her. */
    fun undoDeleteStation(station: Station) {
        viewModelScope.launch {
            try {
                stations.restore(station)
            } catch (_: SQLException) {
                _message.value = StationMessage.Failed(R.string.station_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: StationMessage) {
        _message.compareAndSet(shown, null)
    }
}
