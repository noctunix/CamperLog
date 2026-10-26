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

    @Test
    fun levelAudioCue_quadrantSchemeMapsAllFourSignCombinations() {
        assertEquals(
            LevelAudioDirection.FRONT,
            levelAudioCue(Tilt(-10.0, -10.0), scheme = LevelAudioScheme.QUADRANT).directionOrNull(),
        )
        assertEquals(
            LevelAudioDirection.RIGHT,
            levelAudioCue(Tilt(-10.0, 10.0), scheme = LevelAudioScheme.QUADRANT).directionOrNull(),
        )
        assertEquals(
            LevelAudioDirection.BACK,
            levelAudioCue(Tilt(10.0, 10.0), scheme = LevelAudioScheme.QUADRANT).directionOrNull(),
        )
        assertEquals(
            LevelAudioDirection.LEFT,
            levelAudioCue(Tilt(10.0, -10.0), scheme = LevelAudioScheme.QUADRANT).directionOrNull(),
        )
    }

    @Test
    fun levelAudioCue_quadrantSchemeIgnoresWhichAxisIsLargerUnlikeDominantAxis() {
        val dominantAxis = levelAudioCue(Tilt(10.0, 5.0), scheme = LevelAudioScheme.DOMINANT_AXIS)
        assertEquals(LevelAudioDirection.BACK, dominantAxis.directionOrNull())

        val quadrant = levelAudioCue(Tilt(10.0, 5.0), scheme = LevelAudioScheme.QUADRANT)
        assertEquals(LevelAudioDirection.BACK, quadrant.directionOrNull())

        val quadrantNegativeRoll = levelAudioCue(Tilt(5.0, -10.0), scheme = LevelAudioScheme.QUADRANT)
        assertEquals(LevelAudioDirection.LEFT, quadrantNegativeRoll.directionOrNull())
    }

    @Test
    fun levelAudioCue_quadrantSchemeUsesTheEuclideanDistanceForTheInterval() {
        // 2.4/3.2/4.0 ist ein 3-4-5-Dreieck: sqrt(2.4^2 + 3.2^2) = 4.0 = 2 * LEVEL_TOLERANCE_DEGREES.
        val atDoubleTolerance = levelAudioCue(Tilt(2.4, 3.2), scheme = LevelAudioScheme.QUADRANT)
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, atDoubleTolerance.intervalOrNull())

        val belowDoubleToleranceByDistance = levelAudioCue(Tilt(2.3, 3.2), scheme = LevelAudioScheme.QUADRANT)
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, belowDoubleToleranceByDistance.intervalOrNull())

        // Jede Einzelachse liegt unter der doppelten Toleranz, zusammen aber darüber: QUADRANT wird
        // schon schneller, während DOMINANT_AXIS (das nur die größere Achse betrachtet) noch langsam bleibt.
        val quadrant = levelAudioCue(Tilt(3.0, 3.0), scheme = LevelAudioScheme.QUADRANT)
        val dominantAxis = levelAudioCue(Tilt(3.0, 3.0), scheme = LevelAudioScheme.DOMINANT_AXIS)
        assertEquals(LEVEL_AUDIO_FAST_INTERVAL_MILLIS, quadrant.intervalOrNull())
        assertEquals(LEVEL_AUDIO_SLOW_INTERVAL_MILLIS, dominantAxis.intervalOrNull())
    }
}
