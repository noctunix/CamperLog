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
import app.restvolt.camperlog.domain.PlaceSearchController
import app.restvolt.camperlog.domain.PlaceSearchHit
import app.restvolt.camperlog.domain.PlaceSearchProvider
import app.restvolt.camperlog.domain.PlaceSearchResult
import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.allowedServices
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
import app.restvolt.camperlog.domain.validation
import app.restvolt.camperlog.domain.withElectricityBilling
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [EditStationViewModel]). */
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
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Die id der Station, sobald sie bekannt ist: für eine neue Station ab ihrer ersten Anlage, sonst ab dem Laden. */
    val stationId: Long = 0,
)

/**
 * Welche Ver-/Entsorgungs-Häkchen des aktuellen Formularstands ins Bordbuch eingetragen werden -
 * live aus [StationInput.services] abgeleitet (wie [toStation] sie beim Speichern filtert), statt
 * erst nach einem erfolgreichen [EditStationViewModel.attemptPersist] bekannt: Ein gerade erst
 * angehaktes Häkchen zählt damit schon beim sofortigen Verlassen, auch innerhalb der Autosave-
 * Verzögerung.
 */
internal val StationEditUiState.loggedServices: Set<StationService>
    get() = input.services.intersect(input.type.allowedServices).filterTo(mutableSetOf()) { it in SYNCED_SERVICE_LOG_TYPES }

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für eine neue Station immer -
 * ihr Formular ist nur über eine bewusste Typauswahl erreichbar, anders als bei einer Tour oder
 * Reparatur gibt es also keinen rein zufällig geöffneten, komplett leeren Entwurf, der sich ohne
 * Rückfrage verwerfen ließe. Für eine bestehende Station, solange sich seit dem Öffnen des Formulars
 * wirklich etwas geändert hat.
 */
internal fun StationEditUiState.needsLeaveConfirmation(): Boolean = if (isNew) true else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class StationDraft(
    val input: StationInput,
    val showErrors: Boolean,
    /** Für eine neue Station die id der schon automatisch angelegten Station, falls es sie schon gibt. */
    val persistedStationId: Long? = null,
)

