package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomAttachmentRepositoryTest. */
class FakeAttachmentRepository(initial: List<Attachment> = emptyList()) : AttachmentRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val attachments: List<Attachment> get() = state.value

    override fun observeForOwner(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<Attachment>> =
        state.map { list -> list.filter { it.ownerType == ownerType && it.ownerId == ownerId } }

    override suspend fun forOwner(ownerType: AttachmentOwnerType, ownerId: Long): List<Attachment> =
        state.value.filter { it.ownerType == ownerType && it.ownerId == ownerId }

    override suspend fun allAttachments(): List<Attachment> = state.value

    override suspend fun add(attachment: Attachment): Long {
        val id = nextId++
        state.value += attachment.copy(id = id)
        return id
    }

    override suspend fun setLocation(id: Long, latitude: Double, longitude: Double) {
        state.value = state.value.map { if (it.id == id) it.copy(latitude = latitude, longitude = longitude) else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(attachment: Attachment) {
        state.value += attachment
    }

    override suspend fun deleteForOwner(ownerType: AttachmentOwnerType, ownerId: Long) {
        state.value = state.value.filterNot { it.ownerType == ownerType && it.ownerId == ownerId }
    }

    override suspend fun deleteForOwners(ownerType: AttachmentOwnerType, ownerIds: Collection<Long>) {
        state.value = state.value.filterNot { it.ownerType == ownerType && it.ownerId in ownerIds }
    }

    override suspend fun allFileNames(): Set<String> = state.value.mapTo(HashSet()) { it.fileName }
}
