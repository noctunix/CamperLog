package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.xml.parsers.DocumentBuilderFactory

/** GPX-1.1-Export der Stationen einer Tour (siehe `TourGpx.kt`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class TourGpxTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun station(
        name: String = "",
        notes: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        time: LocalTime? = null,
    ) = Station(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 10),
        time = time,
        name = name,
        notes = notes,
        latitude = latitude,
        longitude = longitude,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun withoutAnyCoordinates_returnsNull() {
        val gpx = tourGpx(resources, listOf(station(name = "Keine Koordinaten")))

        assertNull(gpx)
    }

    @Test
    fun onlyStationsWithCoordinatesBecomeWaypoints() {
        val stations = listOf(
            station(name = "Ohne Koordinaten"),
            station(name = "Strandbad", latitude = 47.6, longitude = 9.5),
            station(name = "Hafen", latitude = 54.3, longitude = 10.1, time = LocalTime.of(18, 30)),
        )

        val gpx = tourGpx(resources, stations)!!
        val document = parse(gpx)

        val waypoints = document.getElementsByTagName("wpt")
        assertEquals(2, waypoints.length)
        assertEquals("47.6", waypoints.item(0).attributes.getNamedItem("lat").textContent)
        assertEquals("9.5", waypoints.item(0).attributes.getNamedItem("lon").textContent)
    }

    @Test
    fun nameAndNotesAreXmlEscapedAndRoundTripCleanly() {
        val evil = """<wpt>&"'"""
        val stations = listOf(station(name = evil, notes = evil, latitude = 47.6, longitude = 9.5))

        val gpx = tourGpx(resources, stations)!!

        assertEquals(false, gpx.contains("<wpt>&\"'"))
        val document = parse(gpx)
        val waypoint = document.getElementsByTagName("wpt").item(0)
        val name = (waypoint as org.w3c.dom.Element).getElementsByTagName("name").item(0).textContent
        val desc = waypoint.getElementsByTagName("desc").item(0).textContent
        assertEquals(evil, name)
        assertEquals(evil, desc)
    }

    @Test
    fun timeIsFormattedAsIso8601WithZSuffix() {
        val stations = listOf(station(name = "Hafen", latitude = 54.3, longitude = 10.1, time = LocalTime.of(18, 30, 0)))

        val gpx = tourGpx(resources, stations)!!

        assertEquals(true, gpx.contains("<time>2026-07-10T18:30:00Z</time>"))
    }

    private fun parse(xml: String): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
}
