package app.restvolt.camperlog.ui.stations

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.ui.VehicleScopeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Die aktuell laufende Tour des aktuellen Fahrzeugs, für die Karte "Laufende Tour" (6.3). */
data class RunningTour(val tourId: Long, val destination: String, val dayNumber: Int, val totalDays: Int)

/** Zustand des Stationen-Reiters. [stations] ist bereits nach Fahrzeug, Suche und Filtern gefiltert. */
data class StationsUiState(
    val isLoading: Boolean = true,
    val hasAnyStation: Boolean = false,
    val stations: List<Station> = emptyList(),
    /** Zielname je Tour-id, um den Zeilen ohne weitere Abfrage den Tournamen mitzugeben. */
    val tourNames: Map<Long, String> = emptyMap(),
    val query: String = "",
    val selectedType: StationType? = null,
    val favoriteOnly: Boolean = false,
    /** Alle Fahrzeuge für den Wechsler; der Reiter zeigt ihn nur ab zwei Einträgen. */
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
    /** Ob gerade die Stationen aller Fahrzeuge angezeigt werden statt nur die des aktuellen. */
    val showAllVehicles: Boolean = false,
    val runningTour: RunningTour? = null,
    val showWhatsNew: Boolean = false,
)

/** Zwischenergebnis aus allen Quell-Flows, bevor Suche und Filter angewendet werden. */
private data class StationsSource(
    val stations: List<Station>,
    val tours: List<Tour>,
    val vehicles: List<Vehicle>,
    val currentVehicleId: Long,
    val allVehicles: Boolean,
)

/** Rückmeldungen, die der Stationen-Reiter als Snackbar anzeigt. */
sealed interface StationsMessage {
    data class Deleted(val station: Station) : StationsMessage
    data class Failed(@StringRes val text: Int) : StationsMessage
}

/** Liefert die gefilterte Stationenliste (6.3), löscht Stationen und beobachtet die laufende Tour. */
class StationsViewModel(
    private val stations: StationRepository,
    private val tours: TourRepository,
    private val vehicles: VehicleRepository,
    private val filterSettings: VehicleScopeSettings,
    private val whatsNewSettings: StationsWhatsNewSettings,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedType = MutableStateFlow<StationType?>(null)
    private val favoriteOnly = MutableStateFlow(false)
    private val showAllVehicles = MutableStateFlow(filterSettings.allVehicles)
    private val showWhatsNew = MutableStateFlow(whatsNewSettings.pending)

    val uiState: StateFlow<StationsUiState> = combine(
        combine(
            stations.observeForVehicle(null),
            tours.observeTours(),
            vehicles.observeVehicles(),
            vehicles.observeCurrentVehicle(),
            showAllVehicles,
        ) { stationList, tourList, vehicleList, current, allVehicles ->
            StationsSource(stationList, tourList, vehicleList, current.id, allVehicles)
        },
        query,
        selectedType,
        favoriteOnly,
        showWhatsNew,
    ) { source, query, type, favorite, whatsNew ->
        val visible = source.stations.filter { source.allVehicles || it.vehicleId == source.currentVehicleId }
        val trimmedQuery = query.trim()
        val tourNames = source.tours.associate { it.id to it.destination }
        StationsUiState(
            isLoading = false,
            hasAnyStation = visible.isNotEmpty(),
            stations = visible.filter { station ->
                (type == null || station.type == type) &&
                    (!favorite || station.favorite) &&
                    matchesQuery(station, trimmedQuery)
            },
            tourNames = tourNames,
            query = query,
            selectedType = type,
            favoriteOnly = favorite,
            vehicles = source.vehicles,
            currentVehicleId = source.currentVehicleId,
            showAllVehicles = source.allVehicles,
            runningTour = runningTourOf(source.tours, source.currentVehicleId, today()),
            showWhatsNew = whatsNew,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationsUiState())

    private fun matchesQuery(station: Station, query: String): Boolean {
        if (query.isEmpty()) return true
        return station.name.contains(query, ignoreCase = true) ||
            station.place.contains(query, ignoreCase = true) ||
            station.notes.contains(query, ignoreCase = true)
    }

    /** Die Tour von [vehicleId], deren Zeitraum [date] enthält (3.3), bei mehreren die mit dem spätesten Start. */
    private fun runningTourOf(tours: List<Tour>, vehicleId: Long, date: LocalDate): RunningTour? {
        val tour = tours
            .filter { it.vehicleId == vehicleId && date in it.startDate..it.endDate }
            .maxByOrNull { it.startDate }
            ?: return null
        val dayNumber = java.time.temporal.ChronoUnit.DAYS.between(tour.startDate, date).toInt() + 1
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(tour.startDate, tour.endDate).toInt() + 1
        return RunningTour(tour.id, tour.destination, dayNumber, totalDays)
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Setzt den Typfilter; `null` zeigt alle Typen. */
    fun onTypeSelected(type: StationType?) {
        selectedType.value = type
    }

    /** Schaltet den Filter "Gerne wieder" um (13.5 Nr. 5). */
    fun onFavoriteOnlyChange(value: Boolean) {
        favoriteOnly.value = value
    }

    /** Macht [id] zum aktuellen Fahrzeug und verlässt dabei die Ansicht „Alle Fahrzeuge" (2.4). */
    fun onSelectVehicle(id: Long) {
        showAllVehicles.value = false
        filterSettings.allVehicles = false
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }

    /** Zeigt die Stationen aller Fahrzeuge, ohne das aktuelle Fahrzeug zu ändern. */
    fun onSelectAllVehicles() {
        showAllVehicles.value = true
        filterSettings.allVehicles = true
    }

    /** Schließt die "Neu: Stationen"-Karte endgültig. */
    fun dismissWhatsNew() {
        whatsNewSettings.pending = false
        showWhatsNew.value = false
    }

    private val _message = MutableStateFlow<StationsMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar der Liste; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<StationsMessage?> = _message.asStateFlow()

    /** Löscht [station] und bietet über [StationsMessage.Deleted] das Rückgängigmachen an (8.3). */
    fun deleteStation(station: Station) {
        viewModelScope.launch {
            _message.value = try {
                stations.delete(station.id)
                StationsMessage.Deleted(station)
            } catch (_: SQLException) {
                StationsMessage.Failed(R.string.station_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteStation] entfernte Station unverändert wieder her. */
    fun undoDeleteStation(station: Station) {
        viewModelScope.launch {
            try {
                stations.restore(station)
            } catch (_: SQLException) {
                _message.value = StationsMessage.Failed(R.string.station_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: StationsMessage) {
        _message.compareAndSet(shown, null)
    }
}
