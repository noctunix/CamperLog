package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LocalDateSerializer
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationPermissionGate
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.RepairError
import app.restvolt.camperlog.domain.RepairField
import app.restvolt.camperlog.domain.RepairInput
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toRepair
import app.restvolt.camperlog.domain.validate
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
import java.util.Locale

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [RepairEditViewModel]). */
data class RepairEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: RepairInput = RepairInput(),
    val errors: Map<RepairField, RepairError> = emptyMap(),
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Die geladene, gespeicherte Reparatur; nötig, um sie über die Kopfzeile löschen zu können. */
    val original: Repair? = null,
    /**
     * Die id dieser Reparatur, sobald bekannt: für eine neue Reparatur ab ihrer ersten automatischen
     * Anlage, sonst ab dem Laden; `0` bis dahin, siehe [app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection].
     */
    val repairId: Long = 0,
    /** Vorbelegtes Datum beim Öffnen einer neuen Reparatur; Basis für [isUnmodifiedNewRepairDraft]. */
    val initialDate: LocalDate? = null,
)

/**
 * Ob der Entwurf einer neuen Reparatur noch unverändert der Vorbelegung entspricht - also beim
 * Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun RepairEditUiState.isUnmodifiedNewRepairDraft(): Boolean = isNew &&
    input.date == initialDate &&
    input.time == null &&
    input.description.isBlank() &&
    input.workshopName.isBlank() &&
    input.paymentMethod.isBlank() &&
    input.odometerKm.isBlank() &&
    input.cost.isBlank() &&
    input.latitude == null &&
    input.longitude == null

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für eine neue Reparatur,
 * solange ihr Entwurf nicht mehr [isUnmodifiedNewRepairDraft] ist; für eine bestehende, solange sich
 * seit dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun RepairEditUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewRepairDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class RepairDraft(
    val input: RepairInput,
    val showErrors: Boolean,
    /** Für eine neue Reparatur die id der schon automatisch angelegten Reparatur, falls es sie schon gibt. */
    val persistedRepairId: Long? = null,
    @Serializable(with = LocalDateSerializer::class) val initialDate: LocalDate? = null,
)

/**
 * Lädt, validiert und speichert eine Reparatur eines Fahrzeugs. [repairId] 0 legt eine neue
 * Reparatur an.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für eine neue wie für eine bestehende Reparatur. Eine neue Reparatur wird dafür
 * sofort als echter Datensatz angelegt, auch mit noch leerer Beschreibung - dadurch können Fotos von
 * Anfang an angehängt werden, ohne auf einen expliziten Speichern-Erfolg zu warten. Verlässt der
 * Nutzer das Formular wieder, entscheidet [onCleared] je nach Fall: Ein komplett unberührter Entwurf
 * einer neuen Reparatur wird gelöscht; eine echte Änderung kann der Nutzer vorher über
 * [onDiscardConfirmed] verwerfen - für eine neue Reparatur heißt das Löschen (mit ihren Anhängen), für
 * eine bestehende das Zurücksetzen auf den beim Öffnen geladenen Stand (siehe [openedRepair]).
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt der gespeicherten Reparatur angezeigt; für eine neue Reparatur sichert das auch die
 * id der schon automatisch angelegten Reparatur gegen einen solchen Neustart ab.
 *
 * @param locale liefert die aktuelle Sprache für Zahlen und Beträge; wird bei jedem Zugriff neu
 *   gelesen, damit ein Sprachwechsel bei laufendem ViewModel greift
 * @param today Bezugstag für die Vorbelegung des Datums einer neuen Reparatur
 */
