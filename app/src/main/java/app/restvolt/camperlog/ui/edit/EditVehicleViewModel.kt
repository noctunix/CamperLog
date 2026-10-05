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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

/** Zustand des Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class EditVehicleUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: VehicleInput = VehicleInput(),
    val errors: Map<VehicleField, VehicleError> = emptyMap(),
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
internal data class VehicleDraft(val input: VehicleInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert ein Fahrzeug. [vehicleId] 0 legt ein neues Fahrzeug an.
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart
 * des Prozesses statt des gespeicherten Fahrzeugs angezeigt.
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

    private var original: Vehicle? = null
    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (vehicleId != 0L) {
            viewModelScope.launch {
                val vehicle = repository.observeVehicle(vehicleId).first()
                original = vehicle
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else vehicle?.toInput(locale()) ?: it.input
                    it.copy(isLoading = false, notFound = vehicle == null, input = input)
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (VehicleInput) -> VehicleInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    /** Validiert und speichert; bei Erfolg wird [EditVehicleUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val locale = locale()
        val errors = state.input.validate(locale, state.isNew)
        if (errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update { it.copy(errors = errors, rejectedSaves = it.rejectedSaves + 1) }
            return
        }
        val vehicle = state.input.toVehicle(original, locale)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(vehicle)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
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
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(VehicleDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "vehicle_draft"
