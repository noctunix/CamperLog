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
import app.restvolt.camperlog.ui.settings.HomeLocationSettings
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

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
    /** Die id der Tour, sobald sie bekannt ist: für eine neue Tour ab ihrer ersten Anlage, sonst ab dem Laden. */
    val tourId: Long? = null,
)

/**
 * Ob der Entwurf einer neuen Tour noch unverändert den Vorbelegungen entspricht - also beim
 * Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun EditUiState.isUnmodifiedNewTourDraft(): Boolean = isNew &&
    input.startDate == null &&
    input.endDate == null &&
    input.destination.isBlank() &&
    input.name.isBlank() &&
    input.notes.isBlank() &&
    input.mapLink.isBlank() &&
    input.costs.all { it.amount.isBlank() } &&
    !trackSwitch &&
    !homeSwitch

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für eine neue Tour, solange
 * ihr Entwurf nicht mehr [isUnmodifiedNewTourDraft] ist (ein komplett unberührter Entwurf verlässt
 * sich ohne Rückfrage, siehe [EditTourViewModel.onCleared]); für eine bestehende Tour, solange sich
 * seit dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun EditUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewTourDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class TourDraft(
    val input: TourInput,
    val showErrors: Boolean,
    val autoTravelDays: String?,
    val autoSlug: String? = null,
    /** Für eine neue Tour die id der schon automatisch angelegten Tour, falls es sie schon gibt. */
    val persistedTourId: Long? = null,
    /** Für eine neue Tour die ids der zuletzt für den Schalter "Beginnt zu Hause" angelegten Stationen. */
    val homeStationIds: List<Long> = emptyList(),
)

/**
 * Lädt, validiert und speichert eine Tour. [tourId] 0 legt eine neue Tour an.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für eine neue wie für eine bestehende Tour. Eine neue Tour wird dafür sofort
 * als echter Datensatz angelegt; dadurch hat sie von Anfang an eine stabile id, gegen die z. B.
 * eine GPS-Aufzeichnung sofort starten kann, ohne auf einen expliziten Speichern-Erfolg zu warten.
 * Verlässt der Nutzer das Formular wieder, entscheidet [onCleared] je nach Fall: Ein komplett
 * unberührter Entwurf einer neuen Tour wird gelöscht; eine echte Änderung kann der Nutzer vorher
 * über [onDiscardConfirmed] verwerfen - für eine neue Tour heißt das Löschen, für eine bestehende
 * das Zurücksetzen auf den beim Öffnen geladenen Stand (siehe [openedTour]).
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
 * @param homeLocationSettings liefert die Zuhause-Koordinate für den Schalter "Beginnt zu Hause"; `null` blendet ihn aus
 * @param defaultHomeStationName liefert den Anzeigenamen der Zuhause-Station, falls [homeLocationSettings] keinen eigenen Namen trägt
 */
class EditTourViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val checklists: ChecklistRepository,
    tourId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val locale: () -> Locale = { app.restvolt.camperlog.domain.supportedLocale(Locale.getDefault()) },
    private val tracks: TrackRepository? = null,
    private val homeLocationSettings: HomeLocationSettings? = null,
    private val defaultHomeStationName: () -> String = { "" },
) : ViewModel() {

    private val draft: TourDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        EditUiState(isNew = tourId == 0L, isLoading = tourId != 0L).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: Tour? = null

    /**
     * Für eine bestehende Tour der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für eine neue Tour `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedTour: Tour? = null
    private var showErrors = draft?.showErrors ?: false
    private var autoTravelDays: String? = draft?.autoTravelDays
    private var autoSlug: String? = draft?.autoSlug
    private var homeStationIds: List<Long> = draft?.homeStationIds ?: emptyList()

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide eine neue Tour anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

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
                    if (it.isDirty) it else it.copy(input = it.input.copy(costs = listOf(CostInput(currency = currency))))
                }
            }
            viewModelScope.launch {
                val restoredId = draft?.persistedTourId
                if (restoredId != null) {
                    original = repository.observeTour(restoredId).first()
                    if (original != null) _uiState.update { it.copy(tourId = restoredId) }
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
                            if (preselectedVehicleId != null) state.copy(input = state.input.copy(vehicleId = preselectedVehicleId)) else state
                        }
                        if (original == null) attemptPersist()
                    }
            }
        } else {
            viewModelScope.launch {
                val tour = repository.observeTour(tourId).first()
                original = tour
                openedTour = tour
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else tour?.toInput(locale()) ?: it.input
                    it.copy(
                        isLoading = false,
                        notFound = tour == null,
                        input = input,
                        showLegacyMapLink = tour?.mapLink != null,
                        tourId = tour?.id,
                    )
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
        showErrors = true
        _uiState.update { state ->
            state.copy(input = transform(state.input), isDirty = true).withErrors()
        }
        saveDraft()
        scheduleAutosave()
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

    /**
     * Übernimmt den Schalter "GPS-Track aufzeichnen" im Formular einer neuen Tour; die Oberfläche
     * startet bzw. stoppt die Aufzeichnung selbst anhand der schon bekannten [EditUiState.tourId].
     */
    fun onTrackSwitchChange(value: Boolean) {
        _uiState.update { it.copy(trackSwitch = value) }
    }

    /**
     * Übernimmt den Schalter "Beginnt zu Hause" im Formular einer neuen Tour und legt bzw. entfernt
     * sofort die zugehörige(n) Zuhause-Station(en), da die Tour ab ihrer Anlage schon eine echte id hat.
     * Holt davor einen eventuell noch ausstehenden Autosave sofort nach, damit die Station(en) mit
     * dem gerade eingegebenen Zeitraum statt einem noch nicht geschriebenen, veralteten Stand entstehen.
     */
    fun onHomeSwitchChange(value: Boolean) {
        _uiState.update { it.copy(homeSwitch = value) }
        pendingSaveJob?.cancel()
        viewModelScope.launch {
            attemptPersist()
            if (value) createHomeStationsNow() else removeTrackedHomeStations()
        }
    }

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für eine neue Tour), sonst aktualisieren. Fehlt [TourInput.startDate] noch (der
     * Nutzer hat es noch nicht gesetzt, nur für eine neue Tour möglich), wird dafür vorübergehend das
     * Startdatum des zuletzt gespeicherten Standes bzw. das heutige Datum verwendet, ohne die Anzeige
     * im Formular zu verändern - das Pflichtfeld bleibt dort leer, bis der Nutzer selbst ein Datum
     * wählt. Ist die Eingabe (mit diesem Platzhalter) ungültig, bleibt der zuletzt gespeicherte Stand
     * unverändert stehen, bis sie es wieder ist. Scheitert der Versuch an
     * [RunningTourAlreadyExistsException], bleibt die Tour unangelegt bzw. unverändert und der
     * Konflikt wird nur angezeigt, nicht wiederholt erzwungen.
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val basis = original
            val startDate = state.input.startDate ?: basis?.startDate ?: LocalDate.now()
            // 0 bedeutet "aktuelles Fahrzeug" und wird nur beim allerersten Anlegen aufgelöst (siehe
            // Tour.save); ein späteres Aktualisieren müsste sonst den schon aufgelösten Stand wieder
            // auf 0 zurücksetzen, solange der Nutzer nie selbst ein Fahrzeug auswählt.
            val vehicleId = state.input.vehicleId.takeIf { it != 0L } ?: basis?.vehicleId ?: 0L
            val loc = locale()
            val effectiveInput = state.input.copy(startDate = startDate, vehicleId = vehicleId).sanitizedForAutosave(loc)
            // Nur ein unlogischer Zeitraum (Ende vor Beginn) bleibt blockierend: anders als bei einer
            // einzelnen ungültigen Zahl oder Kostenzeile lässt sich hier nicht eindeutig sanieren, ohne
            // zu raten, welches der beiden Felder "falsch" ist.
            if (effectiveInput.validation(loc).errors.isNotEmpty()) return@withLock
            val tour = effectiveInput.toTour(basis, loc)
            try {
                val id = repository.save(tour)
                val previousVehicleId = basis?.vehicleId
                if (previousVehicleId != null && previousVehicleId != tour.vehicleId) {
                    stations.moveTourToVehicle(id, tour.vehicleId)
                    checklists.moveTourToVehicle(id, tour.vehicleId)
                }
                original = repository.observeTour(id).first() ?: tour.copy(id = id)
                _uiState.update { it.copy(tourId = id, saveFailed = false, runningTourConflict = false) }
                saveDraft()
            } catch (_: RunningTourAlreadyExistsException) {
                _uiState.update { it.copy(runningTourConflict = true) }
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /**
     * Entschärft eine Eingabe für [attemptPersist]: Eine ungültige Kennzahl, ein ungültiger
     * Kartenlink oder eine einzelne ungültige Kostenzeile fallen weg, statt den Schreibzugriff für
     * die ganze Tour zu blockieren - der Rest der Eingabe (z. B. Ziel oder Datum) speichert sich
     * weiter, während das betroffene Feld inline seinen Fehler zeigt. Das verändert nur den
     * gespeicherten Stand, nicht die Anzeige im Formular.
     */
    private fun TourInput.sanitizedForAutosave(locale: Locale): TourInput {
        val validation = validation(locale)
        var sanitized = this
        if (TourField.TRAVEL_DAYS in validation.errors) sanitized = sanitized.copy(travelDays = "")
        if (TourField.OVERNIGHT_STAYS in validation.errors) sanitized = sanitized.copy(overnightStays = "")
        if (TourField.DISTANCE_KM in validation.errors) sanitized = sanitized.copy(distanceKm = "")
        if (TourField.MAP_LINK in validation.errors) sanitized = sanitized.copy(mapLink = "")
        if (validation.costErrors.isNotEmpty()) {
            val cleanedCosts = sanitized.costs.filterIndexed { index, _ -> index !in validation.costErrors.keys }
            sanitized = sanitized.copy(costs = cleanedCosts.ifEmpty { listOf(CostInput()) })
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

    /**
     * Legt beim Einschalten des Schalters "Beginnt zu Hause" sofort eine Übernachtungs-Station am
     * Zuhause-Ort zum aktuellen Startdatum der (schon angelegten) Tour an; ist ein Enddatum gesetzt,
     * zusätzlich eine zweite dazu. `nights` bleibt für beide ungesetzt, damit sie die
     * Übernachtungsstatistik nicht verzerren.
     */
    private suspend fun createHomeStationsNow() {
        val basis = original ?: return
        val home = homeLocationSettings?.values?.value ?: return
        val latitude = home.latitude ?: return
        val longitude = home.longitude ?: return
        val name = home.name.ifBlank { defaultHomeStationName() }
        fun homeStation(date: LocalDate) = Station(
            vehicleId = basis.vehicleId,
            tourId = basis.id,
            type = StationType.OVERNIGHT,
            date = date,
            name = name,
            latitude = latitude,
            longitude = longitude,
            coordinateSource = CoordinateSource.ENTERED,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val ids = mutableListOf(stations.save(homeStation(basis.startDate)))
        basis.endDate?.let { ids += stations.save(homeStation(it)) }
        homeStationIds = ids
        saveDraft()
    }

    /**
     * Entfernt beim Ausschalten des Schalters "Beginnt zu Hause" die zuletzt dafür angelegten
     * Zuhause-Station(en) wieder, aber nur jene, die seitdem unverändert sind - hat der Nutzer eine
     * davon selbst bearbeitet, bleibt sie als eigenständige Station stehen.
     */
    private suspend fun removeTrackedHomeStations() {
        if (homeStationIds.isEmpty()) return
        val home = homeLocationSettings?.values?.value
        val expectedName = home?.name?.ifBlank { defaultHomeStationName() }
        for (id in homeStationIds) {
            val current = stations.observeStation(id).first() ?: continue
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
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(
            TourDraft(input, showErrors, autoTravelDays, autoSlug, original?.id, homeStationIds),
        )
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für eine neue Tour heißt das, den automatisch angelegten
     * Datensatz zu löschen; für eine bestehende, sie auf den beim Öffnen des Formulars geladenen Stand
     * zurückzusetzen (siehe [openedTour]). Bricht außerdem einen noch wartenden Autosave ab, damit er
     * nicht doch noch zwischen diesem Aufruf und dem tatsächlichen Verlassen feuert.
     */
    fun onDiscardConfirmed() {
        discardConfirmed = true
        pendingSaveJob?.cancel()
    }

    /** Ob [onDiscardConfirmed] für diese Sitzung schon aufgerufen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /**
     * Beim Verlassen ohne sinnvolle Eingabe (eine neue, komplett unberührte Tour) oder nach
     * [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn, wie beim
     * analogen Aufräumen in [EditStationViewModel.onCleared].
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && (discardConfirmed || state.isUnmodifiedNewTourDraft()) -> deleteNewTourDraft()
                !state.isNew && discardConfirmed -> revertToOpenedTour()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /** Löscht die automatisch angelegte, noch nicht sinnvoll befüllte bzw. explizit verworfene neue Tour wieder. */
    private suspend fun deleteNewTourDraft() {
        original?.id?.let { id -> runCatching { repository.delete(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /**
     * Setzt eine bestehende Tour auf [openedTour] zurück. Zieht dabei ihre Stationen und Checklisten
     * immer auf das ursprüngliche Fahrzeug, unabhängig davon, ob und wie oft es während der Sitzung
     * gewechselt wurde - [moveTourToVehicle][StationRepository.moveTourToVehicle] ist für ein schon
     * passendes Fahrzeug ein No-op.
     */
    private suspend fun revertToOpenedTour() {
        val baseline = openedTour ?: return
        persistMutex.withLock {
            runCatching { repository.save(baseline) }
            runCatching { stations.moveTourToVehicle(baseline.id, baseline.vehicleId) }
            runCatching { checklists.moveTourToVehicle(baseline.id, baseline.vehicleId) }
        }
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

/** Verzögerung zwischen einer Formularänderung einer neuen Tour und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