class RepairEditViewModel(
    private val repository: VehicleRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val vehicleId: Long,
    repairId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val locale: () -> Locale = { app.restvolt.camperlog.domain.supportedLocale(Locale.getDefault()) },
    private val today: () -> LocalDate = LocalDate::now,
    locationProvider: LocationProvider = RepairNoOpLocationProvider,
    locationPermissionGate: LocationPermissionGate = RepairNoOpLocationPermissionGate,
) : ViewModel() {

    /** Standortbestimmung für den Werkstatt-Abschnitt, nur sichtbar, wenn die Oberfläche den Standort-Schalter an sieht. */
    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)

    private val draft: RepairDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    /** Vorbelegtes Datum einer neuen Reparatur; wiederhergestellt aus [draft], falls der Prozess inzwischen neu gestartet ist. */
    private val initialDate: LocalDate? = when {
        draft != null -> draft.initialDate
        repairId == 0L -> today()
        else -> null
    }

    private val _uiState = MutableStateFlow(
        RepairEditUiState(isNew = repairId == 0L, isLoading = repairId != 0L, repairId = repairId, initialDate = initialDate).let { state ->
            when {
                draft != null -> state.copy(input = draft.input, isDirty = true)
                repairId == 0L -> state.copy(input = RepairInput(date = initialDate))
                else -> state
            }
        },
    )
    val uiState: StateFlow<RepairEditUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: Repair? = null

    /**
     * Für eine bestehende Reparatur der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für eine neue Reparatur `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedRepair: Repair? = null
    private var showErrors = draft?.showErrors ?: false

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide eine neue Reparatur anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            locationCapture.state.collect { captureState ->
                if (captureState is LocationCaptureState.Found) {
                    onInputChange { input -> input.copy(latitude = captureState.fix.latitude, longitude = captureState.fix.longitude) }
                    locationCapture.clear()
                }
            }
        }
        if (repairId != 0L) {
            viewModelScope.launch {
                val repair = repository.observeRepairs(vehicleId).first().firstOrNull { it.id == repairId }
                original = repair
                openedRepair = repair
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else repair?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = repair == null, input = input, original = repair, repairId = repair?.id ?: repairId)
                }
            }
        } else {
            viewModelScope.launch {
                val restoredId = draft?.persistedRepairId
                if (restoredId != null) {
                    original = repository.observeRepairs(vehicleId).first().firstOrNull { it.id == restoredId }
                    if (original != null) _uiState.update { it.copy(repairId = restoredId, original = original) }
                }
                if (original == null) {
                    val currency = repository.lastUsedRepairCurrency() ?: exchangeRates.observeMainCurrency().first()
                    // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                    _uiState.update { if (it.isDirty) it else it.copy(input = it.input.copy(costCurrency = currency)) }
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
    fun onInputChange(transform: (RepairInput) -> RepairInput) {
        showErrors = true
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
        scheduleAutosave()
    }

    /** Entfernt eine per GPS gesetzte Koordinate wieder. */
    fun onRemoveLocation() = onInputChange { it.copy(latitude = null, longitude = null) }

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für eine neue Reparatur), sonst aktualisieren. Eine noch leere Beschreibung
     * blockiert das nicht - sie bleibt als inline angezeigter Pflichtfeld-Fehler stehen, verhindert
     * aber nicht, dass die übrige Eingabe (und damit auch die id für Anhänge) sofort verfügbar ist.
     * Eine ungültige Kennzahl oder ein ungültiger Betrag wird stattdessen entschärft, siehe
     * [sanitizedForAutosave].
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val basis = original
            val date = state.input.date ?: basis?.date ?: today()
            val loc = locale()
            val effectiveInput = state.input.copy(date = date).sanitizedForAutosave(loc)
            val repair = effectiveInput.toRepair(basis, vehicleId, loc)
            try {
                val id = repository.saveRepair(repair)
                original = repository.observeRepairs(vehicleId).first().firstOrNull { it.id == id } ?: repair.copy(id = id)
                _uiState.update { it.copy(repairId = id, original = original, saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /**
     * Entschärft eine Eingabe für [attemptPersist]: Eine ungültige Kennzahl oder ein ungültiger Betrag
     * fällt weg, statt den Schreibzugriff für die ganze Reparatur zu blockieren - der Rest der Eingabe
     * (z. B. Beschreibung oder Werkstatt) speichert sich weiter, während das betroffene Feld inline
     * seinen Fehler zeigt. Das verändert nur den gespeicherten Stand, nicht die Anzeige im Formular.
     */
    private fun RepairInput.sanitizedForAutosave(locale: Locale): RepairInput {
        val validation = validate(locale)
        var sanitized = this
        if (RepairField.ODOMETER_KM in validation) sanitized = sanitized.copy(odometerKm = "")
        if (RepairField.COST in validation) sanitized = sanitized.copy(cost = "")
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

    /** Die Fehlermeldung zu [RepairEditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun RepairEditUiState.withErrors(): RepairEditUiState {
        if (!showErrors) return copy(errors = emptyMap())
        return copy(errors = input.validate(locale()))
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(RepairDraft(input, showErrors, original?.id, initialDate))
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für eine neue Reparatur heißt das, den automatisch angelegten
     * Datensatz (mit seinen Anhängen) zu löschen; für eine bestehende, sie auf den beim Öffnen des
     * Formulars geladenen Stand zurückzusetzen (siehe [openedRepair]). Bricht außerdem einen noch
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
     * Beim Verlassen ohne sinnvolle Eingabe (eine neue, komplett unberührte Reparatur) oder nach
     * [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn.
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && (discardConfirmed || state.isUnmodifiedNewRepairDraft()) -> deleteNewRepairDraft()
                !state.isNew && discardConfirmed -> revertToOpenedRepair()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /** Löscht die automatisch angelegte, noch nicht sinnvoll befüllte bzw. explizit verworfene neue Reparatur (mit ihren Anhängen) wieder. */
    private suspend fun deleteNewRepairDraft() {
        original?.id?.let { id -> runCatching { repository.deleteRepair(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Setzt eine bestehende Reparatur auf [openedRepair] zurück. */
    private suspend fun revertToOpenedRepair() {
        val baseline = openedRepair ?: return
        persistMutex.withLock { runCatching { repository.saveRepair(baseline) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }
}

private const val DRAFT_KEY = "repair_draft"

/** Verzögerung zwischen einer Formularänderung einer Reparatur und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L

/** Platzhalter für Tests und Vorschauen ohne Standorthardware; liefert nie einen Fix. */
private object RepairNoOpLocationProvider : LocationProvider {
    override suspend fun requestFreshFix(): LocationFix? = null
    override suspend fun lastKnownFix(maxAgeMillis: Long): LocationFix? = null
    override fun isLocationEnabled(): Boolean = false
}

/** Platzhalter für Tests und Vorschauen ohne Standorthardware; hat nie eine Berechtigung. */
private object RepairNoOpLocationPermissionGate : LocationPermissionGate {
    override fun hasPermission(): Boolean = false
    override fun hasRequestedBefore(): Boolean = false
    override fun markRequested() = Unit
}
