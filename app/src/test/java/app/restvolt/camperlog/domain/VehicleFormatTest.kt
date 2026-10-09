package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VehicleFormatTest {

    private val de = Locale.GERMANY

    @Test
    fun formatCmGroupsThousands() {
        assertEquals("636 cm", formatCm(636, de))
        assertEquals("1.250 cm", formatCm(1250, de))
    }

    @Test
    fun kwToPsRoundsCommercially() {
        assertEquals(163, kwToPs(120))
        assertEquals(1, kwToPs(1))
        assertEquals(0, kwToPs(0))
    }

    @Test
    fun formatPowerCombinesKwAndDerivedPs() {
        assertEquals("120 kW (163 PS)", formatPower(120, de))
    }

    @Test
    fun formatApproxPsShowsOnlyTheDerivedValue() {
        assertEquals("≈ 163 PS", formatApproxPs(120, de))
    }
}
