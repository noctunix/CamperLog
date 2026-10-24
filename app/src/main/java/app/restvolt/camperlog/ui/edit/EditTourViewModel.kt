package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostInput
import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository
import app.restvolt.camperlog.domain.FormDraftStore
import app.restvolt.camperlog.domain.QUICK_CURRENCIES
import app.restvolt.camperlog.domain.RunningTourAlreadyExistsException
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourInput
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.derivedMetrics
import app.restvolt.camperlog.domain.suggestSlug
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toTour
import app.restvolt.camperlog.domain.travelDaysBetween
import app.restvolt.camperlog.domain.validation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Currency
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [EditTourViewModel]). */
data class EditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: TourInput = TourInput(),
    /** Fahrzeuge für das Auswahlfeld; die Oberfläche zeigt es nur ab zwei Einträgen. */
    val vehicles: List<Vehicle> = emptyList(),
    val errors: Map<TourField, TourError> = emptyMap(),
    /** Fehler je Kostenzeile, Schlüssel ist der Index in [TourInput.costs]. */
    val costErrors: Map<Int, TourError> = emptyMap(),
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /**
     * Für eine bestehende Tour ein gescheiterter Speicherversuch (Meldung steht noch aus); für eine
     * neue Tour ein dauerhafter, nicht blockierender Hinweis, solange die Eingabe für dieses Fahrzeug
     * eine zweite laufende Tour erzeugen würde.
     */
    val runningTourConflict: Boolean = false,
    /** Nur bestehende Altdaten mit bereits gespeichertem Link dürfen diesen noch bearbeiten. */
    val showLegacyMapLink: Boolean = false,
    /** Schalter "GPS-Track aufzeichnen"; nur für neue, offene Touren überhaupt sichtbar/wirksam. */
    val trackSwitch: Boolean = false,
    /** Schalter "Beginnt zu Hause"; nur für neue Touren mit gesetzter Zuhause-Koordinate überhaupt sichtbar/wirksam. */
    val homeSwitch: Boolean = false,
    /**
     * Der Wert, auf den [homeSwitch] automatisch vorbelegt wurde (an, sobald eine Zuhause-Adresse
     * bekannt ist), damit ein noch unberührter Entwurf trotz dieser Vorbelegung als unberührt gilt.
     */
    val initialHomeSwitch: Boolean = false,
    /** Die id der Tour, sobald sie bekannt ist: für eine neue Tour ab ihrer ersten Anlage, sonst ab dem Laden. */
    val tourId: Long? = null,
    /** Das vorbelegte Startdatum ist Teil eines unberührten neuen Entwurfs. */
    val initialStartDate: LocalDate? = null,
    /** Vollständige Vorbelegung, damit Zurücksetzen eines neuen Entwurfs wieder unverändert ist. */
    val initialInput: TourInput? = null,
    val isFinishing: Boolean = false,
    val isFinished: Boolean = false,
    val validationAttempt: Int = 0,
)

/**
 * Ob der Entwurf einer neuen Tour noch unverändert den Vorbelegungen entspricht - also beim
 * Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun EditUiState.isUnmodifiedNewTourDraft(): Boolean = isNew &&
    !isFinished &&
    input == (initialInput ?: TourInput(startDate = initialStartDate)) &&
    !trackSwitch &&
    homeSwitch == initialHomeSwitch

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für eine neue Tour, solange
 * ihr Entwurf nicht mehr [isUnmodifiedNewTourDraft] ist (ein komplett unberührter Entwurf wird beim
 * Verlassen direkt verworfen); für eine bestehende Tour, solange sich
 * seit dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun EditUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewTourDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class TourDraft(
    val version: Int = 0,
    val ownerUuid: String? = null,
    val input: TourInput,
    val showErrors: Boolean,
    val autoTravelDays: String?,
    val autoSlug: String? = null,
    /** Für eine neue Tour die id der schon automatisch angelegten Tour, falls es sie schon gibt. */
    val persistedTourId: Long? = null,
    /** Für eine neue Tour die ids der zuletzt für den Schalter "Beginnt zu Hause" angelegten Stationen. */
    val homeStationIds: List<Long> = emptyList(),
    @Serializable(with = app.restvolt.camperlog.domain.LocalDateSerializer::class)
    val initialStartDate: LocalDate? = null,
    val initialInput: TourInput? = null,
    val openedInput: TourInput? = null,
    val openedManualCountriesAdded: Set<String>? = null,
    val openedManualCountriesRemoved: Set<String>? = null,
    val trackSwitch: Boolean = false,
    val homeSwitch: Boolean = false,
    val initialHomeSwitch: Boolean = false,
    /** Markiert eine neue Tour, deren automatisch angelegte id bereits besteht. */
    val isNew: Boolean = false,
)

