package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [ChecklistTemplateRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und
 * Änderung, [newUuid] die Kennung neuer Vorlagen ohne eigene UUID.
 */
class RoomChecklistTemplateRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : ChecklistTemplateRepository {

    private val dao get() = database.checklistTemplateDao()

    override fun observeAll(): Flow<List<ChecklistTemplate>> =
        dao.observeAll().map { rows -> rows.map(ChecklistTemplateWithItems::toDomain) }

    override suspend fun allTemplates(): List<ChecklistTemplate> = dao.getAll().map(ChecklistTemplateWithItems::toDomain)

    override suspend fun save(template: ChecklistTemplate): Long {
        val now = clock()
        return if (template.id == 0L) {
            val uuid = template.uuid.ifEmpty { newUuid() }
            val withTimestamps = template.copy(uuid = uuid, createdAt = now, updatedAt = now)
            dao.insertWithItems(withTimestamps.toEntity(), withTimestamps.toItemEntities())
        } else {
            val withTimestamp = template.copy(updatedAt = now)
            dao.updateWithItems(withTimestamp.toEntity(), withTimestamp.toItemEntities())
            withTimestamp.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(template: ChecklistTemplate) {
        dao.insertWithItems(template.toEntity(), template.toItemEntities())
    }
}
