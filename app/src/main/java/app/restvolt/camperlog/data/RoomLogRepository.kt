package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** [LogRepository] auf Basis von Room. [clock] liefert die Anlagezeit, [newUuid] die Kennung neuer Einträge. */
class RoomLogRepository(
    private val dao: LogDao,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : LogRepository {

    override fun observeLatest(vehicleId: Long): Flow<Map<LogType, LocalDate>> =
        dao.observeLatest(vehicleId).map { rows -> rows.associate { LogType.valueOf(it.type) to LocalDate.parse(it.date) } }

    override fun observeEntries(vehicleId: Long, type: LogType): Flow<List<LogEntry>> =
        dao.observeEntries(vehicleId, type.name).map { rows -> rows.map(LogEntryEntity::toDomain) }

    override suspend fun allEntries(): List<LogEntry> = dao.getAll().map(LogEntryEntity::toDomain)

    override suspend fun add(vehicleId: Long, type: LogType, date: LocalDate): LogEntry {
        val entry = LogEntry(uuid = newUuid(), vehicleId = vehicleId, type = type, date = date, createdAt = clock())
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(entry: LogEntry) {
        dao.insert(entry.toEntity())
    }
}
