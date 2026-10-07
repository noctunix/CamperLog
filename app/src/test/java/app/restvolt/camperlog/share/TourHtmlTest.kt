package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** HTML-Export einer Tour (`Tour.html`, siehe `TourHtml.kt`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class TourHtmlTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val tour = Tour(
        startDate = LocalDate.of(2026, 7, 10),
        endDate = LocalDate.of(2026, 7, 12),
        destination = "Bodensee",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 412,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(
        name: String = "",
        notes: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
    ) = Station(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 10),
        name = name,
        notes = notes,
        latitude = latitude,
        longitude = longitude,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun userStringsAreHtmlEscaped() {
        val evil = """<script>&"'"""
        val stations = listOf(station(name = evil, notes = evil))

        val html = tourHtml(resources, tour, stations, emptySet(), emptyMap())

        assertFalse("raw markup must not reach the page", html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;&amp;&quot;&#39;"))
    }

    @Test
    fun coordinatesBecomeAnOpenStreetMapLink() {
        val stations = listOf(station(name = "Strandbad", latitude = 47.6, longitude = 9.5))

        val html = tourHtml(resources, tour, stations, emptySet(), emptyMap())

        assertTrue(html.contains("""href="https://www.openstreetmap.org/?mlat=47.6&amp;mlon=9.5#map=15/47.6/9.5""""))
    }

    @Test
    fun photosAreRenderedWithTheirRelativeZipPath() {
        val station = station(name = "Platz")
        val photosByStation = mapOf(station.id to listOf(TourExportPhoto(attachment(), "Photos/2026-07-10 Platz 1.jpg")))

        val html = tourHtml(resources, tour, listOf(station), emptySet(), photosByStation)

        assertTrue(html.contains("""<img src="Photos/2026-07-10%20Platz%201.jpg" alt="">"""))
    }

    @Test
    fun diaryEntriesAppearAfterTheStopsWithEscapedText() {
        val evil = """<script>&"'"""
        val entry = DiaryEntry(tourId = 1, date = LocalDate.of(2026, 7, 11), text = evil, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

        val html = tourHtml(resources, tour, emptyList(), emptySet(), emptyMap(), listOf(entry))

        assertFalse("raw markup must not reach the page", html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;&amp;&quot;&#39;"))
        assertTrue(html.contains(">Diary<"))
    }

    @Test
    fun printCssAvoidsBreakingAStopAndCapsImageWidth() {
        val html = tourHtml(resources, tour, emptyList(), emptySet(), emptyMap())

        assertTrue(html.contains("break-inside: avoid"))
        assertTrue(html.contains("max-width: 100%"))
    }

    private fun attachment() = app.restvolt.camperlog.domain.Attachment(
        uuid = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1",
        ownerType = app.restvolt.camperlog.domain.AttachmentOwnerType.STATION,
        ownerId = 1,
        fileName = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024,
        createdAt = Instant.EPOCH,
    )
}
