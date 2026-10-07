package app.restvolt.camperlog.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val EPOCH_DATE: LocalDate = LocalDate.of(2026, 7, 1)

private fun overnightStationAt(lat: Double, lon: Double) = Station(
    vehicleId = 1,
    type = StationType.OVERNIGHT,
    date = EPOCH_DATE,
    latitude = lat,
    longitude = lon,
    coordinateSource = CoordinateSource.ENTERED,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

private fun stationWithoutCoordinates() = Station(
    vehicleId = 1,
    type = StationType.SIGHT,
    date = EPOCH_DATE,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

private fun tollStation(kind: TollKind, country: String) = Station(
    vehicleId = 1,
    type = StationType.TOLL,
    date = EPOCH_DATE,
    tollKind = kind,
    tollCountry = country,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

/** Aggregation der Länder einer Tour: Erkennung aus Koordinaten/Vignetten plus manuelle Anpassungen. */
class TourCountriesTest {

    private val lookup = FakeCountryLookupRepository(
        mapOf((48.0 to 8.2) to "DE", (45.6 to 10.68) to "IT"),
    )

    @Test
    fun autoDetectedCountriesCombinesCoordinateLookupAndVignettesButNotOtherTollKinds() = runTest {
        val stations = listOf(
            overnightStationAt(48.0, 8.2),
            overnightStationAt(45.6, 10.68),
            stationWithoutCoordinates(),
            tollStation(TollKind.VIGNETTE, "CH"),
            tollStation(TollKind.MOTORWAY, "AT"),
        )

        assertEquals(setOf("DE", "IT", "CH"), autoDetectedCountries(stations, lookup))
    }

    @Test
    fun autoDetectedCountriesIgnoresCoordinatesWithoutAMatch() = runTest {
        val stations = listOf(overnightStationAt(56.0, 3.0))

        assertEquals(emptySet<String>(), autoDetectedCountries(stations, lookup))
    }

    @Test
    fun tourCountriesUnionsAutoDetectedAndManuallyAddedMinusManuallyRemoved() {
        val result = tourCountries(
            autoDetected = setOf("DE", "IT"),
            manuallyAdded = setOf("FR"),
            manuallyRemoved = setOf("IT"),
        )

        assertEquals(setOf("DE", "FR"), result)
    }

    @Test
    fun tourCountriesByYearGroupsByTourStartYearAndIgnoresUnmappedStations() = runTest {
        val tour2026 = tour(id = 1, year = 2026, manuallyAdded = setOf("FR"))
        val tour2027 = tour(id = 2, year = 2027, manuallyRemoved = setOf("DE"))
        val stationsByTourId = mapOf(
            1L to listOf(overnightStationAt(48.0, 8.2)),
            2L to listOf(overnightStationAt(48.0, 8.2), overnightStationAt(45.6, 10.68)),
            // Station einer dritten, hier nicht übergebenen Tour: zählt nirgends mit.
            3L to listOf(overnightStationAt(45.6, 10.68)),
        )

        val byYear = tourCountriesByYear(listOf(tour2026, tour2027), stationsByTourId, lookup)

        assertEquals(setOf("DE", "FR"), byYear[2026])
        assertEquals(setOf("IT"), byYear[2027])
        assertEquals(setOf("DE", "FR", "IT"), byYear.totalCountries())
    }

    private fun tour(id: Long, year: Int, manuallyAdded: Set<String> = emptySet(), manuallyRemoved: Set<String> = emptySet()) = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(year, 7, 1),
        endDate = LocalDate.of(year, 7, 10),
        destination = "",
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 0,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        manualCountriesAdded = manuallyAdded,
        manualCountriesRemoved = manuallyRemoved,
    )
}
