package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [LogRepository] auf Basis von Room. [clock] liefert die Anlagezeit, [newUuid] die Kennung neuer
 * Einträge. [delete] löscht die Anhänge des Eintrags in derselben Transaktion mit, siehe KDoc von
 * [RoomVehicleRepository].
 */
class RoomLogRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : LogRepository {

    private val dao get() = database.logDao()
    private val attachmentDao get() = database.attachmentDao()

    override fun observeLatest(vehicleId: Long): Flow<Map<LogType, LocalDate>> =
        dao.observeLatest(vehicleId).map { rows -> rows.associate { LogType.valueOf(it.type) to LocalDate.parse(it.date) } }

    override fun observeEntries(vehicleId: Long, type: LogType): Flow<List<LogEntry>> =
        dao.observeEntries(vehicleId, type.name).map { rows -> rows.map(LogEntryEntity::toDomain) }

    override suspend fun allEntries(): List<LogEntry> = dao.getAll().map(LogEntryEntity::toDomain)

    override suspend fun hasEntries(): Boolean = dao.hasAny()

    override suspend fun add(vehicleId: Long, type: LogType, date: LocalDate): LogEntry {
        val entry = LogEntry(uuid = newUuid(), vehicleId = vehicleId, type = type, date = date, createdAt = clock())
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }

    override suspend fun delete(id: Long) = database.withTransaction {
        attachmentDao.deleteForOwner(AttachmentOwnerType.LOG_ENTRY.name, id)
        dao.deleteById(id)
    }

    override suspend fun restore(entry: LogEntry) {
        dao.insert(entry.toEntity())
    }

    override suspend fun linkedEntry(stationId: Long, type: LogType): LogEntry? = dao.linkedEntry(stationId, type.name)?.toDomain()

    override suspend fun findUnlinked(vehicleId: Long, type: LogType, date: LocalDate): LogEntry? =
        dao.findUnlinked(vehicleId, type.name, date.toString())?.toDomain()

    override suspend fun link(entryId: Long, stationId: Long) = dao.link(entryId, stationId)

    override suspend fun addLinked(vehicleId: Long, type: LogType, date: LocalDate, stationId: Long, uuid: String?): LogEntry {
        val entry = LogEntry(
            uuid = uuid ?: newUuid(),
            vehicleId = vehicleId,
            type = type,
            date = date,
            createdAt = clock(),
            stationId = stationId,
        )
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }
}
