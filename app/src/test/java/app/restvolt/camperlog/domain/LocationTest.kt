package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTest {

    @Test
    fun selectBestLastKnown_picksTheMostAccurateCandidateWithinMaxAge() {
        val candidates = listOf(
            LocationCandidate(ageMillis = 5_000, accuracyMeters = 40f),
            LocationCandidate(ageMillis = 2_000, accuracyMeters = 8f),
            LocationCandidate(ageMillis = 10_000, accuracyMeters = 200f),
        )

        assertEquals(1, selectBestLastKnown(candidates, maxAgeMillis = 60_000))
    }

    @Test
    fun selectBestLastKnown_ignoresCandidatesOlderThanMaxAge() {
        val candidates = listOf(
            LocationCandidate(ageMillis = 2 * LOCATION_LAST_KNOWN_MAX_AGE_MS, accuracyMeters = 1f),
            LocationCandidate(ageMillis = 1_000, accuracyMeters = 50f),
        )

        assertEquals(1, selectBestLastKnown(candidates))
    }

    @Test
    fun selectBestLastKnown_returnsMinusOneWhenNothingIsFreshEnough() {
        val candidates = listOf(LocationCandidate(ageMillis = LOCATION_LAST_KNOWN_MAX_AGE_MS + 1, accuracyMeters = 1f))

        assertEquals(-1, selectBestLastKnown(candidates))
    }

    @Test
    fun selectBestLastKnown_returnsMinusOneForNoCandidates() {
        assertEquals(-1, selectBestLastKnown(emptyList()))
    }

    @Test
    fun isApproximateFix_isFalseForNullOrPreciseAccuracy() {
        assertFalse(isApproximateFix(null))
        assertFalse(isApproximateFix(8))
        assertFalse(isApproximateFix(LOCATION_APPROXIMATE_THRESHOLD_M - 1))
    }

    @Test
    fun isApproximateFix_isTrueAtOrAboveTheThreshold() {
        assertTrue(isApproximateFix(LOCATION_APPROXIMATE_THRESHOLD_M))
        assertTrue(isApproximateFix(3_000))
    }
}
