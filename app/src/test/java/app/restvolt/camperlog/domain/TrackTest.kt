package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrackTest {

    @Test
    fun parse_fallsBackToFifteenMinutesForUnknownValues() {
        assertEquals(TrackInterval.MINUTES_15, TrackInterval.parse(null))
        assertEquals(TrackInterval.MINUTES_15, TrackInterval.parse("bogus"))
        assertEquals(TrackInterval.SECONDS_30, TrackInterval.parse("SECONDS_30"))
    }

    @Test
    fun samplingConfig_batchesShortIntervalsOnly() {
        val short = TrackSamplingConfig.forInterval(TrackInterval.SECONDS_30)
        assertEquals(30_000L, short.intervalMillis)
        assertEquals(20f, short.minDistanceMeters)
        assertEquals(60_000L, short.maxUpdateDelayMillis)

        val long = TrackSamplingConfig.forInterval(TrackInterval.MINUTES_15)
        assertEquals(900_000L, long.intervalMillis)
        assertEquals(100f, long.minDistanceMeters)
        assertEquals(0L, long.maxUpdateDelayMillis)
    }

    @Test
    fun effectiveInterval_usesTwoMinutesOnlyWhileChargingAndEnabled() {
        assertEquals(TrackInterval.MINUTES_2, effectiveTrackInterval(TrackInterval.MINUTES_15, fasterWhileCharging = true, charging = true))
        assertEquals(TrackInterval.MINUTES_15, effectiveTrackInterval(TrackInterval.MINUTES_15, fasterWhileCharging = true, charging = false))
        assertEquals(TrackInterval.MINUTES_15, effectiveTrackInterval(TrackInterval.MINUTES_15, fasterWhileCharging = false, charging = true))
        assertEquals(TrackInterval.SECONDS_30, effectiveTrackInterval(TrackInterval.SECONDS_30, fasterWhileCharging = true, charging = true))
    }

    @Test
    fun usableFix_rejectsInaccurateFixes() {
        assertTrue(isUsableTrackFix(null))
        assertTrue(isUsableTrackFix(100))
        assertFalse(isUsableTrackFix(101))
    }

    @Test
    fun trackLength_sumsWithinSegmentsButNotAcrossThem() {
        fun point(segment: Int, second: Long, lat: Double) =
            TrackPoint(tourId = 1, segment = segment, recordedAt = Instant.ofEpochSecond(second), latitude = lat, longitude = 10.0)
        // 0.01° Breite sind etwa 1112 m.
        val points = listOf(point(1, 2, 50.01), point(1, 1, 50.0), point(2, 3, 51.0), point(2, 4, 51.01))
        assertEquals(2 * 1111.95, trackLengthMeters(points), 1.0)
        assertEquals(0.0, trackLengthMeters(emptyList()), 0.0)
    }
}
