package app.restvolt.camperlog.ui.checklists

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateError
import app.restvolt.camperlog.domain.ChecklistTemplateField
import app.restvolt.camperlog.domain.ChecklistTemplateInput
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toTemplate
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Zustand des Vorlagen-Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class ChecklistTemplateEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: ChecklistTemplateInput = ChecklistTemplateInput(),
    val errors: Map<ChecklistTemplateField, ChecklistTemplateError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
    /** Die geladene, gespeicherte Vorlage; nötig, um sie über die Kopfzeile löschen zu können. */
    val original: ChecklistTemplate? = null,
)

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class ChecklistTemplateEditDraft(val input: ChecklistTemplateInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert eine Checklisten-Vorlage. [templateId] 0 legt eine neue Vorlage an.
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt der gespeicherten Vorlage angezeigt.
 */
class ChecklistTemplateEditViewModel(
    private val repository: ChecklistTemplateRepository,
    templateId: Long,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val draft: ChecklistTemplateEditDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        ChecklistTemplateEditUiState(isNew = templateId == 0L, isLoading = templateId != 0L).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<ChecklistTemplateEditUiState> = _uiState.asStateFlow()

    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (templateId != 0L) {
            viewModelScope.launch {
                val template = repository.observeAll().first().firstOrNull { it.id == templateId }
                _uiState.update { state ->
                    val input = if (state.isDirty) state.input else template?.toInput() ?: state.input
                    state.copy(isLoading = false, notFound = template == null, original = template, input = input)
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (ChecklistTemplateInput) -> ChecklistTemplateInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    fun onNameChange(name: String) = onInputChange { it.copy(name = name) }

    /** Hängt einen leeren Punkt an, der sofort bearbeitet werden kann. */
    fun onAddItem() = onInputChange { it.copy(items = it.items + "") }

    fun onItemChange(index: Int, text: String) = onInputChange { input ->
        input.copy(items = input.items.mapIndexed { i, item -> if (i == index) text else item })
    }

    fun onRemoveItem(index: Int) = onInputChange { input -> input.copy(items = input.items.filterIndexed { i, _ -> i != index }) }

    fun onMoveItemUp(index: Int) = onSwapItems(index, index - 1)

    fun onMoveItemDown(index: Int) = onSwapItems(index, index + 1)

    private fun onSwapItems(index: Int, other: Int) = onInputChange { input ->
        if (other < 0 || other >= input.items.size) return@onInputChange input
        val items = input.items.toMutableList()
        val moved = items.removeAt(index)
        items.add(other, moved)
        input.copy(items = items)
    }

    /** Validiert und speichert. */
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
        val template = state.input.toTemplate(state.original)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(template)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Die Fehlermeldung zu [ChecklistTemplateEditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun ChecklistTemplateEditUiState.withErrors(): ChecklistTemplateEditUiState {
        if (!showErrors) return copy(errors = emptyMap())
        return copy(errors = input.validate())
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(ChecklistTemplateEditDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "checklist_template_draft"