/**
 * Lädt, validiert und speichert eine Station. [stationId] 0 legt eine neue Station an; dafür
 * entscheiden [initialTourId]/[initialType]/[prefillLatitude]/[prefillLongitude]/[prefillPlace]
 * über die Vorbelegung (aus der Tourdetailseite bzw. aus einem `geo:`-Link).
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für eine neue wie für eine bestehende Station. Eine neue Station wird dafür
 * sofort nach ihrer Vorbelegung ([applyNewStationDefaults]) als echter Datensatz angelegt - dadurch
 * können Fotos von Anfang an angehängt werden, ohne auf einen expliziten Speichern-Erfolg zu warten.
 * Verlässt der Nutzer das Formular über [onDiscardConfirmed] statt es zu behalten, räumt [onCleared]
 * den Datensatz wieder auf - für eine neue Station heißt das Löschen (mit ihren Anhängen), für eine
 * bestehende das Zurücksetzen auf den beim Öffnen geladenen Stand (siehe [openedStation]).
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt der gespeicherten Station angezeigt; für eine neue Station sichert das auch die
 * id der schon automatisch angelegten Station gegen einen solchen Neustart ab.
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
    placeSearchProvider: PlaceSearchProvider = NoOpPlaceSearchProvider,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = LocalDate::now,
    private val timeNow: () -> LocalTime = LocalTime::now,
    private val locale: () -> Locale = { supportedLocale(Locale.getDefault()) },
) : ViewModel() {

    /** Standortbestimmung für den Platzabschnitt, nur sichtbar, wenn die Oberfläche den Standort-Schalter an sieht. */
    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)

    /** Wetterabfrage für die "Wetter"-Karte, nur sichtbar, wenn die Oberfläche den Wetter-Schalter an sieht. */
    val weatherCapture = WeatherCaptureController(weatherProvider, viewModelScope)

    /**
     * Abfrage für den Temperatur-Button bei [StationInput.manualTemperatureC]: derselbe Mechanismus
     * wie [weatherCapture], aber mit eigenem Zustand, damit sie das volle Wetter der "Wetter"-Karte
     * nicht mit befüllt.
     */
    val temperatureCapture = WeatherCaptureController(weatherProvider, viewModelScope)

    /** Ortssuche für "Ort suchen" im Platzabschnitt, nur sichtbar, wenn die Oberfläche den Wetter-Schalter an sieht. */
    val placeSearch = PlaceSearchController(placeSearchProvider, viewModelScope)

    private val draft: StationDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        StationEditUiState(isNew = stationId == 0L, isLoading = stationId != 0L, stationId = stationId).let { state ->
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

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: Station? = null

    /**
     * Für eine bestehende Station der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für eine neue Station `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedStation: Station? = null
    private var showErrors = draft?.showErrors ?: false

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide eine neue Station anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

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
            temperatureCapture.state.collect { captureState ->
                if (captureState is WeatherCaptureState.Success) {
                    val wholeDegrees = Math.round(captureState.snapshot.temperatureDeciC / 10.0)
                    onInputChange { input -> input.copy(manualTemperatureC = wholeDegrees.toString()) }
                    temperatureCapture.reset()
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
                openedStation = station
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else station?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = station == null, input = input)
                }
            }
        } else {
            viewModelScope.launch {
                val restoredId = draft?.persistedStationId
                if (restoredId != null) {
                    original = repository.observeStation(restoredId).first()
                    if (original != null) _uiState.update { it.copy(stationId = restoredId) }
                }
                if (original == null) {
                    applyNewStationDefaults(initialTourId)
                    attemptPersist()
                }
            }
        }
    }

    /**
     * Vorbelegung einer neuen Station: Datum/Uhrzeit aus der Tour bzw. dem aktuellen Fahrzeug und Tag.
     * Läuft vor dem allerersten [attemptPersist], damit das Pflichtfeld Datum dabei schon gesetzt ist.
     */
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

    /**
     * Übernimmt eine Formularänderung. Es gibt keinen Speichern-Versuch mehr, der Fehler sichtbar
     * machen könnte - stattdessen macht schon die erste Eingabe alle aktuellen Fehler sichtbar, und
     * löst außerdem den debounced Autosave aus.
     */
    fun onInputChange(transform: (StationInput) -> StationInput) {
        showErrors = true
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
        scheduleAutosave()
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

    /** Tastendruck auf "Temperatur automatisch abfragen"; ohne Koordinaten passiert nichts. */
    fun onFetchTemperature() {
        val input = _uiState.value.input
        val latitude = input.latitude ?: return
        val longitude = input.longitude ?: return
        temperatureCapture.fetch(latitude, longitude)
    }

    /** Tastendruck auf "Suchen" in der Ortssuche; eine leere Eingabe tut nichts. */
    fun onSearchPlace(query: String) = placeSearch.search(query, locale().language)

    /**
     * Übernimmt einen Treffer der Ortssuche: Koordinaten über denselben Pfad wie eine eingefügte
     * Koordinate, der Platztext nur, wenn er noch leer ist, um keine eigene Eingabe zu überschreiben.
     */
    fun onPlaceSearchPick(hit: PlaceSearchHit) {
        onLocationTextChange("${hit.latitude}, ${hit.longitude}")
        if (_uiState.value.input.place.isBlank()) {
            onInputChange { it.copy(place = hit.displayName) }
        }
        placeSearch.reset()
    }

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

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für eine neue Station), sonst aktualisieren. Das einzige Pflichtfeld ist das
     * Datum, das für eine neue Station schon vor dem allerersten Aufruf durch
     * [applyNewStationDefaults] gesetzt ist; fehlt es trotzdem, bleibt der zuletzt gespeicherte Stand
     * unverändert stehen. Jede andere ungültige Eingabe wird stattdessen entschärft, siehe
     * [sanitizedForAutosave].
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val basis = original
            val loc = locale()
            val day = today()
            val effectiveInput = state.input.sanitizedForAutosave(day, loc)
            if (effectiveInput.validation(day, loc).errors.isNotEmpty()) return@withLock
            val station = effectiveInput.toStation(basis, loc)
            try {
                val id = repository.save(station)
                original = repository.observeStation(id).first() ?: station.copy(id = id)
                _uiState.update { it.copy(stationId = id, saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /**
     * Entschärft eine Eingabe für [attemptPersist]: Jede ungültige Einzelangabe außer dem fehlenden
     * Datum fällt weg, statt den Schreibzugriff für die ganze Station zu blockieren - der Rest der
     * Eingabe speichert sich weiter, während das betroffene Feld inline seinen Fehler zeigt. Das
     * verändert nur den gespeicherten Stand, nicht die Anzeige im Formular.
     */
    private fun StationInput.sanitizedForAutosave(today: LocalDate, locale: Locale): StationInput {
        val validation = validation(today, locale)
        val errors = validation.errors
        var sanitized = this
        if (StationField.COORDINATES in errors) sanitized = sanitized.copy(latitude = null, longitude = null, coordinateSource = null, accuracyM = null)
        if (StationField.NIGHTS in errors) sanitized = sanitized.copy(nights = "")
        if (StationField.NAME in errors) sanitized = sanitized.copy(name = "")
        if (StationField.PLACE in errors) sanitized = sanitized.copy(place = "")
        if (StationField.NOTES in errors) sanitized = sanitized.copy(notes = "")
        if (StationField.MAP_LINK in errors) sanitized = sanitized.copy(mapLink = null)
        if (StationField.SERVICES in errors) sanitized = sanitized.copy(services = sanitized.services.filterNotTo(mutableSetOf()) { it in SYNCED_SERVICE_LOG_TYPES })
        if (StationField.ELECTRICITY in errors) sanitized = sanitized.copy(electricityBilling = null)
        if (StationField.WIFI_COST in errors) sanitized = sanitized.copy(wifiCostAmount = "")
        if (StationField.TOLL_COUNTRY in errors) sanitized = sanitized.copy(tollCountry = "")
        if (StationField.TOLL_VALID_UNTIL in errors) sanitized = sanitized.copy(tollValidUntil = null)
        if (StationField.TOLL_PAYMENT_METHOD in errors) sanitized = sanitized.copy(tollPaymentMethod = "")
        if (StationField.FERRY_BOOKING_REFERENCE in errors) sanitized = sanitized.copy(ferryBookingReference = "")
        if (StationField.ODOMETER_KM in errors) sanitized = sanitized.copy(odometerKm = "")
        if (StationField.MANUAL_TEMPERATURE in errors) sanitized = sanitized.copy(manualTemperatureC = "")
        if (StationField.LINK in errors) sanitized = sanitized.copy(link = "")
        if (validation.costErrors.isNotEmpty()) {
            sanitized = sanitized.copy(costs = sanitized.costs.filterIndexed { index, _ -> index !in validation.costErrors.keys })
        }
        return sanitized
    }

    /** Plant [attemptPersist] mit kurzer Verzögerung, damit schnell aufeinanderfolgende Tastendrücke nur einen Schreibzugriff auslösen. */
    private fun scheduleAutosave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            attemptPersist()
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
        val state = _uiState.value
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(StationDraft(state.input, showErrors, original?.id))
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für eine neue Station heißt das, den automatisch angelegten
     * Datensatz (mit seinen Anhängen) zu löschen; für eine bestehende, sie auf den beim Öffnen des
     * Formulars geladenen Stand zurückzusetzen (siehe [openedStation]). Bricht außerdem einen noch
     * wartenden Autosave ab, damit er nicht doch noch zwischen diesem Aufruf und dem tatsächlichen
     * Verlassen feuert.
     */
    fun onDiscardConfirmed() {
        discardConfirmed = true
        pendingSaveJob?.cancel()
    }

    /** Ob [onDiscardConfirmed] für diese Sitzung schon aufgerufen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /**
     * Nach [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn.
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && discardConfirmed -> deleteNewStationDraft()
                !state.isNew && discardConfirmed -> revertToOpenedStation()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /** Löscht die explizit verworfene neue Station (mit ihren Anhängen) wieder. */
    private suspend fun deleteNewStationDraft() {
        original?.id?.let { id -> runCatching { repository.delete(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Setzt eine bestehende Station auf [openedStation] zurück. */
    private suspend fun revertToOpenedStation() {
        val baseline = openedStation ?: return
        persistMutex.withLock { runCatching { repository.save(baseline) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }
}

private const val DRAFT_KEY = "station_draft"

/** Verzögerung zwischen einer Formularänderung einer Station und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L

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

/** Platzhalter für Tests und Vorschauen ohne Netzwerk; liefert nie ein Ergebnis. */
private object NoOpPlaceSearchProvider : PlaceSearchProvider {
    override suspend fun search(query: String, language: String): PlaceSearchResult = PlaceSearchResult.Error
}
