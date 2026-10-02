package de.hannes.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourTotals
import de.hannes.camperlog.domain.TourType
import de.hannes.camperlog.domain.YearTotals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomTourRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomTourRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTourRepository(db.tourDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertReadUpdateDelete() = runTest {
        val id = repository.save(tour(start = "2026-05-01", destination = "Gardasee"))
        val stored = checkNotNull(repository.observeTour(id).first())
        assertEquals("Gardasee", stored.destination)
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(tour(start = "2026-05-01", destination = "Gardasee").copy(id = id, createdAt = now, updatedAt = now), stored)

        val created = now
        now = Instant.parse("2026-02-01T12:00:00Z")
        repository.save(stored.copy(destination = "Comer See", mapLink = null, levelingBlocksUsed = true))
        val updated = checkNotNull(repository.observeTour(id).first())
        assertEquals("Comer See", updated.destination)
        assertNull(updated.mapLink)
        assertEquals(true, updated.levelingBlocksUsed)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)

        repository.delete(id)
        assertNull(repository.observeTour(id).first())
        assertEquals(emptyList<Tour>(), repository.observeTours().first())
    }

    @Test
    fun restoreBringsBackDeletedTourUnchanged() = runTest {
        val id = repository.save(tour(start = "2026-05-01", destination = "Gardasee"))
        val stored = checkNotNull(repository.observeTour(id).first())
        repository.delete(id)

        now = Instant.parse("2026-03-01T08:00:00Z")
        repository.restore(stored)

        assertEquals(stored, repository.observeTour(id).first())
    }

    @Test
    fun listIsNewestFirstAndExportOldestFirst() = runTest {
        repository.save(tour(start = "2025-08-01", destination = "B"))
        repository.save(tour(start = "2026-03-01", destination = "C"))
        repository.save(tour(start = "2024-06-01", destination = "A"))

        assertEquals(listOf("C", "B", "A"), repository.observeTours().first().map { it.destination })
        assertEquals(listOf("A", "B", "C"), repository.allTours().map { it.destination })
    }

    @Test
    fun totalsAreZeroWithoutTours() = runTest {
        assertEquals(TourTotals(0, 0, 0, 0, 0), repository.observeTotals().first())
        assertEquals(emptyList<YearTotals>(), repository.observeYearTotals().first())
    }

    @Test
    fun aggregatesTotalsAndYearsDescending() = runTest {
        repository.save(tour(start = "2025-07-01", km = 300, days = 3, nights = 2, cents = 10_050))
        repository.save(tour(start = "2025-12-30", km = 200, days = 4, nights = 3, cents = 5_000))
        repository.save(tour(start = "2026-04-10", km = 150, days = 2, nights = 1, cents = 2_599))
        repository.save(tour(start = "2023-01-01", km = 50, days = 1, nights = 0, cents = 0))

        assertEquals(TourTotals(4, 700, 10, 6, 17_649), repository.observeTotals().first())
        assertEquals(
            listOf(
                YearTotals(2026, TourTotals(1, 150, 2, 1, 2_599)),
                YearTotals(2025, TourTotals(2, 500, 7, 5, 15_050)),
                YearTotals(2023, TourTotals(1, 50, 1, 0, 0)),
            ),
            repository.observeYearTotals().first(),
        )
    }

    private fun tour(
        start: String,
        destination: String = "Ziel",
        km: Int = 100,
        days: Int = 2,
        nights: Int = 1,
        cents: Long = 1_000,
    ): Tour {
        val startDate = LocalDate.parse(start)
        return Tour(
            startDate = startDate,
            endDate = startDate.plusDays(days - 1L),
            destination = destination,
            tourType = TourType.VACATION,
            travelDays = days,
            overnightStays = nights,
            distanceKm = km,
            costCents = cents,
            pitchAssigned = true,
            electricityFlatRate = ElectricityFlatRate.YES,
            lteQuality = LteQuality.OK,
            pitchSlope = PitchSlope.SLOPED,
            levelingBlocksUsed = false,
            notes = "Notiz, mit \"Zeichen\"",
            mapLink = "https://maps.app.goo.gl/xyz",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
