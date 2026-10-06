package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomAttachmentRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomAttachmentRepository
    private val fileStore = FakeAttachmentFileStore()
    private var now = Instant.parse("2026-01-01T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAttachmentRepository(db.attachmentDao(), fileStore) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun attachment(ownerType: AttachmentOwnerType = AttachmentOwnerType.STATION, ownerId: Long = 1, fileName: String = "a.jpg") = Attachment(
        ownerType = ownerType,
        ownerId = ownerId,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = 100,
        width = 10,
        height = 10,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun addGeneratesUuidAndCreatedAtAndObserveForOwnerFiltersByTypeAndId() = runTest {
        val id = repository.add(attachment(ownerId = 1))
        repository.add(attachment(ownerId = 2))
        repository.add(attachment(AttachmentOwnerType.REPAIR, ownerId = 1))

        val forStation1 = repository.observeForOwner(AttachmentOwnerType.STATION, 1).first()
        assertEquals(1, forStation1.size)
        assertEquals(id, forStation1.single().id)
        assertEquals(now, forStation1.single().createdAt)
        assertEquals(4, UUID.fromString(forStation1.single().uuid).version())
    }

    @Test
    fun deleteRemovesOnlyTheRowNotTheFile() = runTest {
        val id = repository.add(attachment())

        repository.delete(id)

        assertEquals(emptyList<Attachment>(), repository.forOwner(AttachmentOwnerType.STATION, 1))
    }

    @Test
    fun restoreBringsBackADeletedAttachmentWithItsOriginalIdAndUuid() = runTest {
        val id = repository.add(attachment())
        val stored = repository.forOwner(AttachmentOwnerType.STATION, 1).single()
        repository.delete(id)

        repository.restore(stored)

        assertEquals(listOf(stored), repository.forOwner(AttachmentOwnerType.STATION, 1))
    }

    @Test
    fun deleteForOwnersRemovesAttachmentsOfSeveralOwnersOfTheSameType() = runTest {
        repository.add(attachment(ownerId = 1, fileName = "a.jpg"))
        repository.add(attachment(ownerId = 2, fileName = "b.jpg"))
        repository.add(attachment(ownerId = 3, fileName = "c.jpg"))

        repository.deleteForOwners(AttachmentOwnerType.STATION, listOf(1, 2))

        assertEquals(listOf("c.jpg"), repository.allAttachments().map { it.fileName })
    }

    @Test
    fun deleteForOwnersWithAnEmptyCollectionIsANoOp() = runTest {
        repository.add(attachment(ownerId = 1))

        repository.deleteForOwners(AttachmentOwnerType.STATION, emptyList())

        assertEquals(1, repository.allAttachments().size)
    }

    @Test
    fun allFileNamesReturnsEveryStoredFileNameOnce() = runTest {
        repository.add(attachment(fileName = "a.jpg"))
        repository.add(attachment(AttachmentOwnerType.REPAIR, fileName = "b.pdf"))

        assertEquals(setOf("a.jpg", "b.pdf"), repository.allFileNames())
    }

    @Test
    fun setLocationUpdatesTheRowAndWritesExifToTheFile() = runTest {
        val id = repository.add(attachment(fileName = "a.jpg"))

        repository.setLocation(id, 47.5, 11.0)

        val stored = repository.forOwner(AttachmentOwnerType.STATION, 1).single()
        assertEquals(47.5, stored.latitude)
        assertEquals(11.0, stored.longitude)
        assertEquals(listOf(Triple("a.jpg", 47.5, 11.0)), fileStore.locationsWritten)
    }

    @Test
    fun setLocationWithOutOfRangeCoordinatesThrows() = runTest {
        val id = repository.add(attachment())

        val thrown = runCatching { repository.setLocation(id, 200.0, 11.0) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertTrue(fileStore.locationsWritten.isEmpty())
    }

    @Test
    fun setLocationForUnknownIdIsANoOp() = runTest {
        repository.setLocation(id = 999, latitude = 47.5, longitude = 11.0)

        assertTrue(fileStore.locationsWritten.isEmpty())
    }
}
