package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/** Aufgezeichnete Tracks im JSON-Teil einer Sicherung (Formatversion 10): Rundgang und Validierung. */
class BackupTracksTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val tourUuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val start = Instant.parse("2026-07-01T08:00:00Z")

    private fun tour() = Tour(
        uuid = tourUuid,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 10),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun point(segment: Int, minutes: Long, accuracy: Int? = 12, altitude: Int? = 520) = TrackPoint(
        tourId = 0,
        segment = segment,
        recordedAt = start.plusSeconds(minutes * 60),
        latitude = 68.2345678,
        longitude = 14.5678901,
        accuracyM = accuracy,
        altitudeM = altitude,
    )

    private fun track(points: List<TrackPoint> = listOf(point(1, 0), point(1, 15, null, null), point(2, 60))) =
        BackupTrack(tourUuid, points)

    private fun backup(tracks: List<BackupTrack> = listOf(track())) = Backup(
        exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
        mainCurrency = nok,
        rates = emptyList(),
        tours = listOf(tour()),
        tourVehicleUuid = mapOf(tourUuid to vehicleUuid),
        vehicles = listOf(BackupVehicle(Vehicle(uuid = vehicleUuid, name = "Bluebird", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH), emptyList(), emptyList())),
        tracks = tracks,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    @Test
    fun roundTrip_keepsPointsAndSegments() {
        val text = encodeBackup(backup())

        assertTrue(text, "\"1782892800000,68.2345678,14.5678901,12,520\"" in text)
        assertTrue(text, "\"1782893700000,68.2345678,14.5678901,,\"" in text)
        assertEquals(listOf(track()), success(text).tracks)
    }

    @Test
    fun decode_withoutTracks_isEmpty() {
        val text = encodeBackup(backup(tracks = emptyList()))

        assertEquals(emptyList<BackupTrack>(), success(text).tracks)
    }

    @Test
    fun decode_rejectsInvalidPoints() {
        val valid = "1782892800000,68.2345678,14.5678901,12,520"
        listOf(
            "1782892800000,91.0,14.5678901,12,520",
            "1782892800000,68.2345678,181.0,12,520",
            "1782892800000,68.2345678,14.5678901,-1,520",
            "1782892800000,68.2345678,14.5678901,12,99999",
            "1782892800000,NaN,14.5678901,12,520",
            "1782892800000,68.2345678,14.5678901,12",
            "heute,68.2345678,14.5678901,12,520",
        ).forEach { broken ->
            val failure = failure(encodeBackup(backup()).replace(valid, broken))
            assertEquals(broken, BackupError.INVALID_DATA, failure?.error)
            assertEquals(broken, 1, failure?.trackNumber)
        }
    }

    @Test
    fun decode_rejectsUnknownTourAndDuplicates() {
        val unknownTour = track().copy(tourUuid = "ffffffff-ffff-4fff-8fff-ffffffffffff")
        val duplicateTime = track(listOf(point(1, 0), point(2, 0)))
        val badSegment = track(listOf(point(0, 0)))

        listOf(listOf(unknownTour), listOf(track(), track()), listOf(duplicateTime), listOf(badSegment)).forEach { tracks ->
            assertEquals(tracks.toString(), BackupError.INVALID_DATA, failure(encodeBackup(backup(tracks)))?.error)
        }
    }
}
