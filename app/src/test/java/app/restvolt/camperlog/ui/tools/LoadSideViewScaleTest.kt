package app.restvolt.camperlog.ui.tools

import app.restvolt.camperlog.domain.MAX_LOAD_POSITION_CM
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadSideViewScaleTest {

    @Test
    fun extentUsesRealLengthAndFrontOverhangWhenBothAreKnown() {
        val extent = loadSideViewExtent(wheelbaseCm = 400, lengthCm = 700, frontOverhangCm = 100)

        assertEquals(-100, extent.startCm)
        assertEquals(600, extent.endCm)
    }

    @Test
    fun extentFallsBackToAProportionalEstimateWithoutLengthData() {
        val extent = loadSideViewExtent(wheelbaseCm = 400, lengthCm = null, frontOverhangCm = null)

        assertEquals(-80, extent.startCm)
        assertEquals(620, extent.endCm)
    }

    @Test
    fun cmToPxMapsTheExtentOntoTheFullWidth() {
        val extent = loadSideViewExtent(wheelbaseCm = 400, lengthCm = 700, frontOverhangCm = 100)
        val scale = LoadSideViewScale(extent, widthPx = 700f)

        assertEquals(0f, scale.cmToPx(-100), 0.001f)
        assertEquals(700f, scale.cmToPx(600), 0.001f)
        assertEquals(350f, scale.cmToPx(250), 0.001f)
    }

    @Test
    fun cmToPxClampsPositionsOutsideTheExtent() {
        val extent = loadSideViewExtent(wheelbaseCm = 400, lengthCm = 700, frontOverhangCm = 100)
        val scale = LoadSideViewScale(extent, widthPx = 700f)

        assertEquals(0f, scale.cmToPx(-500), 0.001f)
        assertEquals(700f, scale.cmToPx(2000), 0.001f)
    }

    @Test
    fun pxToCmSnapsToFiveCentimetresAndRoundTripsWithCmToPx() {
        val extent = loadSideViewExtent(wheelbaseCm = 400, lengthCm = 700, frontOverhangCm = 100)
        val scale = LoadSideViewScale(extent, widthPx = 700f)

        assertEquals(250, scale.pxToCm(scale.cmToPx(250)))
        assertEquals(250, scale.pxToCm(352f))
        assertEquals(360, scale.pxToCm(458f))
    }

    @Test
    fun pxToCmClampsToTheValidPositionRange() {
        val extent = loadSideViewExtent(wheelbaseCm = 2400, lengthCm = null, frontOverhangCm = null)
        val scale = LoadSideViewScale(extent, widthPx = 100f)

        assertEquals(MAX_LOAD_POSITION_CM, scale.pxToCm(100_000f))
    }
}
