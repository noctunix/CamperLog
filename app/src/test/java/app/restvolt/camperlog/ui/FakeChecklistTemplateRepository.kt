package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomChecklistTemplateRepositoryTest. */
class FakeChecklistTemplateRepository(initial: List<ChecklistTemplate> = emptyList()) : ChecklistTemplateRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val templates: List<ChecklistTemplate> get() = state.value

    override fun observeAll(): Flow<List<ChecklistTemplate>> = state.map { list -> list.sortedBy { it.name } }

    override suspend fun allTemplates(): List<ChecklistTemplate> = state.value

    override suspend fun save(template: ChecklistTemplate): Long = if (template.id == 0L) {
        val id = nextId++
        state.value += template.copy(id = id)
        id
    } else {
        state.value = state.value.map { if (it.id == template.id) template else it }
        template.id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(template: ChecklistTemplate) {
        state.value += template
    }
}
