package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleError
import app.restvolt.camperlog.domain.VehicleField
import app.restvolt.camperlog.domain.VehicleInput
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toVehicle
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
import java.util.Locale

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [EditVehicleViewModel]). */
data class EditVehicleUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: VehicleInput = VehicleInput(),
    val errors: Map<VehicleField, VehicleError> = emptyMap(),
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
)

/**
 * Ob der Entwurf eines neuen Fahrzeugs noch unverändert der Vorbelegung entspricht - also beim
 * Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun EditVehicleUiState.isUnmodifiedNewVehicleDraft(): Boolean = isNew && input == VehicleInput()

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für ein neues Fahrzeug, solange
 * sein Entwurf nicht mehr [isUnmodifiedNewVehicleDraft] ist; für ein bestehendes, solange sich seit
 * dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun EditVehicleUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewVehicleDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class VehicleDraft(
    val input: VehicleInput,
    val showErrors: Boolean,
    /** Für ein neues Fahrzeug die id des schon automatisch angelegten Fahrzeugs, falls es sie schon gibt. */
    val persistedVehicleId: Long? = null,
)

/**
 * Lädt, validiert und speichert ein Fahrzeug. [vehicleId] 0 legt ein neues Fahrzeug an.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für ein neues wie für ein bestehendes Fahrzeug. Ein neues Fahrzeug wird dafür
 * sofort als echter Datensatz angelegt, auch mit noch leerem Namen. Verlässt der Nutzer das Formular
 * wieder, entscheidet [onCleared] je nach Fall: Ein komplett unberührter Entwurf eines neuen Fahrzeugs
 * wird gelöscht; eine echte Änderung kann der Nutzer vorher über [onDiscardConfirmed] verwerfen - für
 * ein neues Fahrzeug heißt das Löschen, für ein bestehendes das Zurücksetzen auf den beim Öffnen
 * geladenen Stand (siehe [openedVehicle]).
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt des gespeicherten Fahrzeugs angezeigt; für ein neues Fahrzeug sichert das auch die
 * id des schon automatisch angelegten Fahrzeugs gegen einen solchen Neustart ab.
 *
 * @param locale liefert die aktuelle Sprache für Zahlen und Beträge; wird bei jedem Zugriff neu
 *   gelesen, damit ein Sprachwechsel bei laufendem ViewModel greift
 */
