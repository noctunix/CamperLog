package app.restvolt.camperlog.ui.checklists

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Vorlagenliste. */
data class ChecklistTemplatesUiState(val isLoading: Boolean = true, val templates: List<ChecklistTemplate> = emptyList())

/** Rückmeldung zu einer Vorlage; die Vorlagenliste zeigt sie als Snackbar. */
sealed interface ChecklistTemplateMessage {
    data class Deleted(val template: ChecklistTemplate) : ChecklistTemplateMessage
    data class Failed(@StringRes val text: Int) : ChecklistTemplateMessage
}

/** Hält die Liste der Checklisten-Vorlagen aktuell; legt Vorschläge an und löscht Vorlagen mit Rückgängig. */
class ChecklistTemplatesViewModel(private val templates: ChecklistTemplateRepository) : ViewModel() {

    val uiState: StateFlow<ChecklistTemplatesUiState> = templates.observeAll()
        .map { ChecklistTemplatesUiState(isLoading = false, templates = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistTemplatesUiState())

    private val _message = MutableStateFlow<ChecklistTemplateMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<ChecklistTemplateMessage?> = _message.asStateFlow()

    /** Legt [suggested] als neue Vorlagen an; ein erneuter Aufruf legt sie erneut (zusätzlich) an. */
    fun addSuggestedTemplates(suggested: List<ChecklistTemplate>) {
        viewModelScope.launch { suggested.forEach { templates.save(it) } }
    }

    /** Löscht [template] und bietet über [ChecklistTemplateMessage.Deleted] das Rückgängigmachen an. */
    fun deleteTemplate(template: ChecklistTemplate) {
        viewModelScope.launch {
            _message.value = try {
                templates.delete(template.id)
                ChecklistTemplateMessage.Deleted(template)
            } catch (_: SQLException) {
                ChecklistTemplateMessage.Failed(R.string.checklist_template_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteTemplate] entfernte Vorlage unverändert wieder her. */
    fun undoDeleteTemplate(template: ChecklistTemplate) {
        viewModelScope.launch {
            try {
                templates.restore(template)
            } catch (_: SQLException) {
                _message.value = ChecklistTemplateMessage.Failed(R.string.checklist_template_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: ChecklistTemplateMessage) {
        _message.compareAndSet(shown, null)
    }
}
