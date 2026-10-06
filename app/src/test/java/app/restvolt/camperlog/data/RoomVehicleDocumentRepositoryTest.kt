package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.VehicleDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomVehicleDocumentRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomVehicleDocumentRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var vehicleId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomVehicleDocumentRepository(db) { now }
        vehicleId = kotlinx.coroutines.runBlocking {
            db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun document(title: String = "Fahrzeugschein", expiryDate: LocalDate? = null) = VehicleDocument(
        vehicleId = vehicleId,
        kind = DocumentKind.REGISTRATION,
        title = title,
        expiryDate = expiryDate,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun saveSetsUuidAndTimestampsAndUpdateKeepsCreatedAt() = runTest {
        val id = repository.save(document())
        val stored = checkNotNull(repository.observeForVehicle(vehicleId).first().firstOrNull { it.id == id })
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(4, UUID.fromString(stored.uuid).version())

        val created = now
        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.save(stored.copy(title = "Fahrzeugschein 2"))
        val updated = checkNotNull(repository.observeForVehicle(vehicleId).first().first { it.id == id })
        assertEquals("Fahrzeugschein 2", updated.title)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun observeForVehicleOrdersByExpiryDateWithNullsLast() = runTest {
        repository.save(document(title = "Ohne Ablauf", expiryDate = null))
        repository.save(document(title = "Bald", expiryDate = LocalDate.of(2027, 1, 1)))
        repository.save(document(title = "Später", expiryDate = LocalDate.of(2028, 1, 1)))

        assertEquals(
            listOf("Bald", "Später", "Ohne Ablauf"),
            repository.observeForVehicle(vehicleId).first().map { it.title },
        )
    }

    @Test
    fun allDocumentsReturnsDocumentsOfAllVehicles() = runTest {
        val otherVehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-2", createdAtMillis = 0, updatedAtMillis = 0))
        repository.save(document())
        repository.save(document().copy(vehicleId = otherVehicleId))

        assertEquals(2, repository.allDocuments().size)
    }

    @Test
    fun deleteAlsoDeletesItsAttachments() = runTest {
        val id = repository.save(document())
        db.attachmentDao().insert(
            AttachmentEntity(
                uuid = "att-1",
                ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT.name,
                ownerId = id,
                fileName = "att-1.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 100,
                createdAtMillis = 0,
            ),
        )

        repository.delete(id)

        assertEquals(emptyList<VehicleDocument>(), repository.observeForVehicle(vehicleId).first())
        assertEquals(0, db.attachmentDao().getAll().size)
    }

    @Test
    fun restoreBringsBackADeletedDocumentWithItsOriginalIdAndUuid() = runTest {
        val id = repository.save(document())
        val stored = checkNotNull(repository.observeForVehicle(vehicleId).first().first { it.id == id })
        repository.delete(id)

        repository.restore(stored)

        assertEquals(listOf(stored), repository.observeForVehicle(vehicleId).first())
    }

    @Test
    fun deletingTheVehicleCascadesToItsDocuments() = runTest {
        repository.save(document())

        db.vehicleDao().deleteById(vehicleId)

        assertEquals(emptyList<VehicleDocument>(), repository.allDocuments())
    }
}
