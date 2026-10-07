package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [DiaryEntryRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und
 * Änderung, [newUuid] die Kennung neuer Einträge ohne eigene UUID.
 */
class RoomDiaryEntryRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : DiaryEntryRepository {

    private val dao get() = database.diaryEntryDao()

    override fun observeForTour(tourId: Long): Flow<List<DiaryEntry>> =
        dao.observeForTour(tourId).map { rows -> rows.map(DiaryEntryEntity::toDomain) }

    override suspend fun allEntries(): List<DiaryEntry> = dao.getAll().map(DiaryEntryEntity::toDomain)

    override suspend fun save(entry: DiaryEntry): Long {
        val now = clock()
        return if (entry.id == 0L) {
            val uuid = entry.uuid.ifEmpty { newUuid() }
            dao.insert(entry.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.update(entry.copy(updatedAt = now).toEntity())
            entry.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(entry: DiaryEntry) {
        dao.insert(entry.toEntity())
    }
}
