package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/** Streaming-ZIP-Export einer Tour (siehe `TourExportZip.kt`): Eintragsliste und Fotoinhalt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class TourExportZipTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val tour = Tour(
        id = 1,
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

    private fun station(id: Long, name: String, latitude: Double? = null, longitude: Double? = null) = Station(
        id = id,
        vehicleId = 1,
        tourId = tour.id,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 10),
        name = name,
        latitude = latitude,
        longitude = longitude,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun attachment(uuid: String, fileName: String) = Attachment(
        uuid = uuid,
        ownerType = AttachmentOwnerType.STATION,
        ownerId = 0,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = 0,
        createdAt = Instant.EPOCH,
    )

    private fun writeZip(
        stations: List<Station>,
        photosByStation: Map<Long, List<Attachment>> = emptyMap(),
        diaryEntries: List<DiaryEntry> = emptyList(),
        photoContent: (String) -> ByteArray? = { null },
    ): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        writeTourExportZip(
            output = output,
            res = resources,
            tour = tour,
            stations = stations,
            countries = emptySet(),
            diaryEntries = diaryEntries,
            photosByStation = photosByStation,
            tourNames = mapOf(tour.id to tour.destination),
            vehicleNames = emptyMap(),
            defaultVehicleName = "My motorhome",
            vocabulary = CsvVocabulary.ENGLISH,
            photoContent = { fileName -> photoContent(fileName)?.let { ByteArrayInputStream(it) } },
        )
        return readZip(output.toByteArray())
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    @Test
    fun tourWithPhotosOnTwoStops_getsUniqueNamesWithIdenticalBytes() {
        val photoA = "A".repeat(10).toByteArray()
        val photoB = "B".repeat(10).toByteArray()
        val stationOne = station(1, "Platz", 47.6, 9.5)
        val stationTwo = station(2, "Platz", 47.7, 9.6) // same name/date as stationOne: forces a disambiguating suffix
        val photosByStation = mapOf(
            stationOne.id to listOf(attachment("photo-a", "a.jpg")),
            stationTwo.id to listOf(attachment("photo-b", "b.jpg")),
        )

        val entries = writeZip(
            stations = listOf(stationOne, stationTwo),
            photosByStation = photosByStation,
            photoContent = { fileName -> if (fileName == "a.jpg") photoA else if (fileName == "b.jpg") photoB else null },
        )

        val photoEntries = entries.keys.filter { it.startsWith("Photos/") }
        assertEquals(2, photoEntries.size)
        assertEquals(2, photoEntries.toSet().size)
        assertEquals(photoA.toList(), entries.getValue(photoEntries.first { it.endsWith("1.jpg") }).toList())
        assertEquals(photoB.toList(), entries.getValue(photoEntries.first { it.endsWith("2.jpg") }).toList())
    }

    @Test
    fun missingPhotoFile_isSkippedWithoutFailingTheExport() {
        val stationOne = station(1, "Platz")
        val photosByStation = mapOf(stationOne.id to listOf(attachment("photo-a", "missing.jpg")))

        val entries = writeZip(stations = listOf(stationOne), photosByStation = photosByStation, photoContent = { null })

        assertTrue(entries.keys.none { it.startsWith("Photos/") })
        assertTrue(entries.containsKey("Tour.html"))
    }

    @Test
    fun stationsWithoutCoordinates_omitTourGpx() {
        val entries = writeZip(stations = listOf(station(1, "Platz")))

        assertFalse(entries.containsKey("Tour.gpx"))
        assertTrue(entries.containsKey("Tour.html"))
        assertTrue(entries.containsKey("Tour.md"))
        assertTrue(entries.containsKey("Stops.csv"))
    }

    @Test
    fun stationWithCoordinates_includesTourGpx() {
        val entries = writeZip(stations = listOf(station(1, "Platz", 47.6, 9.5)))

        assertTrue(entries.containsKey("Tour.gpx"))
    }

    @Test
    fun tourWithoutStops_stillExportsTheOtherFiles() {
        val entries = writeZip(stations = emptyList())

        assertEquals(setOf("Tour.html", "Tour.md", "Stops.csv"), entries.keys)
        val csv = String(entries.getValue("Stops.csv"), Charsets.UTF_8)
        assertTrue(csv.lines().filter { it.isNotBlank() }.size == 1) // nur die Kopfzeile
    }
}
