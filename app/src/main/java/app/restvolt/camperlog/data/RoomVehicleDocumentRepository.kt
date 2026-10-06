package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [VehicleDocumentRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und
 * Änderung, [newUuid] die Kennung neuer Dokumente ohne eigene UUID. [delete] löscht die Anhänge des
 * Dokuments in derselben Transaktion mit, siehe KDoc von [RoomVehicleRepository].
 */
class RoomVehicleDocumentRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : VehicleDocumentRepository {

    private val dao get() = database.vehicleDocumentDao()
    private val attachmentDao get() = database.attachmentDao()

    override fun observeForVehicle(vehicleId: Long): Flow<List<VehicleDocument>> =
        dao.observeForVehicle(vehicleId).map { rows -> rows.map(VehicleDocumentEntity::toDomain) }

    override suspend fun allDocuments(): List<VehicleDocument> = dao.getAll().map(VehicleDocumentEntity::toDomain)

    override suspend fun save(document: VehicleDocument): Long {
        val now = clock()
        return if (document.id == 0L) {
            val uuid = document.uuid.ifEmpty { newUuid() }
            dao.insert(document.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.update(document.copy(updatedAt = now).toEntity())
            document.id
        }
    }

    override suspend fun delete(id: Long) = database.withTransaction {
        attachmentDao.deleteForOwner(AttachmentOwnerType.VEHICLE_DOCUMENT.name, id)
        dao.deleteById(id)
    }

    override suspend fun restore(document: VehicleDocument) {
        dao.insert(document.toEntity())
    }
}
