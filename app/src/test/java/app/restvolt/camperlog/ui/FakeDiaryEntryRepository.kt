package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomDiaryEntryRepositoryTest. */
class FakeDiaryEntryRepository(initial: List<DiaryEntry> = emptyList()) : DiaryEntryRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val entries: List<DiaryEntry> get() = state.value

    override fun observeForTour(tourId: Long): Flow<List<DiaryEntry>> =
        state.map { list -> list.filter { it.tourId == tourId }.sortedBy { it.date } }

    override fun observeAllEntries(): Flow<List<DiaryEntry>> = state.map { list -> list.sortedByDescending { it.date } }

    override suspend fun allEntries(): List<DiaryEntry> = state.value

    override suspend fun save(entry: DiaryEntry): Long = if (entry.id == 0L) {
        val id = nextId++
        state.value += entry.copy(id = id)
        id
    } else {
        state.value = state.value.map { if (it.id == entry.id) entry else it }
        entry.id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(entry: DiaryEntry) {
        state.value += entry
    }
}
