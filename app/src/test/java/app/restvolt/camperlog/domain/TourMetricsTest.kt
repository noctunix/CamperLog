package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TourMetricsTest {

    private val today = LocalDate.of(2026, 7, 10)
    private val tour = Tour(
        id = 1,
        vehicleId = 1,
        startDate = LocalDate.of(2026, 7, 8),
        endDate = null,
        destination = "",
        tourType = TourType.VACATION,
        travelDays = 99,
        overnightStays = 88,
        distanceKm = 77,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun runningTourCountsInclusiveThroughTodayAndSumsOnlyItsOvernightStations() {
        val metrics = tour.derivedMetrics(
            stations = listOf(
                station(tourId = 1, nights = 2),
                station(tourId = 1, nights = null),
                station(tourId = 1, type = StationType.SIGHT, nights = 7),
                station(tourId = 2, nights = 12),
                station(tourId = null, nights = 20),
            ),
            trackPoints = emptyList(),
            today = today,
        )

        assertEquals(TourMetrics(travelDays = 3, overnightStays = 3, distanceKm = 0), metrics)
    }

    @Test
    fun completedTourUsesEndDateAndDoesNotReplaceStoredManualValues() {
        val completed = tour.copy(endDate = LocalDate.of(2026, 7, 9))
        val metrics = completed.derivedMetrics(emptyList(), emptyList(), today)

        assertEquals(TourMetrics(travelDays = 2, overnightStays = 0, distanceKm = 0), metrics)
        assertEquals(99, completed.travelDays)
        assertEquals(88, completed.overnightStays)
        assertEquals(77, completed.distanceKm)
    }

    @Test
    fun futureStartDateForRunningTourStillCountsOneDay() {
        val futureTour = tour.copy(startDate = today.plusDays(4))

        assertEquals(
            1,
            futureTour.derivedMetrics(emptyList(), emptyList(), today).travelDays,
        )
    }

    @Test
    fun trackLengthSumsSegmentsSeparatelyFiltersOtherToursAndRoundsToKilometers() {
        val points = listOf(
            point(tourId = 1, segment = 1, second = 1, latitude = 0.0, longitude = 0.0),
            point(tourId = 1, segment = 1, second = 2, latitude = 0.0, longitude = 0.006),
            point(tourId = 1, segment = 2, second = 3, latitude = 0.0, longitude = 1.0),
            point(tourId = 1, segment = 2, second = 4, latitude = 0.0, longitude = 1.006),
            point(tourId = 2, segment = 1, second = 5, latitude = 0.0, longitude = 0.0),
            point(tourId = 2, segment = 1, second = 6, latitude = 0.0, longitude = 1.0),
        )

        val metrics = tour.derivedMetrics(emptyList(), points, today)

        assertEquals(1, metrics.distanceKm)
        assertEquals(0, tour.derivedMetrics(emptyList(), emptyList(), today).distanceKm)
    }

    private fun station(
        tourId: Long?,
        type: StationType = StationType.OVERNIGHT,
        nights: Int?,
    ) = Station(
        vehicleId = 1,
        tourId = tourId,
        type = type,
        date = today,
        nights = nights,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun point(tourId: Long, segment: Int, second: Long, latitude: Double, longitude: Double) = TrackPoint(
        tourId = tourId,
        segment = segment,
        recordedAt = Instant.ofEpochSecond(second),
        latitude = latitude,
        longitude = longitude,
    )
}
