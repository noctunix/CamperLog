package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.ParsedLocation
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationError
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.defaultStationDate
import app.restvolt.camperlog.domain.parseLocationText
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toStation
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/** Zustand des Stationsformulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class StationEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: StationInput = StationInput(),
    val vehicles: List<Vehicle> = emptyList(),
    /** Touren aller Fahrzeuge für das Tour-Auswahlfeld; die Oberfläche filtert nach [StationInput.vehicleId]. */
    val tours: List<Tour> = emptyList(),
    val errors: Map<StationField, StationError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Der letzte Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
)

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class StationDraft(val input: StationInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert eine Station. [stationId] 0 legt eine neue Station an; dafür
 * entscheiden [initialTourId]/[initialType]/[prefillLatitude]/[prefillLongitude]/[prefillPlace]
 * über die Vorbelegung (aus der Tourdetailseite bzw. aus einem `geo:`-Link, 13.5 Nr. 4).
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt der gespeicherten Station angezeigt.
 */
class EditStationViewModel(
    private val repository: StationRepository,
    private val tours: TourRepository,
    private val vehicles: VehicleRepository,
    stationId: Long,
    initialTourId: Long? = null,
    initialType: StationType? = null,
    prefillLatitude: Double? = null,
    prefillLongitude: Double? = null,
    prefillPlace: String? = null,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = LocalDate::now,
    private val timeNow: () -> LocalTime = LocalTime::now,
) : ViewModel() {

    private val draft: StationDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        StationEditUiState(isNew = stationId == 0L, isLoading = stationId != 0L).let { state ->
            when {
                draft != null -> state.copy(input = draft.input, isDirty = true)
                stationId == 0L -> state.copy(
                    input = StationInput(
                        tourId = initialTourId,
                        type = initialType ?: StationType.OVERNIGHT,
                        place = prefillPlace.orEmpty(),
                        latitude = prefillLatitude,
                        longitude = prefillLongitude,
                        coordinateSource = if (prefillLatitude != null) CoordinateSource.ENTERED else null,
                        nights = if (initialType == StationType.OVERNIGHT) "1" else "",
                        locationText = if (prefillLatitude != null && prefillLongitude != null) "$prefillLatitude, $prefillLongitude" else "",
                    ),
                )
                else -> state
            }
        },
    )
    val uiState: StateFlow<StationEditUiState> = _uiState.asStateFlow()

    private var original: Station? = null
    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            vehicles.observeVehicles().collect { list -> _uiState.update { it.copy(vehicles = list) } }
        }
        viewModelScope.launch {
            tours.observeTours().collect { list -> _uiState.update { it.copy(tours = list) } }
        }
        if (stationId != 0L) {
            viewModelScope.launch {
                val station = repository.observeStation(stationId).first()
                original = station
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else station?.toInput() ?: it.input
                    it.copy(isLoading = false, notFound = station == null, input = input)
                }
            }
        } else if (draft == null) {
            viewModelScope.launch { applyNewStationDefaults(initialTourId) }
        }
    }

    /** Vorbelegung einer neuen Station nach 3.3: Datum/Uhrzeit aus der Tour bzw. dem aktuellen Fahrzeug und Tag. */
    private suspend fun applyNewStationDefaults(tourId: Long?) {
        if (tourId != null) {
            val tour = tours.observeTour(tourId).first() ?: return
            val existing = repository.observeForTour(tourId).first()
            val date = defaultStationDate(tour, existing, today())
            val time = if (date == today()) timeNow() else null
            _uiState.update {
                if (it.isDirty) it else it.copy(input = it.input.copy(vehicleId = tour.vehicleId, date = date, time = time))
            }
        } else {
            val vehicleId = vehicles.observeCurrentVehicle().first().id
            val date = today()
            val resolvedTourId = repository.defaultTourId(vehicleId, date)
            _uiState.update {
                if (it.isDirty) {
                    it
                } else {
                    it.copy(input = it.input.copy(vehicleId = vehicleId, tourId = resolvedTourId, date = date, time = timeNow()))
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (StationInput) -> StationInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    /** Wechselt die Stationsart; bei einem Wechsel zu Übernachtung wird "Nächte" auf 1 vorbelegt, sofern noch leer (3.2). */
    fun onTypeChange(type: StationType) = onInputChange { input ->
        val nights = if (type == StationType.OVERNIGHT && input.nights.isBlank()) "1" else input.nights
        input.copy(type = type, nights = nights)
    }

    /** Wählt eine Tour (oder "Keine Tour" bei `null`); das Fahrzeug folgt dann der Tour (2.4). */
    fun onTourChange(tourId: Long?) {
        val tour = _uiState.value.tours.firstOrNull { it.id == tourId }
        onInputChange { input -> input.copy(tourId = tourId, vehicleId = tour?.vehicleId ?: input.vehicleId) }
    }

    /** Liest den eingefügten Text offline (5.1); erkannte Koordinaten oder ein Link werden übernommen. */
    fun onLocationTextChange(text: String) = onInputChange { input ->
        when (val parsed = parseLocationText(text)) {
            is ParsedLocation.Coordinates -> input.copy(
                locationText = text,
                latitude = parsed.latitude,
                longitude = parsed.longitude,
                coordinateSource = CoordinateSource.ENTERED,
                mapLink = null,
            )
            is ParsedLocation.MapLinkOnly -> input.copy(
                locationText = text,
                latitude = null,
                longitude = null,
                coordinateSource = null,
                mapLink = parsed.link,
            )
            ParsedLocation.ShortLinkUnsupported, ParsedLocation.NotRecognized -> input.copy(
                locationText = text,
                latitude = null,
                longitude = null,
                coordinateSource = null,
                mapLink = null,
            )
        }
    }

    /** Validiert und speichert; bei Erfolg wird [StationEditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val errors = state.input.validate()
        if (errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update { it.copy(errors = errors, rejectedSaves = it.rejectedSaves + 1) }
            return
        }
        val station = state.input.toStation(original)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(station)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Die Fehlermeldung zu [StationEditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun StationEditUiState.withErrors(): StationEditUiState {
        if (!showErrors) return copy(errors = emptyMap())
        return copy(errors = input.validate())
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(StationDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "station_draft"
