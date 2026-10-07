package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [ChecklistRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Checklisten ohne eigene UUID.
 */
class RoomChecklistRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : ChecklistRepository {

    private val dao get() = database.checklistDao()

    override fun observeForTour(tourId: Long): Flow<List<Checklist>> =
        dao.observeForTour(tourId).map { rows -> rows.map(ChecklistWithItems::toDomain) }

    override fun observeForVehicleWithoutTour(vehicleId: Long): Flow<List<Checklist>> =
        dao.observeForVehicleWithoutTour(vehicleId).map { rows -> rows.map(ChecklistWithItems::toDomain) }

    override fun observeChecklist(id: Long): Flow<Checklist?> = dao.observeById(id).map { it?.toDomain() }

    override fun observeAll(): Flow<List<Checklist>> = dao.observeAll().map { rows -> rows.map(ChecklistWithItems::toDomain) }

    override suspend fun allChecklists(): List<Checklist> = dao.getAll().map(ChecklistWithItems::toDomain)

    override suspend fun save(checklist: Checklist): Long {
        val now = clock()
        return if (checklist.id == 0L) {
            val uuid = checklist.uuid.ifEmpty { newUuid() }
            val withTimestamps = checklist.copy(uuid = uuid, createdAt = now, updatedAt = now)
            dao.insertWithItems(withTimestamps.toEntity(), withTimestamps.toItemEntities())
        } else {
            val withTimestamp = checklist.copy(updatedAt = now)
            dao.updateWithItems(withTimestamp.toEntity(), withTimestamp.toItemEntities())
            withTimestamp.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(checklist: Checklist) {
        dao.insertWithItems(checklist.toEntity(), checklist.toItemEntities())
    }

    override suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long) = database.withTransaction {
        dao.getForTour(tourId).map { it.toDomain() }.filter { it.vehicleId != vehicleId }
            .forEach { save(it.copy(vehicleId = vehicleId)) }
    }
}
