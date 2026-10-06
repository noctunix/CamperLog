package app.restvolt.camperlog.share

import android.content.Intent
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareIntentsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tour = Tour(
        id = 1,
        startDate = LocalDate.of(2026, 7, 10),
        endDate = LocalDate.of(2026, 7, 12),
        destination = "Lofoten",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 412,
        costs = listOf(Money(8_950, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun webMapLinkIsOpenedFirstAndOnlyAsBrowsable() {
        val intents = mapIntents(tour.copy(mapLink = "https://example.org/karte"))

        assertEquals("https://example.org/karte", intents.first().dataString)
        assertTrue(intents.first().hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(3, intents.size)
    }

    @Test
    fun nonWebMapLinkIsIgnored() {
        val intents = mapIntents(tour.copy(mapLink = "intent://evil#Intent;end"))

        assertEquals(listOf("geo", "https"), intents.map { it.data?.scheme })
    }

    private fun station(
        name: String = "",
        place: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        mapLink: String? = null,
    ) = Station(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        name = name,
        place = place,
        latitude = latitude,
        longitude = longitude,
        mapLink = mapLink,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun stationWithCoordinatesOpensGeoUriWithLabel() {
        val intents = mapIntents(station(name = "Camping Moskenes", latitude = 68.0912, longitude = 13.1023))

        assertEquals("geo", intents.first().data?.scheme)
        assertTrue(intents.first().dataString!!.contains("68.0912,13.1023"))
        assertTrue(intents.first().dataString!!.contains("Camping"))
    }

    @Test
    fun stationWithoutCoordinatesFallsBackToMapLinkThenSearch() {
        val intents = mapIntents(station(name = "Camping Moskenes", mapLink = "https://example.org/platz"))

        assertEquals("https://example.org/platz", intents.first().dataString)
        assertEquals(listOf("https", "geo", "https"), intents.map { it.data?.scheme })
    }

    @Test
    fun stationWithoutCoordinatesOrLinkSearchesByPlace() {
        val intents = mapIntents(station(place = "Moskenes, Norwegen"))

        assertEquals(listOf("geo", "https"), intents.map { it.data?.scheme })
    }

    @Test
    fun exportFileNamesAreUnique() {
        val dir = folder.root

        val first = uniqueFile(dir, "export").apply { writeText("a") }
        val second = uniqueFile(dir, "export")

        assertEquals("export.csv", first.name)
        assertEquals("export-2.csv", second.name)
    }

    @Test
    fun onlyExportsOlderThanOneHourAreDeleted() {
        val now = 10 * 60 * 60 * 1000L
        val old = folder.newFile("old.csv").apply { setLastModified(now - 61 * 60 * 1000L) }
        val fresh = folder.newFile("fresh.csv").apply { setLastModified(now - 5 * 60 * 1000L) }

        deleteOldExports(folder.root, now)

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
