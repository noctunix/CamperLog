package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelAudioTest {

    @Test
    fun levelAudioCue_isNullWithinToleranceOnBothAxes() {
        assertNull(levelAudioCue(Tilt(0.0, 0.0)))
        assertNull(levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES, -LEVEL_TOLERANCE_DEGREES)))
    }

    @Test
    fun levelAudioCue_mapsPositivePitchToBackAndNegativeToFront() {
        assertEquals(LevelAudioDirection.BACK, levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES + 0.1, 0.0))?.direction)
        assertEquals(LevelAudioDirection.FRONT, levelAudioCue(Tilt(-LEVEL_TOLERANCE_DEGREES - 0.1, 0.0))?.direction)
    }

    @Test
    fun levelAudioCue_mapsPositiveRollToRightAndNegativeToLeft() {
        assertEquals(LevelAudioDirection.RIGHT, levelAudioCue(Tilt(0.0, LEVEL_TOLERANCE_DEGREES + 0.1))?.direction)
        assertEquals(LevelAudioDirection.LEFT, levelAudioCue(Tilt(0.0, -LEVEL_TOLERANCE_DEGREES - 0.1))?.direction)
    }

    @Test
    fun levelAudioCue_picksTheAxisWithTheLargerMagnitudeOnDiagonalTilt() {
        val dominantPitch = levelAudioCue(Tilt(10.0, 5.0))
        assertEquals(LevelAudioDirection.BACK, dominantPitch?.direction)

        val dominantRoll = levelAudioCue(Tilt(5.0, -10.0))
        assertEquals(LevelAudioDirection.LEFT, dominantRoll?.direction)
    }

    @Test
    fun levelAudioCue_usesTheSlowIntervalJustOutsideToleranceAndFastFromDoubleTolerance() {
        val justOutside = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES + 0.1, 0.0))
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, justOutside?.beepIntervalMillis)

        val belowDoubleTolerance = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES * 2 - 0.1, 0.0))
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, belowDoubleTolerance?.beepIntervalMillis)

        val atDoubleTolerance = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES * 2, 0.0))
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, atDoubleTolerance?.beepIntervalMillis)

        val wellBeyondDoubleTolerance = levelAudioCue(Tilt(45.0, 0.0))
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, wellBeyondDoubleTolerance?.beepIntervalMillis)
    }

    @Test
    fun levelAudioCue_respectsACustomTolerance() {
        assertNull(levelAudioCue(Tilt(4.0, 0.0), toleranceDegrees = 5.0))
        assertEquals(LevelAudioDirection.BACK, levelAudioCue(Tilt(4.0, 0.0), toleranceDegrees = 3.0)?.direction)
    }
}
