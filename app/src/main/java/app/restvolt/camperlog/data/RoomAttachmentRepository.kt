package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.LATITUDE_RANGE
import app.restvolt.camperlog.domain.LONGITUDE_RANGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * [AttachmentRepository] auf Basis von Room. [clock] liefert die Anlagezeit, [newUuid] die Kennung
 * neuer Anhänge ohne eigene UUID, [fileStore] schreibt die GPS-EXIF-Tags von [setLocation] in die
 * Datei. Das kaskadierende Löschen eines Eintrags (Station, Reparatur, Bordbuch-Eintrag,
 * Fahrzeugdokument) entfernt seine Anhänge selbst, siehe die jeweiligen `Room*Repository`-Implementierungen.
 */
class RoomAttachmentRepository(
    private val dao: AttachmentDao,
    private val fileStore: AttachmentFileStore,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : AttachmentRepository {

    override fun observeForOwner(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<Attachment>> =
        dao.observeForOwner(ownerType.name, ownerId).map { rows -> rows.map(AttachmentEntity::toDomain) }

    override suspend fun forOwner(ownerType: AttachmentOwnerType, ownerId: Long): List<Attachment> =
        dao.forOwner(ownerType.name, ownerId).map(AttachmentEntity::toDomain)

    override suspend fun allAttachments(): List<Attachment> = dao.getAll().map(AttachmentEntity::toDomain)

    override suspend fun add(attachment: Attachment): Long {
        val uuid = attachment.uuid.ifEmpty { newUuid() }
        return dao.insert(attachment.copy(uuid = uuid, createdAt = clock()).toEntity())
    }

    override suspend fun setLocation(id: Long, latitude: Double, longitude: Double) {
        require(latitude in LATITUDE_RANGE && longitude in LONGITUDE_RANGE) { "Koordinaten außerhalb des gültigen Bereichs" }
        val fileName = dao.getFileName(id) ?: return
        dao.updateLocation(id, latitude, longitude)
        fileStore.writeLocation(fileName, latitude, longitude)
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(attachment: Attachment) {
        dao.insert(attachment.toEntity())
    }

    override suspend fun deleteForOwner(ownerType: AttachmentOwnerType, ownerId: Long) = dao.deleteForOwner(ownerType.name, ownerId)

    override suspend fun deleteForOwners(ownerType: AttachmentOwnerType, ownerIds: Collection<Long>) {
        if (ownerIds.isNotEmpty()) dao.deleteForOwners(ownerType.name, ownerIds.toList())
    }

    override suspend fun allFileNames(): Set<String> = dao.getAllFileNames().toSet()
}
