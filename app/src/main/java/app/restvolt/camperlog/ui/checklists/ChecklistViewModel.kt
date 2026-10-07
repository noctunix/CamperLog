package app.restvolt.camperlog.ui.checklists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Checklisten-Ansicht. */
data class ChecklistUiState(val isLoading: Boolean = true, val notFound: Boolean = false, val checklist: Checklist? = null)

/** Zeigt eine gestartete Checkliste an; jedes Häkchen und „Alle Haken entfernen" speichern sofort. */
class ChecklistViewModel(private val repository: ChecklistRepository, checklistId: Long) : ViewModel() {

    val uiState: StateFlow<ChecklistUiState> = repository.observeChecklist(checklistId)
        .map { ChecklistUiState(isLoading = false, notFound = it == null, checklist = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistUiState())

    /** Dreht den Haken des Punkts mit [index] um und speichert sofort. */
    fun toggleItem(index: Int) {
        val checklist = uiState.value.checklist ?: return
        val items = checklist.items.mapIndexed { i, item -> if (i == index) item.copy(checked = !item.checked) else item }
        viewModelScope.launch { repository.save(checklist.copy(items = items)) }
    }

    /** Entfernt alle Haken. */
    fun uncheckAll() {
        val checklist = uiState.value.checklist ?: return
        if (checklist.items.none { it.checked }) return
        viewModelScope.launch { repository.save(checklist.copy(items = checklist.items.map { it.copy(checked = false) })) }
    }
}
