package app.restvolt.camperlog.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
class RoomDiaryEntryRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomDiaryEntryRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var tourId = 0L
    private var vehicleId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomDiaryEntryRepository(db) { now }
        tourId = kotlinx.coroutines.runBlocking {
            vehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
            RoomTourRepository(db).save(
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

    private fun entry(date: LocalDate = LocalDate.of(2026, 7, 4), text: String = "Langer Tag am Fjord.") = DiaryEntry(
        tourId = tourId,
        date = date,
        text = text,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun saveSetsUuidAndTimestampsAndUpdateKeepsCreatedAt() = runTest {
        val id = repository.save(entry())
        val stored = checkNotNull(repository.observeForTour(tourId).first().firstOrNull { it.id == id })
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(4, UUID.fromString(stored.uuid).version())

        val created = now
        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.save(stored.copy(text = "Überarbeitet"))
        val updated = checkNotNull(repository.observeForTour(tourId).first().first { it.id == id })
        assertEquals("Überarbeitet", updated.text)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun observeForTourOrdersByDate() = runTest {
        repository.save(entry(date = LocalDate.of(2026, 7, 6), text = "Dritter Tag"))
        repository.save(entry(date = LocalDate.of(2026, 7, 4), text = "Erster Tag"))
        repository.save(entry(date = LocalDate.of(2026, 7, 5), text = "Zweiter Tag"))

        assertEquals(
            listOf("Erster Tag", "Zweiter Tag", "Dritter Tag"),
            repository.observeForTour(tourId).first().map { it.text },
        )
    }

    @Test
    fun allEntriesReturnsEntriesOfAllTours() = runTest {
        val otherTourId = RoomTourRepository(db).save(
            Tour(
                vehicleId = vehicleId,
                startDate = LocalDate.of(2027, 1, 1),
                endDate = LocalDate.of(2027, 1, 2),
                destination = "Ostsee",
                tourType = TourType.WEEKEND,
                travelDays = 2,
                overnightStays = 1,
                distanceKm = 100,
                costs = emptyList(),
                notes = "",
                mapLink = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        repository.save(entry())
        repository.save(entry().copy(tourId = otherTourId, date = LocalDate.of(2027, 1, 1)))

        assertEquals(2, repository.allEntries().size)
    }

    @Test
    fun secondEntryForTheSameTourAndDateViolatesTheUniqueIndex() = runTest {
        repository.save(entry(date = LocalDate.of(2026, 7, 4)))

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { repository.save(entry(date = LocalDate.of(2026, 7, 4))) }
        }
    }

    @Test
    fun restoreBringsBackADeletedEntryWithItsOriginalIdAndUuid() = runTest {
        val id = repository.save(entry())
        val stored = checkNotNull(repository.observeForTour(tourId).first().first { it.id == id })
        repository.delete(id)

        repository.restore(stored)

        assertEquals(listOf(stored), repository.observeForTour(tourId).first())
    }

    @Test
    fun deletingTheTourCascadesToItsDiaryEntries() = runTest {
        repository.save(entry())

        db.tourDao().deleteById(tourId)

        assertEquals(emptyList<DiaryEntry>(), repository.allEntries())
    }
}