class EditVehicleViewModel(
    private val repository: VehicleRepository,
    vehicleId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val locale: () -> Locale = { app.restvolt.camperlog.domain.supportedLocale(Locale.getDefault()) },
) : ViewModel() {

    private val draft: VehicleDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        EditVehicleUiState(isNew = vehicleId == 0L, isLoading = vehicleId != 0L).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<EditVehicleUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: Vehicle? = null

    /**
     * Für ein bestehendes Fahrzeug der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für ein neues Fahrzeug `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedVehicle: Vehicle? = null
    private var showErrors = draft?.showErrors ?: false

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide ein neues Fahrzeug anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (vehicleId != 0L) {
            viewModelScope.launch {
                val vehicle = repository.observeVehicle(vehicleId).first()
                original = vehicle
                openedVehicle = vehicle
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else vehicle?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = vehicle == null, input = input)
                }
            }
        } else {
            viewModelScope.launch {
                val restoredId = draft?.persistedVehicleId
                if (restoredId != null) original = repository.observeVehicle(restoredId).first()
                if (original == null) attemptPersist()
            }
        }
    }

    /**
     * Übernimmt eine Formularänderung. Es gibt keinen Speichern-Versuch mehr, der Fehler sichtbar
     * machen könnte - stattdessen macht schon die erste Eingabe alle aktuellen Fehler sichtbar, und
     * löst außerdem den debounced Autosave aus.
     */
    fun onInputChange(transform: (VehicleInput) -> VehicleInput) {
        showErrors = true
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
        scheduleAutosave()
    }

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für ein neues Fahrzeug), sonst aktualisieren. Ein noch leerer Name blockiert das
     * nicht - er bleibt als inline angezeigter Pflichtfeld-Fehler stehen, verhindert aber nicht, dass
     * die übrige Eingabe sofort gespeichert wird. Jedes andere ungültige Feld wird stattdessen
     * entschärft, siehe [sanitizedForAutosave].
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val loc = locale()
            val effectiveInput = state.input.sanitizedForAutosave(loc, state.isNew)
            val vehicle = effectiveInput.toVehicle(original, loc)
            try {
                val id = repository.save(vehicle)
                original = repository.observeVehicle(id).first()
                _uiState.update { it.copy(saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /**
     * Entschärft eine Eingabe für [attemptPersist]: Jedes Feld, das [VehicleInput.validate] außer dem
     * Namen als ungültig meldet, fällt für den gespeicherten Stand weg, statt den Schreibzugriff für
     * das ganze Fahrzeug zu blockieren - der Rest der Eingabe speichert sich weiter, während das
     * betroffene Feld inline seinen Fehler zeigt. Das verändert nur den gespeicherten Stand, nicht die
     * Anzeige im Formular.
     */
    private fun VehicleInput.sanitizedForAutosave(locale: Locale, isNew: Boolean): VehicleInput {
        var sanitized = this
        for (field in validate(locale, isNew).keys) {
            sanitized = when (field) {
                VehicleField.NAME -> sanitized
                VehicleField.FIRST_REGISTRATION -> sanitized.copy(firstRegistration = null)
                VehicleField.PURCHASE_PRICE -> sanitized.copy(purchasePrice = "")
                VehicleField.PURCHASE_ODOMETER_KM -> sanitized.copy(purchaseOdometerKm = "")
                VehicleField.SALE_DATE -> sanitized.copy(saleDate = null)
                VehicleField.SALE_PRICE -> sanitized.copy(salePrice = "")
                VehicleField.SALE_ODOMETER_KM -> sanitized.copy(saleOdometerKm = "")
                VehicleField.INSURANCE_PREMIUM_PER_YEAR -> sanitized.copy(insurancePremiumPerYear = "")
                VehicleField.VEHICLE_TAX_PER_YEAR -> sanitized.copy(vehicleTaxPerYear = "")
                VehicleField.LENGTH -> sanitized.copy(lengthCm = "")
                VehicleField.WIDTH -> sanitized.copy(widthCm = "")
                VehicleField.HEIGHT -> sanitized.copy(heightCm = "")
                VehicleField.WHEELBASE -> sanitized.copy(wheelbaseCm = "")
                VehicleField.FRONT_OVERHANG -> sanitized.copy(frontOverhangCm = "")
                VehicleField.GROSS_WEIGHT_KG -> sanitized.copy(grossWeightKg = "")
                VehicleField.MEASURED_EMPTY_WEIGHT_KG -> sanitized.copy(measuredEmptyWeightKg = "")
                VehicleField.ALLOWED_FRONT_AXLE_KG -> sanitized.copy(allowedFrontAxleKg = "")
                VehicleField.ALLOWED_REAR_AXLE_KG -> sanitized.copy(allowedRearAxleKg = "")
                VehicleField.MEASURED_FRONT_AXLE_KG -> sanitized.copy(measuredFrontAxleKg = "")
                VehicleField.MEASURED_REAR_AXLE_KG -> sanitized.copy(measuredRearAxleKg = "")
                VehicleField.WEIGHING_DATE -> sanitized.copy(weighingDate = null)
                VehicleField.BREAKDOWN_PHONE -> sanitized.copy(breakdownPhone = "")
                VehicleField.TRAVEL_PROTECTION_PHONE -> sanitized.copy(travelProtectionPhone = "")
                VehicleField.INSURER_CLAIMS_PHONE -> sanitized.copy(insurerClaimsPhone = "")
                VehicleField.POWER_KW -> sanitized.copy(powerKw = "")
                VehicleField.DISPLACEMENT_CC -> sanitized.copy(displacementCc = "")
                VehicleField.TIRE_PRESSURE_FRONT -> sanitized.copy(tirePressureFrontBar = "")
                VehicleField.TIRE_PRESSURE_REAR -> sanitized.copy(tirePressureRearBar = "")
                VehicleField.FUEL_TANK -> sanitized.copy(fuelTankL = "")
                VehicleField.AD_BLUE_TANK -> sanitized.copy(adBlueTankL = "")
                VehicleField.FRESH_WATER_TANK -> sanitized.copy(freshWaterTankL = "")
                VehicleField.GREY_WATER_TANK -> sanitized.copy(greyWaterTankL = "")
                VehicleField.BOILER -> sanitized.copy(boilerL = "")
                VehicleField.CASSETTE -> sanitized.copy(cassetteL = "")
                VehicleField.BATTERY_CAPACITY_AH -> sanitized.copy(batteryCapacityAh = "")
                VehicleField.SOLAR_POWER_WP -> sanitized.copy(solarPowerWp = "")
                VehicleField.LAST_OIL_CHANGE_DATE -> sanitized.copy(lastOilChangeDate = null)
                VehicleField.LAST_OIL_CHANGE_ODOMETER_KM -> sanitized.copy(lastOilChangeOdometerKm = "")
            }
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

    /** Die Fehlermeldung zu [EditVehicleUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun EditVehicleUiState.withErrors(): EditVehicleUiState {
        if (!showErrors) return copy(errors = emptyMap())
        return copy(errors = input.validate(locale(), isNew))
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(VehicleDraft(input, showErrors, original?.id))
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für ein neues Fahrzeug heißt das, den automatisch angelegten
     * Datensatz zu löschen; für ein bestehendes, es auf den beim Öffnen des Formulars geladenen Stand
     * zurückzusetzen (siehe [openedVehicle]). Bricht außerdem einen noch wartenden Autosave ab, damit
     * er nicht doch noch zwischen diesem Aufruf und dem tatsächlichen Verlassen feuert.
     */
    fun onDiscardConfirmed() {
        discardConfirmed = true
        pendingSaveJob?.cancel()
    }

    /** Ob [onDiscardConfirmed] für diese Sitzung schon aufgerufen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /**
     * Beim Verlassen ohne sinnvolle Eingabe (ein neues, komplett unberührtes Fahrzeug) oder nach
     * [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn.
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && (discardConfirmed || state.isUnmodifiedNewVehicleDraft()) -> deleteNewVehicleDraft()
                !state.isNew && discardConfirmed -> revertToOpenedVehicle()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /**
     * Löscht das automatisch angelegte, noch nicht sinnvoll befüllte bzw. explizit verworfene neue
     * Fahrzeug wieder. Es existiert zu diesem Zeitpunkt immer schon mindestens ein weiteres Fahrzeug
     * (sonst gäbe es das Formular gar nicht), daher lehnt [VehicleRepository.delete] den Versuch nie
     * als letztes verbliebenes Fahrzeug ab.
     */
    private suspend fun deleteNewVehicleDraft() {
        original?.id?.let { id -> runCatching { repository.delete(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Setzt ein bestehendes Fahrzeug auf [openedVehicle] zurück, inklusive seiner Achsen. */
    private suspend fun revertToOpenedVehicle() {
        val baseline = openedVehicle ?: return
        persistMutex.withLock { runCatching { repository.save(baseline) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }
}

private const val DRAFT_KEY = "vehicle_draft"

/** Verzögerung zwischen einer Formularänderung eines Fahrzeugs und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
