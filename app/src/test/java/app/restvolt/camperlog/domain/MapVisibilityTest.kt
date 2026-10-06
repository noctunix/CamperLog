package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Sichtbarkeitsregeln der Karte: Schalter aus / keine Koordinaten / teilweise Koordinaten. */
class MapVisibilityTest {

    private fun station(
        id: Long = 0,
        date: LocalDate = LocalDate.of(2026, 7, 4),
        time: LocalTime? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        createdAt: Instant = Instant.EPOCH,
    ) = Station(
        id = id,
        vehicleId = 1,
        type = StationType.SIGHT,
        date = date,
        time = time,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun isMapAvailable_falseWhenTheSwitchIsOffEvenWithCoordinates() {
        val stations = listOf(station(latitude = 68.0912, longitude = 13.1023))
        assertFalse(isMapAvailable(weatherMapEnabled = false, stations = stations))
    }

    @Test
    fun isMapAvailable_falseWhenNoStationHasCoordinates() {
        val stations = listOf(station(), station())
        assertFalse(isMapAvailable(weatherMapEnabled = true, stations = stations))
    }

    @Test
    fun isMapAvailable_trueWithPartialCoordinates() {
        val stations = listOf(station(), station(latitude = 68.0912, longitude = 13.1023))
        assertTrue(isMapAvailable(weatherMapEnabled = true, stations = stations))
    }

    @Test
    fun isMapAvailable_falseForAnEmptyList() {
        assertFalse(isMapAvailable(weatherMapEnabled = true, stations = emptyList()))
    }

    @Test
    fun stationsWithCoordinates_dropsStationsMissingEitherCoordinate() {
        val located = station(id = 1, latitude = 1.0, longitude = 2.0)
        val unlocated = station(id = 2)
        assertEquals(listOf(located), stationsWithCoordinates(listOf(located, unlocated)))
    }

    @Test
    fun stationsForMap_sortsChronologicallyRegardlessOfInputOrder() {
        val first = station(id = 1, date = LocalDate.of(2026, 7, 4), latitude = 1.0, longitude = 1.0)
        val second = station(id = 2, date = LocalDate.of(2026, 7, 4), time = LocalTime.of(18, 0), latitude = 2.0, longitude = 2.0)
        val third = station(id = 3, date = LocalDate.of(2026, 7, 6), latitude = 3.0, longitude = 3.0)
        val unlocated = station(id = 4, date = LocalDate.of(2026, 7, 5))

        // Reihenfolge wie im Stationen-Reiter: neueste zuerst, trotzdem muss die Karte chronologisch verbinden.
        val result = stationsForMap(listOf(third, unlocated, second, first))

        // Stationen ohne Uhrzeit sortieren nach den terminierten des gleichen Tages.
        assertEquals(listOf(second, first, third), result)
    }

    @Test
    fun toLatLon_nullWhenEitherCoordinateIsMissing() {
        assertEquals(null, station(latitude = 1.0, longitude = null).toLatLon())
        assertEquals(null, station(latitude = null, longitude = 1.0).toLatLon())
        assertEquals(LatLon(1.0, 2.0), station(latitude = 1.0, longitude = 2.0).toLatLon())
    }
}
