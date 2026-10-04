package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.YearTotals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

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
        assertEquals(4, UUID.fromString(stored.uuid).version())
        assertEquals(
            tour(start = "2026-05-01", destination = "Gardasee").copy(id = id, uuid = stored.uuid, createdAt = now, updatedAt = now),
            stored,
        )

        val created = now
        now = Instant.parse("2026-02-01T12:00:00Z")
        repository.save(stored.copy(destination = "Comer See", mapLink = null, levelingBlocksUsed = true))
        val updated = checkNotNull(repository.observeTour(id).first())
        assertEquals("Comer See", updated.destination)
        assertNull(updated.mapLink)
        assertEquals(true, updated.levelingBlocksUsed)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
        assertEquals(stored.uuid, updated.uuid)

        repository.delete(id)
        assertNull(repository.observeTour(id).first())
        assertEquals(emptyList<Tour>(), repository.observeTours().first())
    }

    @Test
    fun keepsGivenUuidAndGeneratesDistinctOnes() = runTest {
        val given = repository.save(tour(start = "2026-05-01", destination = "A").copy(uuid = "fixed-uuid"))
        val first = repository.save(tour(start = "2026-05-02", destination = "B"))
        val second = repository.save(tour(start = "2026-05-03", destination = "C"))

        assertEquals("fixed-uuid", repository.observeTour(given).first()?.uuid)
        assertNotEquals(repository.observeTour(first).first()?.uuid, repository.observeTour(second).first()?.uuid)
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
    fun costsKeepOrderAndAreReplacedOnUpdate() = runTest {
        val costs = listOf(Money(145_050, NOK), Money(12_000, ISK), eur(2_000))
        val id = repository.save(tour(start = "2026-05-01", costs = costs))
        val stored = checkNotNull(repository.observeTour(id).first())
        assertEquals(costs, stored.costs)
        assertEquals(costs, repository.allTours().single().costs)

        repository.save(stored.copy(costs = listOf(eur(500))))
        assertEquals(listOf(eur(500)), repository.observeTour(id).first()?.costs)

        repository.save(stored.copy(costs = emptyList()))
        assertEquals(emptyList<Money>(), repository.observeTour(id).first()?.costs)
        assertEquals(0, costRows())
    }

    @Test
    fun deletingTourRemovesItsCosts() = runTest {
        val id = repository.save(tour(start = "2026-05-01", costs = listOf(eur(100), Money(200, NOK))))
        repository.save(tour(start = "2026-06-01", costs = listOf(eur(300))))
        repository.delete(id)
        assertEquals(1, costRows())
    }

    @Test
    fun lastUsedCurrencyComesFromMostRecentlyChangedTour() = runTest {
        assertNull(repository.lastUsedCurrency())

        val first = repository.save(tour(start = "2026-05-01", costs = listOf(eur(100), Money(200, NOK))))
        assertEquals(NOK, repository.lastUsedCurrency())

        now = Instant.parse("2026-01-02T10:00:00Z")
        repository.save(tour(start = "2025-01-01", costs = listOf(Money(5_000, ISK))))
        assertEquals(ISK, repository.lastUsedCurrency())

        now = Instant.parse("2026-01-03T10:00:00Z")
        repository.save(checkNotNull(repository.observeTour(first).first()).copy(destination = "Neu"))
        assertEquals(NOK, repository.lastUsedCurrency())

        // Touren ohne Kosten zählen nicht.
        now = Instant.parse("2026-01-04T10:00:00Z")
        repository.save(tour(start = "2026-07-01", costs = emptyList()))
        assertEquals(NOK, repository.lastUsedCurrency())
    }

    private fun costRows(): Int = db.query("SELECT COUNT(*) FROM tour_costs", null).use { it.moveToFirst(); it.getInt(0) }

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
        assertEquals(TourTotals(0, 0, 0, 0, emptyList()), repository.observeTotals().first())
        assertEquals(emptyList<YearTotals>(), repository.observeYearTotals().first())
    }

    @Test
    fun aggregatesTotalsAndYearsDescending() = runTest {
        repository.save(tour(start = "2025-07-01", km = 300, days = 3, nights = 2, costs = listOf(eur(10_050), Money(30_000, NOK))))
        repository.save(tour(start = "2025-12-30", km = 200, days = 4, nights = 3, costs = listOf(eur(5_000))))
        repository.save(tour(start = "2026-04-10", km = 150, days = 2, nights = 1, costs = listOf(Money(1_500, NOK), eur(2_599))))
        repository.save(tour(start = "2023-01-01", km = 50, days = 1, nights = 0, costs = emptyList()))

        assertEquals(TourTotals(4, 700, 10, 6, listOf(eur(17_649), Money(31_500, NOK))), repository.observeTotals().first())
        assertEquals(
            listOf(
                YearTotals(2026, TourTotals(1, 150, 2, 1, listOf(eur(2_599), Money(1_500, NOK)))),
                YearTotals(2025, TourTotals(2, 500, 7, 5, listOf(eur(15_050), Money(30_000, NOK)))),
                YearTotals(2023, TourTotals(1, 50, 1, 0, emptyList())),
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
        costs: List<Money> = listOf(eur(1_000)),
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
            costs = costs,
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

private val NOK: Currency = Currency.getInstance("NOK")
private val ISK: Currency = Currency.getInstance("ISK")

private fun eur(minor: Long) = Money(minor, EUR)
