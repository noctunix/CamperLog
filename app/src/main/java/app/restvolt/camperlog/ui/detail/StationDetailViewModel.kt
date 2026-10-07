package app.restvolt.camperlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Zustand der Stationsdetailansicht. [Loaded.tour] ist nur gesetzt, wenn die Station zu einer Tour gehört. */
sealed interface StationDetailUiState {
    data object Loading : StationDetailUiState
    data object NotFound : StationDetailUiState
    data class Loaded(val station: Station, val tour: Tour? = null) : StationDetailUiState
}

/** Rückmeldung zum Bearbeiten der Station; die Detailseite zeigt sie als Snackbar. */
data class StationDetailMessage(val loggedServices: Set<StationService>)

/**
 * Beobachtet eine einzelne Station mit ihrer Tour, damit Änderungen aus dem Formular sofort sichtbar
 * sind. [initialLoggedServices] seedet die Speicher-Snackbar, wenn diese Seite direkt nach dem ersten
 * Anlegen der Station erreicht wird; `null` sonst, insbesondere beim normalen Öffnen einer Station.
 */
class StationDetailViewModel(
    stations: StationRepository,
    tours: TourRepository,
    stationId: Long,
    initialLoggedServices: Set<StationService>? = null,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StationDetailUiState> = stations.observeStation(stationId).flatMapLatest { station ->
        when {
            station == null -> flowOf(StationDetailUiState.NotFound)
            station.tourId != null -> tours.observeTour(station.tourId).map { tour -> StationDetailUiState.Loaded(station, tour) }
            else -> flowOf(StationDetailUiState.Loaded(station))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationDetailUiState.Loading)

    private val _message = MutableStateFlow(initialLoggedServices?.let { StationDetailMessage(it) })

    /** Einmalige Rückmeldung für die Snackbar der Detailseite; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<StationDetailMessage?> = _message.asStateFlow()

    /** Meldet, dass die Station gerade bearbeitet und gespeichert wurde, für die Snackbar der Detailseite. */
    fun onStationSaved(loggedServices: Set<StationService>) {
        _message.value = StationDetailMessage(loggedServices)
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: StationDetailMessage) {
        _message.compareAndSet(shown, null)
    }
}
