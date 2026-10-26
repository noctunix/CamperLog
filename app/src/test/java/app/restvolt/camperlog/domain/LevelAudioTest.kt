package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelAudioTest {

    private fun LevelAudioCue.directionOrNull(): LevelAudioDirection? = (this as? LevelAudioCue.Beep)?.direction

    private fun LevelAudioCue.intervalOrNull(): Long? = (this as? LevelAudioCue.Beep)?.beepIntervalMillis

    @Test
    fun levelAudioCue_isLevelWithinToleranceOnBothAxes() {
        assertEquals(LevelAudioCue.Level, levelAudioCue(Tilt(0.0, 0.0)))
        assertEquals(LevelAudioCue.Level, levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES, -LEVEL_TOLERANCE_DEGREES)))
    }

    @Test
    fun levelAudioCue_mapsPositivePitchToBackAndNegativeToFront() {
        assertEquals(LevelAudioDirection.BACK, levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES + 0.1, 0.0)).directionOrNull())
        assertEquals(LevelAudioDirection.FRONT, levelAudioCue(Tilt(-LEVEL_TOLERANCE_DEGREES - 0.1, 0.0)).directionOrNull())
    }

    @Test
    fun levelAudioCue_mapsPositiveRollToRightAndNegativeToLeft() {
        assertEquals(LevelAudioDirection.RIGHT, levelAudioCue(Tilt(0.0, LEVEL_TOLERANCE_DEGREES + 0.1)).directionOrNull())
        assertEquals(LevelAudioDirection.LEFT, levelAudioCue(Tilt(0.0, -LEVEL_TOLERANCE_DEGREES - 0.1)).directionOrNull())
    }

    @Test
    fun levelAudioCue_picksTheAxisWithTheLargerMagnitudeOnDiagonalTilt() {
        val dominantPitch = levelAudioCue(Tilt(10.0, 5.0))
        assertEquals(LevelAudioDirection.BACK, dominantPitch.directionOrNull())

        val dominantRoll = levelAudioCue(Tilt(5.0, -10.0))
        assertEquals(LevelAudioDirection.LEFT, dominantRoll.directionOrNull())
    }

    @Test
    fun levelAudioCue_usesTheSlowIntervalJustOutsideToleranceAndFastFromDoubleTolerance() {
        val justOutside = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES + 0.1, 0.0))
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, justOutside.intervalOrNull())

        val belowDoubleTolerance = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES * 2 - 0.1, 0.0))
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, belowDoubleTolerance.intervalOrNull())

        val atDoubleTolerance = levelAudioCue(Tilt(LEVEL_TOLERANCE_DEGREES * 2, 0.0))
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, atDoubleTolerance.intervalOrNull())

        val wellBeyondDoubleTolerance = levelAudioCue(Tilt(45.0, 0.0))
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, wellBeyondDoubleTolerance.intervalOrNull())
    }

    @Test
    fun levelAudioCue_respectsACustomTolerance() {
        assertEquals(LevelAudioCue.Level, levelAudioCue(Tilt(4.0, 0.0), toleranceDegrees = 5.0))
        assertEquals(LevelAudioDirection.BACK, levelAudioCue(Tilt(4.0, 0.0), toleranceDegrees = 3.0).directionOrNull())
    }
}
