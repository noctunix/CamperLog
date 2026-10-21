package app.restvolt.camperlog.domain

import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CompleteTourTest {

    private val today = LocalDate.of(2026, 7, 10)

    private fun runningTour(startDate: LocalDate) = Tour(
        id = 1,
        vehicleId = 1,
        startDate = startDate,
        endDate = null,
        destination = "",
        tourType = TourType.VACATION,
        travelDays = 0,
        overnightStays = 0,
        distanceKm = 0,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun finishesANormalTourStartedInThePast() = runTest {
        val tours = FakeTourRepository(listOf(runningTour(startDate = today.minusDays(3))))
        val stations = FakeStationRepository()
        val tracks = FakeTrackRepository()

        val result = completeTour(1, today, today, tours, stations, tracks)

        assertTrue(result)
        val finished = checkNotNull(tours.tours.firstOrNull { it.id == 1L })
        assertEquals(today.minusDays(3), finished.startDate)
        assertEquals(today, finished.endDate)
    }

    @Test
    fun finishingATourWithAFutureStartDatePullsTheStartBackToTheChosenEndDate() = runTest {
        val tours = FakeTourRepository(listOf(runningTour(startDate = today.plusDays(4))))
        val stations = FakeStationRepository()
        val tracks = FakeTrackRepository()

        val result = completeTour(1, today, today, tours, stations, tracks)

        assertTrue(result)
        val finished = checkNotNull(tours.tours.firstOrNull { it.id == 1L })
        assertEquals(today, finished.startDate)
        assertEquals(today, finished.endDate)
    }

    @Test
    fun refusesAnEndDateAfterToday() = runTest {
        val tours = FakeTourRepository(listOf(runningTour(startDate = today.minusDays(1))))
        val stations = FakeStationRepository()
        val tracks = FakeTrackRepository()

        val result = completeTour(1, today.plusDays(1), today, tours, stations, tracks)

        assertFalse(result)
        assertEquals(null, tours.tours.first().endDate)
    }
}
