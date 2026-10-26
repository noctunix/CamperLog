package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelTest {

    @Test
    fun calibrated_subtractsTheZeroPointFromBothAxes() {
        val tilt = Tilt(pitchDegrees = 5.0, rollDegrees = -3.0)
        val zero = Tilt(pitchDegrees = 1.0, rollDegrees = -1.0)

        val result = tilt.calibrated(zero)

        assertEquals(4.0, result.pitchDegrees, 0.0)
        assertEquals(-2.0, result.rollDegrees, 0.0)
    }

    @Test
    fun levelStatus_isLevelOnlyWithinToleranceOnBothAxes() {
        assertEquals(LevelStatus.LEVEL, levelStatus(Tilt(0.0, 0.0)))
        assertEquals(LevelStatus.LEVEL, levelStatus(Tilt(LEVEL_TOLERANCE_DEGREES, -LEVEL_TOLERANCE_DEGREES)))
        assertEquals(LevelStatus.TILTED, levelStatus(Tilt(LEVEL_TOLERANCE_DEGREES + 0.1, 0.0)))
        assertEquals(LevelStatus.TILTED, levelStatus(Tilt(0.0, -LEVEL_TOLERANCE_DEGREES - 0.1)))
    }

    @Test
    fun levelStatus_respectsACustomTolerance() {
        assertEquals(LevelStatus.LEVEL, levelStatus(Tilt(4.0, 4.0), toleranceDegrees = 5.0))
        assertEquals(LevelStatus.TILTED, levelStatus(Tilt(4.0, 4.0), toleranceDegrees = 3.0))
    }
}
