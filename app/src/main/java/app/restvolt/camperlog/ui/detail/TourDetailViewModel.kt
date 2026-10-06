package app.restvolt.camperlog.ui.detail

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Conversion
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.convert
import app.restvolt.camperlog.domain.costsByCategory
import app.restvolt.camperlog.domain.stationCostTotals
import app.restvolt.camperlog.domain.totalCosts
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
 * [Loaded.stopCosts] ist die Summe der Stationskosten je Währung, [Loaded.totalCosts] die
 * Gesamtsumme (manuelle Tourkosten plus Stationskosten) je Währung, [Loaded.categoryCosts] deren
 * Aufschlüsselung nach Kategorie. [Loaded.conversion] ist `null`, wenn alle Kosten bereits in der
 * Hauptwährung vorliegen und eine Umrechnung nichts Neues zeigen würde.
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(
        val tour: Tour,
        val vehicle: Vehicle? = null,
        val stations: List<Station> = emptyList(),
        val stopCosts: List<Money> = emptyList(),
        val totalCosts: List<Money> = emptyList(),
        val categoryCosts: Map<CostCategory, List<Money>> = emptyMap(),
        val conversion: Conversion? = null,
    ) : DetailUiState
}

/** Rückmeldung zu einer Station aus der Zeitleiste; die Detailseite zeigt sie als Snackbar. */
sealed interface StationMessage {
    /** [linkedEntryIds] sind die Bordbuch-Einträge, die vor dem Löschen mit der Station verknüpft waren. */
    data class Deleted(val station: Station, val linkedEntryIds: List<Long> = emptyList()) : StationMessage
    data class Saved(val loggedServices: Set<StationService>) : StationMessage
    data class Failed(@StringRes val text: Int) : StationMessage
}

/** Beobachtet eine Tour mit ihrer Stationen-Zeitleiste, damit Änderungen aus Formular und Löschen sofort sichtbar sind. */
class TourDetailViewModel(
    private val repository: TourRepository,
    vehicles: VehicleRepository,
    private val stations: StationRepository,
    exchangeRates: ExchangeRateRepository,
    tourId: Long,
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeTour(tourId),
        vehicles.observeVehicles(),
        stations.observeForTour(tourId),
        combine(exchangeRates.observeMainCurrency(), exchangeRates.observeRates()) { main, rates -> main to rates },
    ) { tour, vehicleList, stationList, (main, rates) ->
        if (tour == null) {
            DetailUiState.NotFound
        } else {
            val totalCosts = tour.totalCosts(stationList)
            DetailUiState.Loaded(
                tour = tour,
                vehicle = vehicleList.firstOrNull { it.id == tour.vehicleId }.takeIf { vehicleList.size > 1 },
                stations = stationList,
                stopCosts = stationList.stationCostTotals(),
                totalCosts = totalCosts,
                categoryCosts = stationList.costsByCategory(),
                conversion = if (totalCosts.all { it.currency == main }) null else convert(totalCosts, main, rates),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _message = MutableStateFlow<StationMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar der Detailseite; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<StationMessage?> = _message.asStateFlow()

    /** Löscht [station] und bietet über [StationMessage.Deleted] das Rückgängigmachen an. */
    fun deleteStation(station: Station) {
        viewModelScope.launch {
            _message.value = try {
                val linkedEntryIds = stations.linkedLogEntries(station.id).map { it.id }
                stations.delete(station.id)
                StationMessage.Deleted(station, linkedEntryIds)
            } catch (_: SQLException) {
                StationMessage.Failed(R.string.station_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteStation] entfernte Station wieder her und verknüpft ihre Bordbuch-Einträge erneut. */
    fun undoDeleteStation(message: StationMessage.Deleted) {
        viewModelScope.launch {
            try {
                stations.restore(message.station)
                stations.relinkLogEntries(message.linkedEntryIds, message.station.id)
            } catch (_: SQLException) {
                _message.value = StationMessage.Failed(R.string.station_restore_failed)
            }
        }
    }

    /** Meldet, dass eine neue Station gespeichert wurde, für die Snackbar der Detailseite. */
    fun onStationSaved(loggedServices: Set<StationService>) {
        _message.value = StationMessage.Saved(loggedServices)
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: StationMessage) {
        _message.compareAndSet(shown, null)
    }
}
