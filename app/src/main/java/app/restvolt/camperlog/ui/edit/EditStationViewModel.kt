package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationPermissionGate
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.ParsedLocation
import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationError
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.StationCostInput
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.WeatherCaptureController
import app.restvolt.camperlog.domain.WeatherCaptureState
import app.restvolt.camperlog.domain.WeatherProvider
import app.restvolt.camperlog.domain.WeatherResult
import app.restvolt.camperlog.domain.defaultCostCategory
import app.restvolt.camperlog.domain.defaultStationDate
import app.restvolt.camperlog.domain.parseLocationText
import app.restvolt.camperlog.domain.supportedLocale
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toStation
import app.restvolt.camperlog.domain.validate
import app.restvolt.camperlog.domain.validation
import app.restvolt.camperlog.domain.withElectricityBilling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

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
    /** Fehler je Kostenzeile, Schlüssel ist der Index in [StationInput.costs]. */
    val costErrors: Map<Int, StationError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Welche Ver-/Entsorgungs-Häkchen beim letzten erfolgreichen Speichern ins Bordbuch eingetragen wurden. */
    val loggedServices: Set<StationService> = emptySet(),
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
 * über die Vorbelegung (aus der Tourdetailseite bzw. aus einem `geo:`-Link).
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt der gespeicherten Station angezeigt.
 *
 * @param locale liefert die aktuelle Sprache für die Beträge und Dezimalzahlen der Stromabrechnung;
 *   wird bei jedem Zugriff neu gelesen, damit ein Sprachwechsel bei laufendem ViewModel greift
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
    locationProvider: LocationProvider = NoOpLocationProvider,
    locationPermissionGate: LocationPermissionGate = NoOpLocationPermissionGate,
    weatherProvider: WeatherProvider = NoOpWeatherProvider,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = LocalDate::now,
    private val timeNow: () -> LocalTime = LocalTime::now,
    private val locale: () -> Locale = { supportedLocale(Locale.getDefault()) },
) : ViewModel() {

    /** Standortbestimmung für den Platzabschnitt, nur sichtbar, wenn die Oberfläche den Standort-Schalter an sieht. */
    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)

    /** Wetterabfrage für die "Wetter"-Karte, nur sichtbar, wenn die Oberfläche den Wetter-Schalter an sieht. */
    val weatherCapture = WeatherCaptureController(weatherProvider, viewModelScope)

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
            locationCapture.state.collect { captureState ->
                if (captureState is LocationCaptureState.Found) {
                    onInputChange { input ->
                        input.copy(
                            latitude = captureState.fix.latitude,
                            longitude = captureState.fix.longitude,
                            coordinateSource = CoordinateSource.GPS,
                            accuracyM = captureState.fix.accuracyM,
                        )
                    }
                    locationCapture.clear()
                }
            }
        }
        viewModelScope.launch {
            weatherCapture.state.collect { captureState ->
                if (captureState is WeatherCaptureState.Success) {
                    onInputChange { input -> input.copy(weather = captureState.snapshot) }
                    weatherCapture.reset()
                }
            }
        }
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
                    val input = if (it.isDirty) it.input else station?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = station == null, input = input)
                }
            }
        } else if (draft == null) {
            viewModelScope.launch { applyNewStationDefaults(initialTourId) }
        }
    }

    /** Vorbelegung einer neuen Station: Datum/Uhrzeit aus der Tour bzw. dem aktuellen Fahrzeug und Tag. */
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

    /** Wechselt die Stationsart; bei einem Wechsel zu Übernachtung wird "Nächte" auf 1 vorbelegt, sofern noch leer. */
    fun onTypeChange(type: StationType) = onInputChange { input ->
        val nights = if (type == StationType.OVERNIGHT && input.nights.isBlank()) "1" else input.nights
        input.copy(type = type, nights = nights)
    }

    /** Wählt eine Tour (oder "Keine Tour" bei `null`); das Fahrzeug folgt dann der Tour. */
    fun onTourChange(tourId: Long?) {
        val tour = _uiState.value.tours.firstOrNull { it.id == tourId }
        onInputChange { input -> input.copy(tourId = tourId, vehicleId = tour?.vehicleId ?: input.vehicleId) }
    }

    /** Liest den eingefügten Text offline; erkannte Koordinaten oder ein Link werden übernommen. */
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

    /** Entfernt vom GPS gesetzte Koordinaten wieder ("Koordinaten entfernen" im Erfolgszustand). */
    fun onRemoveGpsCoordinates() = onInputChange { input ->
        input.copy(latitude = null, longitude = null, coordinateSource = null, accuracyM = null)
    }

    /** Tastendruck auf "Wetter abrufen"/"Aktualisieren"; ohne Koordinaten passiert nichts. */
    fun onFetchWeather() {
        val input = _uiState.value.input
        val latitude = input.latitude ?: return
        val longitude = input.longitude ?: return
        weatherCapture.fetch(latitude, longitude)
    }

    /** "Entfernen" im Erfolgszustand der Wetterkarte. */
    fun onRemoveWeather() = onInputChange { it.copy(weather = null) }

    /** Wechselt die Stromabrechnungsart; siehe [withElectricityBilling] für das Verhalten je Feld. */
    fun onElectricityBillingChange(billing: ElectricityBilling?) = onInputChange { it.withElectricityBilling(billing) }

    /** Hängt eine Kostenzeile mit der Vorgabe-Kategorie des aktuellen Stationstyps an; Währung ist die zuletzt verwendete. */
    fun onAddCost() {
        viewModelScope.launch {
            val currency = tours.lastUsedCurrency() ?: EUR
            onInputChange { input -> input.copy(costs = input.costs + StationCostInput(category = input.type.defaultCostCategory, currency = currency)) }
        }
    }

    /** Entfernt die Kostenzeile mit Index [index]. */
    fun onRemoveCost(index: Int) = onInputChange { input -> input.copy(costs = input.costs.filterIndexed { i, _ -> i != index }) }

    fun onCostCategoryChange(index: Int, category: CostCategory) = onCostChange(index) { it.copy(category = category) }

    fun onCostAmountChange(index: Int, amount: String) = onCostChange(index) { it.copy(amount = amount) }

    fun onCostCurrencyChange(index: Int, currency: Currency) = onCostChange(index) { it.copy(currency = currency) }

    fun onCostNoteChange(index: Int, note: String) = onCostChange(index) { it.copy(note = note) }

    private fun onCostChange(index: Int, transform: (StationCostInput) -> StationCostInput) = onInputChange { input ->
        input.copy(costs = input.costs.mapIndexed { i, cost -> if (i == index) transform(cost) else cost })
    }

    /** Validiert und speichert; bei Erfolg wird [StationEditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val validation = state.input.validation(today(), locale())
        if (validation.errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update {
                it.copy(errors = validation.errors, costErrors = validation.costErrors, rejectedSaves = it.rejectedSaves + 1)
            }
            return
        }
        val station = state.input.toStation(original, locale())
        val loggedServices = station.services.filterTo(mutableSetOf()) { it in SYNCED_SERVICE_LOG_TYPES }
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), costErrors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(station)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true, loggedServices = loggedServices) }
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
        if (!showErrors) return copy(errors = emptyMap(), costErrors = emptyMap())
        val validation = input.validation(locale = locale())
        return copy(errors = validation.errors, costErrors = validation.costErrors)
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(StationDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "station_draft"

/** Platzhalter für Tests und Vorschauen ohne Standorthardware; liefert nie einen Fix. */
private object NoOpLocationProvider : LocationProvider {
    override suspend fun requestFreshFix(): LocationFix? = null
    override suspend fun lastKnownFix(maxAgeMillis: Long): LocationFix? = null
    override fun isLocationEnabled(): Boolean = false
}

/** Platzhalter für Tests und Vorschauen ohne Standorthardware; hat nie eine Berechtigung. */
private object NoOpLocationPermissionGate : LocationPermissionGate {
    override fun hasPermission(): Boolean = false
    override fun hasRequestedBefore(): Boolean = false
    override fun markRequested() = Unit
}

/** Platzhalter für Tests und Vorschauen ohne Netzwerk; liefert nie ein Ergebnis. */
private object NoOpWeatherProvider : WeatherProvider {
    override suspend fun fetchCurrent(latitude: Double, longitude: Double): WeatherResult = WeatherResult.Error
}