/**
 * Lädt, validiert und speichert eine Tour. [tourId] 0 legt eine neue Tour an.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für eine neue wie für eine bestehende Tour. Eine neue Tour wird dafür sofort
 * als echter Datensatz angelegt; dadurch hat sie von Anfang an eine stabile id, gegen die z. B.
 * eine GPS-Aufzeichnung sofort starten kann, ohne auf einen expliziten Speichern-Erfolg zu warten.
 * Vor jedem regulären Verlassen schreibt oder verwirft die Oberfläche den Entwurf asynchron. Beim
 * Löschen des ViewModels wird kein blockierender Datenbankzugriff ausgeführt.
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart
 * des Prozesses statt der gespeicherten Tour angezeigt; für eine neue Tour sichert das auch die
 * id der schon automatisch angelegten Tour gegen einen solchen Neustart ab.
 *
 * @param stations zieht die Stationen der Tour mit, wenn sie das Fahrzeug wechselt, und legt die
 *   Zuhause-Stationen des Schalters "Beginnt zu Hause" an
 * @param checklists zieht die Checklisten der Tour mit, wenn sie das Fahrzeug wechselt
 * @param locale liefert die aktuelle Sprache für Beträge; wird bei jedem Zugriff neu gelesen,
 *   damit ein Sprachwechsel bei laufendem ViewModel greift
 * @param homeLocations liefert die Zuhause-Adressen-Historie für den Schalter "Beginnt zu Hause"; `null` blendet ihn aus
 * @param defaultHomeStationName liefert den Anzeigenamen der Zuhause-Station, falls die passende Adresse keinen eigenen Namen trägt
 */
class EditTourViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val checklists: ChecklistRepository,
    tourId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val formDraftStore: FormDraftStore,
    private val locale: () -> Locale = { app.restvolt.camperlog.domain.supportedLocale(Locale.getDefault()) },
    private val tracks: TrackRepository? = null,
    private val homeLocations: HomeLocationRepository? = null,
    private val defaultHomeStationName: () -> String = { "" },
) : ViewModel() {

    private var draft: TourDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)
        ?.let { decodeFromSavedState<TourDraft>(it) }
        ?.takeIf { it.version == TOUR_DRAFT_VERSION }

    private var initialStartDate = draft?.initialStartDate ?: LocalDate.now()
    private val _uiState = MutableStateFlow(
        EditUiState(
            isNew = tourId == 0L || draft?.isNew == true,
            isLoading = tourId != 0L || (tourId == 0L && draft?.persistedTourId != null),
            input = draft?.input ?: if (tourId == 0L) TourInput(startDate = initialStartDate) else TourInput(),
            initialStartDate = if (tourId == 0L) initialStartDate else null,
            initialInput = if (tourId == 0L || draft?.isNew == true) {
                draft?.initialInput ?: draft?.openedInput ?: TourInput(startDate = initialStartDate)
            } else null,
            trackSwitch = draft?.trackSwitch ?: false,
            homeSwitch = draft?.homeSwitch ?: false,
            initialHomeSwitch = draft?.initialHomeSwitch ?: false,
        ).let { state ->
            if (draft == null) state else state.copy(isDirty = !state.isUnmodifiedNewTourDraft())
        },
    )
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: Tour? = null

    /**
     * Für eine bestehende Tour der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [discardDraft], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für eine neue Tour `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedTour: Tour? = null
    private var showErrors = draft?.showErrors ?: false
    private var autoTravelDays: String? = draft?.autoTravelDays
    private var autoSlug: String? = draft?.autoSlug
    private var homeStationIds: List<Long> = draft?.homeStationIds ?: emptyList()
    private var openedInput: TourInput? = draft?.openedInput
    private var openedManualCountriesAdded: Set<String> = draft?.openedManualCountriesAdded.orEmpty()
    private var openedManualCountriesRemoved: Set<String> = draft?.openedManualCountriesRemoved.orEmpty()
    private var pendingDraftJob: Job? = null
    private var rawDraftSaveSucceeded = true

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide eine neue Tour anlegen. */
    private val persistMutex = Mutex()

    /** Sperrt Autosaves, sobald der Nutzer den Entwurf ausdrücklich verwirft. */
    private var discardConfirmed = false
    private var discardApplied = false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            vehicles.observeVehicles().collect { list -> _uiState.update { it.copy(vehicles = list) } }
        }
        if (tourId == 0L) {
            viewModelScope.launch {
                val currency = repository.lastUsedCurrency() ?: return@launch
                // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                _uiState.update {
                    if (it.input.costs != it.initialInput?.costs) it else it.copy(
                        input = it.input.copy(costs = listOf(CostInput(currency = currency))),
                        initialInput = it.initialInput.copy(costs = listOf(CostInput(currency = currency))),
                    )
                }
            }
            viewModelScope.launch {
                val restoredId = draft?.persistedTourId
                if (restoredId != null) {
                    val restoredTour = repository.observeTour(restoredId).first()
                    if (restoredTour != null && draft?.ownerUuid == restoredTour.uuid) {
                        original = restoredTour
                        openedTour = restoredTour
                        openedInput = draft?.openedInput ?: restoredTour.toInput(locale())
                        openedManualCountriesAdded = draft?.openedManualCountriesAdded ?: restoredTour.manualCountriesAdded
                        openedManualCountriesRemoved = draft?.openedManualCountriesRemoved ?: restoredTour.manualCountriesRemoved
                        _uiState.update { it.copy(tourId = restoredId, isLoading = false) }
                    } else {
                        persistMutex.withLock { formDraftStore.delete(TOUR_DRAFT_KIND, restoredId) }
                        savedStateHandle.remove<SavedState>(DRAFT_KEY)
                        draft = null
                        initialStartDate = LocalDate.now()
                        showErrors = false
                        autoTravelDays = null
                        autoSlug = null
                        homeStationIds = emptyList()
                        openedTour = null
                        openedInput = null
                        openedManualCountriesAdded = emptySet()
                        openedManualCountriesRemoved = emptySet()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                tourId = null,
                                input = TourInput(startDate = initialStartDate),
                                initialStartDate = initialStartDate,
                                errors = emptyMap(),
                                costErrors = emptyMap(),
                                isDirty = false,
                                trackSwitch = false,
                                homeSwitch = false,
                                initialHomeSwitch = false,
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                // Läuft dauerhaft statt nur einmal: Legt der Nutzer währenddessen (z. B. inline aus der
                // Pilot-Tour heraus) das erste Fahrzeug überhaupt an, greift die Vorbelegung auch dann noch,
                // nicht nur bei einem zufällig schon vorhandenen Fahrzeug im allerersten Zwischenstand. Ist
                // die Tour noch nicht angelegt (frisches Formular oder ein zuvor an einem Konflikt
                // gescheiterter Versuch), probiert jede Emission auch das erneut.
                combine(vehicles.observeVehicles(), vehicles.observeCurrentVehicle()) { list, current -> list to current }
                    .collect { (vehicleList, current) ->
                        _uiState.update { state ->
                            // Nur vorbelegen, solange der Nutzer noch kein Fahrzeug gewählt hat.
                            if (state.input.vehicleId != 0L) return@update state
                            // Das aktuelle Fahrzeug nur vorbelegen, wenn es ein echtes, nicht verkauftes
                            // ist; sonst das erste echte, nicht verkaufte, oder - gibt es keins - gar
                            // keine Vorauswahl. Das Demo-Fahrzeug des Tutorials zählt nie.
                            val preselectedVehicleId = vehicleList.firstOrNull { it.id == current.id && !it.isSold && !it.isDemo }?.id
                                ?: vehicleList.firstOrNull { !it.isSold && !it.isDemo }?.id
                            if (preselectedVehicleId != null) state.copy(
                                input = state.input.copy(vehicleId = preselectedVehicleId),
                                initialInput = state.initialInput?.takeIf { state.input.vehicleId == it.vehicleId }
                                    ?.copy(vehicleId = preselectedVehicleId),
                            ) else state
                        }
                        if (original == null) createNewTourPlaceholder()
                    }
            }
            if (homeLocations != null) {
                viewModelScope.launch {
                    homeLocations.observeAll().collect { list ->
                        if (list.none { it.hasLocation }) return@collect
                        _uiState.update { state ->
                            // Nur vorbelegen, solange der Nutzer den Schalter noch nicht selbst verändert hat.
                            if (state.homeSwitch != state.initialHomeSwitch || state.initialHomeSwitch) state
                            else state.copy(homeSwitch = true, initialHomeSwitch = true)
                        }
                    }
                }
            }
        } else {
            viewModelScope.launch {
                val loadedTour = repository.observeTour(tourId).first()
                if (loadedTour == null) {
                    persistMutex.withLock { formDraftStore.delete(TOUR_DRAFT_KIND, tourId) }
                    savedStateHandle.remove<SavedState>(DRAFT_KEY)
                    _uiState.update { it.copy(isLoading = false, notFound = true) }
                    return@launch
                }
                original = loadedTour
                openedTour = loadedTour
                openedInput = loadedTour.toInput(locale())
                openedManualCountriesAdded = loadedTour.manualCountriesAdded
                openedManualCountriesRemoved = loadedTour.manualCountriesRemoved
                val rawPayload = formDraftStore.get(TOUR_DRAFT_KIND, tourId)
                val stored = rawPayload?.let(::decodeTourDraft)
                val candidates = listOfNotNull(draft, stored)
                val restored = candidates.firstOrNull { it.ownerUuid == loadedTour.uuid }
                if (restored == null && (rawPayload != null || candidates.isNotEmpty())) {
                    persistMutex.withLock { formDraftStore.delete(TOUR_DRAFT_KIND, tourId) }
                    savedStateHandle.remove<SavedState>(DRAFT_KEY)
                }
                draft = restored
                if (restored == null) {
                    initialStartDate = loadedTour.startDate
                    showErrors = false
                    autoTravelDays = null
                    autoSlug = null
                    homeStationIds = emptyList()
                }
                if (restored != null) {
                    if (restored.isNew) initialStartDate = restored.initialStartDate ?: loadedTour.startDate
                    openedInput = restored.openedInput ?: loadedTour.toInput(locale())
                    openedManualCountriesAdded = restored.openedManualCountriesAdded ?: loadedTour.manualCountriesAdded
                    openedManualCountriesRemoved = restored.openedManualCountriesRemoved ?: loadedTour.manualCountriesRemoved
                    openedTour = openedInput!!.toTour(loadedTour, locale()).copy(
                        manualCountriesAdded = openedManualCountriesAdded,
                        manualCountriesRemoved = openedManualCountriesRemoved,
                    )
                    autoTravelDays = restored.autoTravelDays
                    autoSlug = restored.autoSlug
                    homeStationIds = restored.homeStationIds
                    showErrors = restored.showErrors
                }
                _uiState.update {
                    // Der dauerhafte Rohentwurf hat Vorrang; SavedStateHandle deckt den Prozessneustart ab.
                    val input = restored?.input ?: loadedTour.toInput(locale())
                    it.copy(
                        isLoading = false,
                        notFound = false,
                        input = input,
                        showLegacyMapLink = loadedTour.mapLink != null,
                        tourId = loadedTour.id,
                        isDirty = restored != null,
                        trackSwitch = restored?.trackSwitch ?: it.trackSwitch,
                        homeSwitch = restored?.homeSwitch ?: it.homeSwitch,
                        initialHomeSwitch = restored?.initialHomeSwitch ?: it.initialHomeSwitch,
                        isNew = restored?.isNew ?: false,
                        initialStartDate = if (restored?.isNew == true) initialStartDate else it.initialStartDate,
                        errors = if (restored == null) emptyMap() else it.errors,
                        costErrors = if (restored == null) emptyMap() else it.costErrors,
                    )
                }
                if (restored != null) {
                    _uiState.update { it.withErrors() }
                    saveDraft()
                    attemptPersist()
                }
            }
        }
    }

    /**
     * Übernimmt eine Formularänderung. Es gibt keinen Speichern-Versuch mehr, der Fehler sichtbar
     * machen könnte - stattdessen macht schon die erste Eingabe alle aktuellen Fehler sichtbar, und
     * löst außerdem den debounced Autosave aus.
     */
    fun onInputChange(transform: (TourInput) -> TourInput) {
        if (_uiState.value.isFinishing) return
        showErrors = true
        _uiState.update { state ->
            state.copy(input = transform(state.input), isDirty = true).withErrors()
        }
        saveDraft()
        scheduleDraftAutosave()
    }

    fun onCostAmountChange(index: Int, amount: String) = onCostChange(index) { it.copy(amount = amount) }

    fun onCostCurrencyChange(index: Int, currency: Currency) = onCostChange(index) { it.copy(currency = currency) }

    /** Hängt eine Kostenzeile mit der ersten noch freien Währung an, bevorzugt aus [QUICK_CURRENCIES]. */
    fun onAddCost() = onInputChange { input ->
        val used = input.costs.map(CostInput::currency).toSet()
        val next = (QUICK_CURRENCIES + ALL_CURRENCIES).firstOrNull { it !in used } ?: return@onInputChange input
        input.copy(costs = input.costs + CostInput(currency = next))
    }

    /** Entfernt eine Kostenzeile; die letzte bleibt stehen und wird nur geleert. */
    fun onRemoveCost(index: Int) = onInputChange { input ->
        val costs = input.costs.filterIndexed { i, _ -> i != index }
        input.copy(costs = costs.ifEmpty { listOf(CostInput(currency = input.costs[index].currency)) })
    }

    private fun onCostChange(index: Int, transform: (CostInput) -> CostInput) = onInputChange { input ->
        input.copy(costs = input.costs.mapIndexed { i, cost -> if (i == index) transform(cost) else cost })
    }

    fun onVehicleChange(vehicleId: Long) = onInputChange { it.copy(vehicleId = vehicleId) }

    fun onNameChange(name: String) = onInputChange { updateSlugSuggestion(it.copy(name = name)) }

    fun onDestinationChange(destination: String) = onInputChange { updateSlugSuggestion(it.copy(destination = destination)) }

    fun onSlugChange(slug: String) = onInputChange { it.copy(slug = slug) }

    /**
     * Schlägt den Slug aus [TourInput.name] bzw. [TourInput.destination] vor, solange der Nutzer ihn
     * nicht selbst geändert hat (leer oder noch gleich dem letzten Vorschlag).
     */
    private fun updateSlugSuggestion(input: TourInput): TourInput {
        if (input.slug.isNotBlank() && input.slug != autoSlug) return input
        val suggestion = suggestSlug(input.name.ifBlank { input.destination })
        autoSlug = suggestion
        return input.copy(slug = suggestion)
    }

    fun onStartDateChange(date: LocalDate) {
        onInputChange { prefillTravelDays(it.copy(startDate = date)) }
        refreshDerivedMetrics()
    }

    fun onEndDateChange(date: LocalDate?) {
        onInputChange { prefillTravelDays(it.copy(endDate = date)) }
        // Der Schalter ist nur für offene Touren sichtbar; ein gesetztes Enddatum blendet ihn aus
        // und beendet damit auch eine eventuell schon laufende Aufzeichnung (siehe EditTourScreen).
        if (date != null) _uiState.update { it.copy(trackSwitch = false) }
        if (date != null) refreshDerivedMetrics()
    }

    fun onStartTimeChange(time: LocalTime?) = onInputChange { it.copy(startTime = time) }

    fun onEndTimeChange(time: LocalTime?) = onInputChange { it.copy(endTime = time) }

    /** Setzt Startdatum UND -zeit auf den exakten aktuellen Moment (Schnellausfüll-Button "Jetzt"). */
    fun onStartNow() {
        val now = LocalDateTime.now()
        onInputChange { prefillTravelDays(it.copy(startDate = now.toLocalDate(), startTime = now.toLocalTime())) }
        refreshDerivedMetrics()
    }

    /** Setzt Enddatum UND -zeit auf den exakten aktuellen Moment (Schnellausfüll-Button "Jetzt"). */
    fun onEndNow() {
        val now = LocalDateTime.now()
        onInputChange { prefillTravelDays(it.copy(endDate = now.toLocalDate(), endTime = now.toLocalTime())) }
        _uiState.update { it.copy(trackSwitch = false) }
        refreshDerivedMetrics()
    }

    /**
     * Übernimmt den Schalter "GPS-Track aufzeichnen" im Formular einer neuen Tour; die Oberfläche
     * startet bzw. stoppt die Aufzeichnung selbst anhand der schon bekannten [EditUiState.tourId].
     */
    fun onTrackSwitchChange(value: Boolean) {
        if (_uiState.value.isFinishing || _uiState.value.isFinished) return
        _uiState.update { it.copy(trackSwitch = value) }
        saveDraft()
        scheduleDraftAutosave()
    }

    /**
     * Übernimmt den Schalter "Beginnt zu Hause" im Formular einer neuen Tour und legt bzw. entfernt
     * sofort die zugehörige(n) Zuhause-Station(en), da die Tour ab ihrer Anlage schon eine echte id hat.
     * Holt davor einen eventuell noch ausstehenden Autosave sofort nach, damit die Station(en) mit
     * dem gerade eingegebenen Zeitraum statt einem noch nicht geschriebenen, veralteten Stand entstehen.
     */
    fun onHomeSwitchChange(value: Boolean) {
        if (_uiState.value.isFinishing || _uiState.value.isFinished) return
        _uiState.update { it.copy(homeSwitch = value) }
        saveDraft()
        scheduleDraftAutosave()
        pendingDraftJob?.cancel()
        viewModelScope.launch {
            attemptPersist()
            if (!value) persistMutex.withLock {
                if (!_uiState.value.isFinished && !discardConfirmed && !discardApplied) removeTrackedHomeStations()
            }
        }
    }

    /** Speichert eine neue Tour früh als stabile ID für Tracking; Formulareingaben bleiben Entwurf. */
    private suspend fun createNewTourPlaceholder() {
        persistMutex.withLock {
            if (original != null) return@withLock
            val state = _uiState.value
            val input = (state.initialInput ?: TourInput(startDate = initialStartDate))
                .copy(startDate = initialStartDate)
            val tour = input.toTour(null, locale())
            try {
                val id = repository.save(tour)
                original = repository.observeTour(id).first() ?: tour.copy(id = id)
                openedTour = original
                openedInput = input
                openedManualCountriesAdded = emptySet()
                openedManualCountriesRemoved = emptySet()
                _uiState.update { it.copy(tourId = id, saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            } catch (_: RunningTourAlreadyExistsException) {
                _uiState.update { it.copy(runningTourConflict = true) }
            }
        }
    }

    /** Speichert den Rohentwurf dauerhaft und übernimmt nur vollständig gültige Eingaben in die Tour. */
    private suspend fun attemptPersist() {
        if (_uiState.value.isFinished || discardConfirmed || discardApplied) return
        var id = _uiState.value.tourId
        if (id == null && _uiState.value.isNew) {
            createNewTourPlaceholder()
            id = _uiState.value.tourId
        }
        id ?: return
        persistMutex.withLock {
            if (_uiState.value.isFinished || discardConfirmed || discardApplied) return@withLock
            try {
                formDraftStore.put(TOUR_DRAFT_KIND, id, encodeTourDraft(currentDraft()))
                rawDraftSaveSucceeded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                rawDraftSaveSucceeded = false
                _uiState.update { it.copy(saveFailed = true) }
                return@withLock
            }
            val state = _uiState.value
            val loc = locale()
            if (state.input.validation(loc).errors.isNotEmpty()) return@withLock
            val basis = original
            val vehicleId = state.input.vehicleId.takeIf { it != 0L } ?: basis?.vehicleId ?: 0L
            val tour = state.input.copy(vehicleId = vehicleId).toTour(basis, loc)
            try {
                val savedId = repository.save(tour)
                if (basis != null && basis.vehicleId != tour.vehicleId) {
                    stations.moveTourToVehicle(savedId, tour.vehicleId)
                    checklists.moveTourToVehicle(savedId, tour.vehicleId)
                }
                original = repository.observeTour(savedId).first() ?: tour.copy(id = savedId)
                _uiState.update { it.copy(tourId = savedId, saveFailed = false, runningTourConflict = false) }
                if (state.homeSwitch && homeStationIds.isEmpty()) createHomeStationsNow()
                if (!state.homeSwitch) removeTrackedHomeStations()
            } catch (_: RunningTourAlreadyExistsException) {
                _uiState.update { it.copy(runningTourConflict = true) }
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    private fun scheduleDraftAutosave() {
        pendingDraftJob?.cancel()
        pendingDraftJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            attemptPersist()
        }
    }

    /** Validiert und übernimmt den vollständigen Datensatz; bei Fehlern bleibt Rohentwurf und Tour unangetastet. */
    suspend fun onFinish(): Boolean {
        if (_uiState.value.isFinishing || _uiState.value.isFinished) return false
        _uiState.update { it.copy(isFinishing = true, validationAttempt = it.validationAttempt + 1) }
        showErrors = true
        try {
            if (!flushDraft()) {
                _uiState.update { it.copy(isFinishing = false) }
                return false
            }
        } catch (cancelled: CancellationException) {
            _uiState.update { it.copy(isFinishing = false) }
            throw cancelled
        }
        val state = _uiState.value.withErrors()
        _uiState.value = state
        if (state.errors.isNotEmpty() || state.costErrors.isNotEmpty()) {
            _uiState.update { it.copy(isFinishing = false) }
            return false
        }
        val basis = original
        val startDate = state.input.startDate ?: run {
            _uiState.update { it.copy(isFinishing = false) }
            return false
        }
        val vehicleId = state.input.vehicleId.takeIf { it != 0L } ?: basis?.vehicleId ?: 0L
        val effectiveInput = state.input.copy(startDate = startDate, vehicleId = vehicleId)
        val tour = effectiveInput.toTour(basis, locale())
        return persistMutex.withLock {
          try {
            val id = repository.save(tour)
            if (basis != null && basis.vehicleId != tour.vehicleId) {
                stations.moveTourToVehicle(id, tour.vehicleId)
                checklists.moveTourToVehicle(id, tour.vehicleId)
            }
            original = repository.observeTour(id).first() ?: tour.copy(id = id)
            if (_uiState.value.homeSwitch) {
                if (homeStationIds.isEmpty()) createHomeStationsNow()
            } else {
                removeTrackedHomeStations()
            }
            formDraftStore.delete(TOUR_DRAFT_KIND, id)
            savedStateHandle.remove<SavedState>(DRAFT_KEY)
            _uiState.update { it.copy(tourId = id, isFinishing = false, isFinished = true, isDirty = false, saveFailed = false, runningTourConflict = false) }
            true
          } catch (cancelled: CancellationException) {
            _uiState.update { it.copy(isFinishing = false) }
            throw cancelled
          } catch (_: RunningTourAlreadyExistsException) {
            _uiState.update { it.copy(isFinishing = false, runningTourConflict = true) }
            false
          } catch (_: Exception) {
            _uiState.update { it.copy(isFinishing = false, saveFailed = true) }
            false
          }
        }
    }

    /** Wartet den debounce ab und schreibt den vollständigen Arbeitsstand vor dem Verlassen. */
    suspend fun flushDraft(): Boolean {
        pendingDraftJob?.cancelAndJoin()
        pendingDraftJob = null
        attemptPersist()
        return rawDraftSaveSucceeded
    }

    /** Schreibt den Rohentwurf vor dem Verlassen genau einmal und sperrt weitere Eingaben. */
    suspend fun keepDraft(): Boolean {
        if (_uiState.value.isFinishing || _uiState.value.isFinished) return false
        _uiState.update { it.copy(isFinishing = true) }
        return try {
            if (flushDraft()) {
                _uiState.update { it.copy(isFinishing = false, isFinished = true) }
                true
            } else {
                _uiState.update { it.copy(isFinishing = false) }
                false
            }
        } catch (cancelled: CancellationException) {
            _uiState.update { it.copy(isFinishing = false) }
            throw cancelled
        } catch (_: Exception) {
            _uiState.update { it.copy(isFinishing = false, saveFailed = true) }
            false
        }
    }

    private fun currentDraft(): TourDraft {
        val state = _uiState.value
        return TourDraft(
            version = TOUR_DRAFT_VERSION,
            ownerUuid = original?.uuid,
            input = state.input,
            showErrors = showErrors,
            autoTravelDays = autoTravelDays,
            autoSlug = autoSlug,
            persistedTourId = state.tourId,
            homeStationIds = homeStationIds,
            initialStartDate = initialStartDate,
            initialInput = state.initialInput,
            openedInput = openedInput,
            openedManualCountriesAdded = openedManualCountriesAdded,
            openedManualCountriesRemoved = openedManualCountriesRemoved,
            trackSwitch = state.trackSwitch,
            homeSwitch = state.homeSwitch,
            initialHomeSwitch = state.initialHomeSwitch,
            isNew = state.isNew,
        )
    }

    /**
     * Legt beim Einschalten des Schalters "Beginnt zu Hause" sofort eine Übernachtungs-Station am
     * Zuhause-Ort zum aktuellen Startdatum der (schon angelegten) Tour an; ist ein Enddatum gesetzt,
     * zusätzlich eine zweite dazu, mit der zu diesem Datum gültigen Adresse (fällt keine Adresse auf
     * das Enddatum, wird die Startadresse übernommen). `nights` bleibt für beide ungesetzt, damit sie
     * die Übernachtungsstatistik nicht verzerren.
     */
    private suspend fun createHomeStationsNow() {
        val basis = original ?: return
        val repo = homeLocations ?: return
        fun homeStation(date: LocalDate, home: HomeLocation): Station? {
            val latitude = home.latitude ?: return null
            val longitude = home.longitude ?: return null
            return Station(
                vehicleId = basis.vehicleId,
                tourId = basis.id,
                type = StationType.OVERNIGHT,
                date = date,
                name = home.name.ifBlank { defaultHomeStationName() },
                latitude = latitude,
                longitude = longitude,
                coordinateSource = CoordinateSource.ENTERED,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }
        val startHome = repo.locationAt(basis.startDate) ?: return
        val ids = mutableListOf<Long>()
        homeStation(basis.startDate, startHome)?.let { ids += stations.save(it) }
        basis.endDate?.let { end ->
            val endHome = repo.locationAt(end) ?: startHome
            homeStation(end, endHome)?.let { ids += stations.save(it) }
        }
        homeStationIds = ids
        saveDraft()
    }

    /**
     * Entfernt beim Ausschalten des Schalters "Beginnt zu Hause" die zuletzt dafür angelegten
     * Zuhause-Station(en) wieder, aber nur jene, die seitdem unverändert sind - hat der Nutzer eine
     * davon selbst bearbeitet, bleibt sie als eigenständige Station stehen. Die erwartete Adresse
     * wird je Station erneut anhand ihres eigenen Datums nachgeschlagen, nicht anhand eines einzigen
     * festen Standes.
     */
    private suspend fun removeTrackedHomeStations() {
        if (homeStationIds.isEmpty()) return
        val repo = homeLocations
        for (id in homeStationIds) {
            val current = stations.observeStation(id).first() ?: continue
            val home = repo?.locationAt(current.date)
            val expectedName = home?.name?.ifBlank { defaultHomeStationName() }
            val untouched = home != null &&
                current.type == StationType.OVERNIGHT &&
                current.name == expectedName &&
                current.latitude == home.latitude &&
                current.longitude == home.longitude &&
                current.coordinateSource == CoordinateSource.ENTERED &&
                current.nights == null
            if (untouched) stations.delete(id)
        }
        homeStationIds = emptyList()
        saveDraft()
    }

    /** Die Fehlermeldung zu [EditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    /** Für eine bestehende Tour wurde die Konflikt-Meldung angezeigt; für eine neue Tour zeigt die Oberfläche sie dauerhaft inline an. */
    fun onRunningTourConflictShown() {
        _uiState.update { it.copy(runningTourConflict = false) }
    }

    private fun EditUiState.withErrors(): EditUiState {
        if (!showErrors) return copy(errors = emptyMap(), costErrors = emptyMap())
        val validation = input.validation(locale())
        return copy(errors = validation.errors, costErrors = validation.costErrors)
    }

    private fun saveDraft() {
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(currentDraft())
    }

    /** Wendet das Verwerfen vollständig an, bevor die Oberfläche die Route verlässt. */
    suspend fun discardDraft(): Boolean {
        if (_uiState.value.isFinishing || _uiState.value.isFinished) return false
        _uiState.update { it.copy(isFinishing = true) }
        discardConfirmed = true
        pendingDraftJob?.cancelAndJoin()
        return try {
            persistMutex.withLock {
                if (uiState.value.isNew) deleteNewTourDraft() else revertToOpenedTour()
                discardApplied = true
                true
            }
        } catch (cancelled: CancellationException) {
            discardConfirmed = false
            _uiState.update { it.copy(isFinishing = false) }
            throw cancelled
        } catch (_: Exception) {
            discardConfirmed = false
            _uiState.update { it.copy(isFinishing = false) }
            _uiState.update { it.copy(saveFailed = true) }
            false
        }
    }

    /** Ob [discardDraft] in dieser Sitzung erfolgreich abgeschlossen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /** Bricht den debounce-Job ab; Datenbankarbeit geschieht vor dem regulären Verlassen asynchron. */
    override fun onCleared() {
        pendingDraftJob?.cancel()
    }

    /** Löscht die automatisch angelegte, noch nicht sinnvoll befüllte bzw. explizit verworfene neue Tour wieder. */
    private suspend fun deleteNewTourDraft() {
        original?.id?.let { id ->
            removeTrackedHomeStations()
            repository.delete(id)
            formDraftStore.delete(TOUR_DRAFT_KIND, id)
        }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /**
     * Setzt eine bestehende Tour auf [openedTour] zurück. Zieht dabei ihre Stationen und Checklisten
     * immer auf das ursprüngliche Fahrzeug, unabhängig davon, ob und wie oft es während der Sitzung
     * gewechselt wurde - [moveTourToVehicle][StationRepository.moveTourToVehicle] ist für ein schon
     * passendes Fahrzeug ein No-op.
     */
    private suspend fun revertToOpenedTour() {
        val baseline = openedTour
        val id = original?.id
        if (baseline != null) {
            repository.save(baseline)
            stations.moveTourToVehicle(baseline.id, baseline.vehicleId)
            checklists.moveTourToVehicle(baseline.id, baseline.vehicleId)
        }
        if (id != null) formDraftStore.delete(TOUR_DRAFT_KIND, id)
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Aktualisiert nach einer Datumswahl alle automatisch ableitbaren Kennzahlen. */
    private fun refreshDerivedMetrics() {
        viewModelScope.launch {
            val snapshot = _uiState.value.input
            val start = snapshot.startDate ?: return@launch
            val end = snapshot.endDate ?: return@launch
            if (end < start) return@launch
            val stationList = stations.observeForTour(original?.id ?: 0L).first()
            val trackPoints = tracks?.observeForTour(original?.id ?: 0L)?.first().orEmpty()
            val basis = original?.copy(startDate = start, endDate = end) ?: Tour(
                vehicleId = snapshot.vehicleId,
                startDate = start,
                endDate = end,
                destination = snapshot.destination,
                tourType = snapshot.tourType,
                travelDays = 0,
                overnightStays = 0,
                distanceKm = 0,
                costs = emptyList(),
                notes = "",
                mapLink = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val metrics = basis.derivedMetrics(stationList, trackPoints, end)
            if (_uiState.value.input.startDate != start || _uiState.value.input.endDate != end) return@launch
            autoTravelDays = metrics.travelDays.toString()
            onInputChange {
                it.copy(
                    travelDays = metrics.travelDays.toString(),
                    overnightStays = metrics.overnightStays.toString(),
                    distanceKm = metrics.distanceKm.toString(),
                )
            }
        }
    }

    /**
     * Füllt die Reisetage aus dem Zeitraum vor, solange der Nutzer sie nicht selbst geändert hat.
     */
    private fun prefillTravelDays(input: TourInput): TourInput {
        val start = input.startDate ?: return input
        val end = input.endDate ?: return input
        if (end < start) return input
        val untouched = input.travelDays.isBlank() || input.travelDays == autoTravelDays
        if (!untouched) return input
        val days = travelDaysBetween(start, end).toString()
        autoTravelDays = days
        return input.copy(travelDays = days)
    }
}

private const val DRAFT_KEY = "draft"
private const val TOUR_DRAFT_KIND = "TOUR"
private const val TOUR_DRAFT_VERSION = 1

private val tourDraftJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun encodeTourDraft(draft: TourDraft): String = tourDraftJson.encodeToString(TourDraft.serializer(), draft)

private fun decodeTourDraft(payload: String): TourDraft? =
    runCatching { tourDraftJson.decodeFromString(TourDraft.serializer(), payload) }
        .getOrNull()
        ?.takeIf { it.version == TOUR_DRAFT_VERSION }

/** Verzögerung zwischen einer Formularänderung einer neuen Tour und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
