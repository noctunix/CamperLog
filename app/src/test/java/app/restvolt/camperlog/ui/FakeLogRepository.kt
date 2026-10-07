package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomLogRepositoryTest. */
class FakeLogRepository(initial: List<LogEntry> = emptyList()) : LogRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val entries: List<LogEntry> get() = state.value

    override fun observeLatest(vehicleId: Long) =
        state.map { list ->
            list.filter { it.vehicleId == vehicleId }
                .groupBy { it.type }
                .mapValues { (_, entries) -> entries.maxOf { it.date } }
        }

    override fun observeEntries(vehicleId: Long, type: LogType): Flow<List<LogEntry>> =
        state.map { list ->
            list.filter { it.vehicleId == vehicleId && it.type == type }
                .sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.id })
        }

    override suspend fun allEntries(): List<LogEntry> = state.value

    override fun observeAllEntries(): Flow<List<LogEntry>> =
        state.map { list -> list.sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.id }) }

    override suspend fun hasEntries(): Boolean = state.value.isNotEmpty()

    override suspend fun add(vehicleId: Long, type: LogType, date: LocalDate): LogEntry {
        val entry = LogEntry(id = nextId++, uuid = "log-${nextId}", vehicleId = vehicleId, type = type, date = date, createdAt = Instant.EPOCH)
        state.value += entry
        return entry
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(entry: LogEntry) {
        state.value += entry
    }

    override suspend fun linkedEntry(stationId: Long, type: LogType): LogEntry? =
        state.value.firstOrNull { it.stationId == stationId && it.type == type }

    override suspend fun findUnlinked(vehicleId: Long, type: LogType, date: LocalDate): LogEntry? =
        state.value.firstOrNull { it.vehicleId == vehicleId && it.type == type && it.date == date && it.stationId == null }

    override suspend fun link(entryId: Long, stationId: Long) {
        state.value = state.value.map { if (it.id == entryId) it.copy(stationId = stationId) else it }
    }

    override suspend fun addLinked(vehicleId: Long, type: LogType, date: LocalDate, stationId: Long, uuid: String?): LogEntry {
        val entry = LogEntry(
            id = nextId++,
            uuid = uuid ?: "log-${nextId}",
            vehicleId = vehicleId,
            type = type,
            date = date,
            createdAt = Instant.EPOCH,
            stationId = stationId,
        )
        state.value += entry
        return entry
    }

    /** Simuliert das Fremdschlüsselverhalten `ON DELETE SET NULL`, wenn [FakeStationRepository] eine Station entfernt. */
    fun detachStation(stationId: Long) {
        state.value = state.value.map { if (it.stationId == stationId) it.copy(stationId = null) else it }
    }
}
