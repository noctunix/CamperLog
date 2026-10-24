package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.BoardType
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.LodgingAmenity
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.labelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Markdown-Export einer Tour (`Tour.md`, siehe `TourMarkdown.kt`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class TourMarkdownTest {

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

    private fun station(name: String = "") = Station(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 10),
        name = name,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun attachment(fileName: String) = Attachment(
        uuid = fileName,
        ownerType = AttachmentOwnerType.STATION,
        ownerId = 1,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = 1024,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun userTextIsEscapedSoItRendersLiterally() {
        val markdown = tourMarkdown(resources, tour.copy(notes = "1. Tag\n- *fett* [x] #1"), listOf(station(name = "Camp_<Nord>")), emptySet(), emptyMap())

        assertTrue(markdown.contains("### Camp\\_\\<Nord\\>"))
        assertTrue(markdown.contains("1\\. Tag\n\\- \\*fett\\* \\[x\\] \\#1"))
    }

    @Test
    fun wifiBoardAndAmenitiesAppearForALodgingStop() {
        val lodgingStation = Station(
            vehicleId = 1,
            type = StationType.OVERNIGHT,
            date = LocalDate.of(2026, 7, 10),
            name = "Seehotel",
            siteKind = SiteKind.HOTEL,
            wifiQuality = LteQuality.GOOD,
            boardType = BoardType.HALF_BOARD,
            lodgingAmenities = setOf(LodgingAmenity.POOL, LodgingAmenity.SAUNA),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val markdown = tourMarkdown(resources, tour, listOf(lodgingStation), emptySet(), emptyMap())

        assertTrue("WLAN quality missing", markdown.contains(resources.getString(LteQuality.GOOD.labelRes)))
        assertTrue("board type missing", markdown.contains(resources.getString(BoardType.HALF_BOARD.labelRes)))
        assertTrue("amenities missing", markdown.contains(resources.getString(LodgingAmenity.POOL.labelRes)))
        assertTrue("amenities missing", markdown.contains(resources.getString(LodgingAmenity.SAUNA.labelRes)))
    }

    @Test
    fun photoPathsWithUmlautsArePercentEncoded() {
        val photo = TourExportPhoto(attachment("c.jpg"), "Photos/2026-07-10 Müritz 1.jpg")

        assertEquals("Photos/2026-07-10%20M%C3%BCritz%201.jpg", photo.relativeUrl)
    }

    @Test
    fun photosBecomeRelativeMarkdownImageLinks() {
        val station = station(name = "Platz")
        val photosByStation = mapOf(
            station.id to listOf(
                TourExportPhoto(attachment("a.jpg"), "Photos/2026-07-10 Platz 1.jpg"),
                TourExportPhoto(attachment("b.jpg"), "Photos/2026-07-10 Platz 2.jpg"),
            ),
        )

        val markdown = tourMarkdown(resources, tour, listOf(station), emptySet(), photosByStation)

        assertTrue(markdown.contains("![](Photos/2026-07-10%20Platz%201.jpg)"))
        assertTrue(markdown.contains("![](Photos/2026-07-10%20Platz%202.jpg)"))
    }

    @Test
    fun summaryAndStopHeadingsUseMarkdownSyntax() {
        val markdown = tourMarkdown(resources, tour, listOf(station(name = "Strandbad")), emptySet(), emptyMap())

        assertTrue(markdown.contains("# Tour to Bodensee"))
        assertTrue(markdown.contains("### Strandbad"))
    }

    @Test
    fun diaryEntriesAppearAfterTheStopsWithEscapedText() {
        val entry = DiaryEntry(tourId = 1, date = LocalDate.of(2026, 7, 11), text = "1. Tag\n- *fett*", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

        val markdown = tourMarkdown(resources, tour, emptyList(), emptySet(), emptyMap(), listOf(entry))

        assertTrue(markdown.contains("## Diary"))
        assertTrue(markdown.contains("1\\. Tag\n\\- \\*fett\\*"))
    }

    @Test
    fun diaryEntryShowsTimeWeatherAndOdometerWhenSet() {
        val entry = DiaryEntry(
            tourId = 1,
            date = LocalDate.of(2026, 7, 11),
            time = LocalTime.of(18, 30),
            text = "Langer Tag",
            weather = "Sonnig, 22 °C",
            odometerKm = 48_300,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val markdown = tourMarkdown(resources, tour, emptyList(), emptySet(), emptyMap(), listOf(entry))

        assertTrue(markdown.contains("18:30"))
        assertTrue(markdown.contains("Weather: Sonnig, 22 °C"))
        assertTrue(markdown.contains("Odometer: 48,300 km"))
    }
}
