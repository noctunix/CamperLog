package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
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
class RoomChecklistRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomChecklistRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var vehicleId = 0L
    private var otherVehicleId = 0L
    private var tourId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomChecklistRepository(db) { now }
        kotlinx.coroutines.runBlocking {
            vehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
            otherVehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-2", createdAtMillis = 0, updatedAtMillis = 0))
            tourId = RoomTourRepository(db).save(
                Tour(
                    vehicleId = vehicleId,
                    startDate = LocalDate.of(2026, 7, 1),
                    endDate = LocalDate.of(2026, 7, 10),
                    destination = "Lofoten",
                    tourType = TourType.VACATION,
                    travelDays = 10,
                    overnightStays = 9,
                    distanceKm = 2000,
                    costs = emptyList(),
                    notes = "",
                    mapLink = null,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun checklist(title: String = "Abfahrt", tourId: Long? = null) = Checklist(
        vehicleId = vehicleId,
        tourId = tourId,
        title = title,
        items = listOf(ChecklistItem("Dachluken schließen"), ChecklistItem("Trittstufe einfahren", checked = true)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun saveSetsUuidAndTimestampsAndUpdateKeepsCreatedAt() = runTest {
        val id = repository.save(checklist())
        val stored = checkNotNull(repository.allChecklists().firstOrNull { it.id == id })
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(4, UUID.fromString(stored.uuid).version())

        val created = now
        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.save(stored.copy(items = stored.items.map { it.copy(checked = true) }))
        val updated = checkNotNull(repository.allChecklists().first { it.id == id })
        assertEquals(true, updated.items.all { it.checked })
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun itemOrderAndCheckedStateArePreserved() = runTest {
        val id = repository.save(checklist())
        val stored = checkNotNull(repository.allChecklists().firstOrNull { it.id == id })
        assertEquals(
            listOf(ChecklistItem("Dachluken schließen"), ChecklistItem("Trittstufe einfahren", checked = true)),
            stored.items,
        )
    }

    @Test
    fun observeForTourOrdersNewestFirst() = runTest {
        now = Instant.parse("2026-01-01T10:00:00Z")
        repository.save(checklist(title = "Älteste", tourId = tourId))
        now = Instant.parse("2026-01-02T10:00:00Z")
        repository.save(checklist(title = "Neueste", tourId = tourId))

        assertEquals(listOf("Neueste", "Älteste"), repository.observeForTour(tourId).first().map { it.title })
    }

    @Test
    fun observeForVehicleWithoutTourExcludesChecklistsOfATour() = runTest {
        repository.save(checklist(title = "Einwintern"))
        repository.save(checklist(title = "Abfahrt", tourId = tourId))

        assertEquals(listOf("Einwintern"), repository.observeForVehicleWithoutTour(vehicleId).first().map { it.title })
    }

    @Test
    fun restoreBringsBackADeletedChecklistWithItsOriginalIdAndItems() = runTest {
        val id = repository.save(checklist())
        val stored = checkNotNull(repository.allChecklists().first { it.id == id })
        repository.delete(id)

        repository.restore(stored)

        assertEquals(listOf(stored), repository.allChecklists())
    }

    @Test
    fun deletingTheTourCascadesToItsChecklists() = runTest {
        repository.save(checklist(tourId = tourId))
        repository.save(checklist(title = "Einwintern"))

        db.tourDao().deleteById(tourId)

        assertEquals(listOf("Einwintern"), repository.allChecklists().map { it.title })
    }

    @Test
    fun deletingTheVehicleCascadesToItsChecklists() = runTest {
        // otherVehicleId hat keine Tour, die den Fremdschlüssel-RESTRICT auf vehicles greifen ließe.
        repository.save(checklist().copy(vehicleId = otherVehicleId))

        db.vehicleDao().deleteById(otherVehicleId)

        assertEquals(emptyList<Checklist>(), repository.allChecklists())
    }

    @Test
    fun moveTourToVehicleMovesOnlyChecklistsOfThatTourAndLeavesOthersUntouched() = runTest {
        val tourChecklistId = repository.save(checklist(title = "Abfahrt", tourId = tourId))
        val standaloneId = repository.save(checklist(title = "Einwintern"))

        repository.moveTourToVehicle(tourId, otherVehicleId)

        val moved = checkNotNull(repository.allChecklists().first { it.id == tourChecklistId })
        val untouched = checkNotNull(repository.allChecklists().first { it.id == standaloneId })
        assertEquals(otherVehicleId, moved.vehicleId)
        assertEquals(vehicleId, untouched.vehicleId)
    }
}
