package app.restvolt.camperlog.ui.tours

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
import app.restvolt.camperlog.ui.VehicleScopeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Tourenliste. [tours] ist bereits nach Fahrzeug, Suche und Jahr gefiltert. */
data class ToursUiState(
    val isLoading: Boolean = true,
    val hasAnyTour: Boolean = false,
    val tours: List<Tour> = emptyList(),
    val years: List<Int> = emptyList(),
    val query: String = "",
    val selectedYear: Int? = null,
    /** Alle Fahrzeuge für den Wechsler; die Tourenliste zeigt den Wechsler nur ab zwei Einträgen. */
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
    /** Ob gerade die Touren aller Fahrzeuge angezeigt werden statt nur die des aktuellen. */
    val showAllVehicles: Boolean = false,
    /** Anzahl Stationen je Tour, für die Kartenzeile "· 9 Stationen" (6.1); fehlende Einträge heißen 0. */
    val stationCounts: Map<Long, Int> = emptyMap(),
)

/** Zwischenergebnis der Fahrzeugfilterung, bevor Suche und Jahr angewendet werden. */
private data class VehicleFilter(
    val tours: List<Tour>,
    val vehicles: List<Vehicle>,
    val currentVehicleId: Long,
    val allVehicles: Boolean,
    val stationCounts: Map<Long, Int>,
)

/** Rückmeldungen, die die Tourenliste als Snackbar anzeigt. */
sealed interface ToursMessage {
    data class Deleted(val tour: Tour) : ToursMessage
    data object Saved : ToursMessage
    data class Failed(@StringRes val text: Int) : ToursMessage
}

/** Liefert die gefilterte Tourenliste und löscht Touren. */
class ToursViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val filterSettings: VehicleScopeSettings,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedYear = MutableStateFlow<Int?>(null)
    private val showAllVehicles = MutableStateFlow(filterSettings.allVehicles)

    val uiState: StateFlow<ToursUiState> = combine(
        combine(
            repository.observeTours(),
            vehicles.observeVehicles(),
            vehicles.observeCurrentVehicle(),
            showAllVehicles,
            stations.observeForVehicle(null),
        ) { tours, vehicleList, current, allVehicles, allStations ->
            val counts = allStations.mapNotNull(Station::tourId).groupingBy { it }.eachCount()
            VehicleFilter(tours, vehicleList, current.id, allVehicles, counts)
        },
        query,
        selectedYear,
    ) { filter, query, year ->
        val visible = filter.tours.filter { filter.allVehicles || it.vehicleId == filter.currentVehicleId }
        val years = visible.map(Tour::year).distinct().sortedDescending()
        val activeYear = year?.takeIf { it in years }
        ToursUiState(
            isLoading = false,
            hasAnyTour = visible.isNotEmpty(),
            tours = visible.filter { tour ->
                tour.destination.contains(query.trim(), ignoreCase = true) &&
                    (activeYear == null || tour.year == activeYear)
            },
            years = years,
            query = query,
            selectedYear = activeYear,
            vehicles = filter.vehicles,
            currentVehicleId = filter.currentVehicleId,
            showAllVehicles = filter.allVehicles,
            stationCounts = filter.stationCounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToursUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Setzt den Jahresfilter; `null` zeigt alle Jahre. */
    fun onYearSelected(year: Int?) {
        selectedYear.value = year
    }

    /** Macht [id] zum aktuellen Fahrzeug und verlässt dabei die Ansicht „Alle Fahrzeuge". */
    fun onSelectVehicle(id: Long) {
        showAllVehicles.value = false
        filterSettings.allVehicles = false
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }

    /** Zeigt die Touren aller Fahrzeuge, ohne das aktuelle Fahrzeug zu ändern. */
    fun onSelectAllVehicles() {
        showAllVehicles.value = true
        filterSettings.allVehicles = true
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
}
