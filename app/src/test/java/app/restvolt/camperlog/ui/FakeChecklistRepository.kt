package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomChecklistRepositoryTest. */
class FakeChecklistRepository(initial: List<Checklist> = emptyList()) : ChecklistRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val checklists: List<Checklist> get() = state.value

    override fun observeForTour(tourId: Long): Flow<List<Checklist>> =
        state.map { list -> list.filter { it.tourId == tourId }.sortedByDescending { it.createdAt } }

    override fun observeForVehicleWithoutTour(vehicleId: Long): Flow<List<Checklist>> = state.map { list ->
        list.filter { it.vehicleId == vehicleId && it.tourId == null }.sortedByDescending { it.createdAt }
    }

    override fun observeChecklist(id: Long): Flow<Checklist?> = state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeAll(): Flow<List<Checklist>> = state.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun allChecklists(): List<Checklist> = state.value

    override suspend fun save(checklist: Checklist): Long = if (checklist.id == 0L) {
        val id = nextId++
        state.value += checklist.copy(id = id)
        id
    } else {
        state.value = state.value.map { if (it.id == checklist.id) checklist else it }
        checklist.id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(checklist: Checklist) {
        state.value += checklist
    }

    override suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long) {
        state.value = state.value.map { if (it.tourId == tourId) it.copy(vehicleId = vehicleId) else it }
    }
}
