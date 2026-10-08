package app.restvolt.camperlog.ui.tours

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.completeTour
import app.restvolt.camperlog.ui.VehicleScopeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    /** Anzahl Stationen je Tour, für die Kartenzeile "· 9 Stationen"; fehlende Einträge heißen 0. */
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
    /**
     * [stations] sind die mit der Tour gelöschten Stationen, [linkedEntryIdsByStation] je Stations-id
     * die davor mit ihr verknüpften Bordbuch-Einträge, für ein vollständiges Rückgängig.
     */
    data class Deleted(
        val tour: Tour,
        val stations: List<Station> = emptyList(),
        val linkedEntryIdsByStation: Map<Long, List<Long>> = emptyMap(),
    ) : ToursMessage
    data object Saved : ToursMessage
    data object Finished : ToursMessage
    data class Failed(@StringRes val text: Int) : ToursMessage
}

/** Liefert die gefilterte Tourenliste und löscht Touren. */
class ToursViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val tracks: TrackRepository,
    private val filterSettings: VehicleScopeSettings,
    private val today: () -> LocalDate = LocalDate::now,
    private val onTourFinished: (Long) -> Unit = {},
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

    private val _finishingTourId = MutableStateFlow<Long?>(null)
    val finishingTourId: StateFlow<Long?> = _finishingTourId.asStateFlow()

    /** Beendet eine laufende Tour mit denselben Regeln wie die Detailseite. */
    fun finish(tour: Tour, endDate: LocalDate) {
        if (tour.endDate != null || _finishingTourId.value != null) return
        _finishingTourId.value = tour.id
        viewModelScope.launch {
            try {
                if (completeTour(tour.id, endDate, today(), repository, stations, tracks)) {
                    onTourFinished(tour.id)
                    _message.value = ToursMessage.Finished
                } else {
                    _message.value = ToursMessage.Failed(R.string.tour_finish_failed)
                }
            } catch (_: SQLException) {
                _message.value = ToursMessage.Failed(R.string.tour_finish_failed)
            } finally {
                _finishingTourId.value = null
            }
        }
    }

    /**
     * Löscht [tour] und explizit ihre Stationen (ihre verknüpften Bordbuch-Einträge bleiben, SET NULL).
     * Bietet über [ToursMessage.Deleted] das vollständige Rückgängigmachen an. Die Stationen
     * würden in Room auch über CASCADE verschwinden, das explizite Löschen bleibt aber unabhängig davon
     * richtig und macht aus dem Löschen hier dieselbe Reihenfolge wie beim Rückgängigmachen.
     */
    fun delete(tour: Tour) {
        viewModelScope.launch {
            _message.value = try {
                val tourStations = stations.observeForTour(tour.id).first()
                val linkedEntryIds = tourStations.associate { it.id to stations.linkedLogEntries(it.id).map(LogEntry::id) }
                tourStations.forEach { stations.delete(it.id) }
                repository.delete(tour.id)
                ToursMessage.Deleted(tour, tourStations, linkedEntryIds)
            } catch (_: SQLException) {
                ToursMessage.Failed(R.string.tours_delete_failed)
            }
        }
    }

    /** Stellt eine über [delete] entfernte Tour samt ihren Stationen wieder her und verknüpft deren Bordbuch-Einträge erneut. */
    fun undoDelete(message: ToursMessage.Deleted) {
        viewModelScope.launch {
            try {
                repository.restore(message.tour)
                message.stations.forEach { stations.restore(it) }
                message.stations.forEach { station ->
                    stations.relinkLogEntries(message.linkedEntryIdsByStation[station.id].orEmpty(), station.id)
                }
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
