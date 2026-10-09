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
}
