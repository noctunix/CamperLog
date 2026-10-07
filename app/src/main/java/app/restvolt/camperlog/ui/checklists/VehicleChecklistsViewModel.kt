package app.restvolt.camperlog.ui.checklists

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Checklisten eines Fahrzeugs ohne Tourbezug (z. B. Einwintern). */
data class VehicleChecklistsUiState(
    val isLoading: Boolean = true,
    val checklists: List<Checklist> = emptyList(),
    val templates: List<ChecklistTemplate> = emptyList(),
)

/** Rückmeldung zu einer Checkliste; die Liste zeigt sie als Snackbar. */
sealed interface VehicleChecklistMessage {
    data class Deleted(val checklist: Checklist) : VehicleChecklistMessage
    data class Failed(@StringRes val text: Int) : VehicleChecklistMessage
}

/** Hält die Checklisten und Vorlagen eines Fahrzeugs aktuell; startet neue Checklisten und löscht mit Rückgängig. */
class VehicleChecklistsViewModel(
    private val checklists: ChecklistRepository,
    private val templates: ChecklistTemplateRepository,
    private val vehicleId: Long,
) : ViewModel() {

    val uiState: StateFlow<VehicleChecklistsUiState> = combine(
        checklists.observeForVehicleWithoutTour(vehicleId),
        templates.observeAll(),
    ) { checklistList, templateList -> VehicleChecklistsUiState(isLoading = false, checklists = checklistList, templates = templateList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleChecklistsUiState())

    private val _message = MutableStateFlow<VehicleChecklistMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<VehicleChecklistMessage?> = _message.asStateFlow()

    private val _startedChecklistId = MutableStateFlow<Long?>(null)

    /** id der gerade gestarteten Checkliste, bis [onChecklistStartHandled] aufgerufen wird; löst das Öffnen aus. */
    val startedChecklistId: StateFlow<Long?> = _startedChecklistId.asStateFlow()

    /** Legt [suggested] als neue Vorlagen an; ein erneuter Aufruf legt sie erneut (zusätzlich) an. */
    fun addSuggestedTemplates(suggested: List<ChecklistTemplate>) {
        viewModelScope.launch { suggested.forEach { templates.save(it) } }
    }

    /** Startet [template] als neue Checkliste dieses Fahrzeugs ohne Tourbezug. */
    fun startChecklist(template: ChecklistTemplate) {
        viewModelScope.launch {
            val checklist = app.restvolt.camperlog.domain.startChecklist(template, vehicleId)
            _startedChecklistId.value = checklists.save(checklist)
        }
    }

    /** [startedChecklistId] wurde übernommen und soll nicht erneut ausgelöst werden. */
    fun onChecklistStartHandled() {
        _startedChecklistId.value = null
    }

    /** Löscht [checklist] und bietet über [VehicleChecklistMessage.Deleted] das Rückgängigmachen an. */
    fun deleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            _message.value = try {
                checklists.delete(checklist.id)
                VehicleChecklistMessage.Deleted(checklist)
            } catch (_: SQLException) {
                VehicleChecklistMessage.Failed(R.string.checklist_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteChecklist] entfernte Checkliste unverändert wieder her. */
    fun undoDeleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            try {
                checklists.restore(checklist)
            } catch (_: SQLException) {
                _message.value = VehicleChecklistMessage.Failed(R.string.checklist_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: VehicleChecklistMessage) {
        _message.compareAndSet(shown, null)
    }
}
