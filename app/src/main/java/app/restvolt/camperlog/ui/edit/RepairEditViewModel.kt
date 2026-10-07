package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.RepairError
import app.restvolt.camperlog.domain.RepairField
import app.restvolt.camperlog.domain.RepairInput
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toRepair
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Locale

/** Zustand des Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class RepairEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: RepairInput = RepairInput(),
    val errors: Map<RepairField, RepairError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Der letzte Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
    /** Die geladene, gespeicherte Reparatur; nötig, um sie über die Kopfzeile löschen zu können. */
    val original: Repair? = null,
    /**
     * Die id dieser Reparatur, sobald bekannt (bei einer bestehenden von Anfang an, bei einer neuen
     * erst nach dem ersten Speichern); `0` bis dahin, siehe [app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection].
     */
    val savedRepairId: Long = 0,
)

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class RepairDraft(val input: RepairInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert eine Reparatur eines Fahrzeugs. [repairId] 0 legt eine neue
 * Reparatur an. Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem
 * Neustart des Prozesses statt der gespeicherten Reparatur angezeigt.
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
) : ViewModel() {

    private val draft: RepairDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        RepairEditUiState(isNew = repairId == 0L, isLoading = repairId != 0L, savedRepairId = repairId).let { state ->
            when {
                draft != null -> state.copy(input = draft.input, isDirty = true)
                repairId == 0L -> state.copy(input = RepairInput(date = today()))
                else -> state
            }
        },
    )
    val uiState: StateFlow<RepairEditUiState> = _uiState.asStateFlow()

    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (repairId != 0L) {
            viewModelScope.launch {
                val repair = repository.observeRepairs(vehicleId).first().firstOrNull { it.id == repairId }
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else repair?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = repair == null, input = input, original = repair)
                }
            }
        } else {
            viewModelScope.launch {
                val currency = repository.lastUsedRepairCurrency() ?: exchangeRates.observeMainCurrency().first()
                // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                _uiState.update {
                    if (it.isDirty) it else it.copy(input = it.input.copy(costCurrency = currency))
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (RepairInput) -> RepairInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    /** Validiert und speichert; bei Erfolg wird [RepairEditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val locale = locale()
        val errors = state.input.validate(locale)
        if (errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update { it.copy(errors = errors, rejectedSaves = it.rejectedSaves + 1) }
            return
        }
        val repair = state.input.toRepair(state.original, vehicleId, locale)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                val id = repository.saveRepair(repair)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true, savedRepairId = id) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
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
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(RepairDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "repair_draft"
