package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsLogRedactorTest {

    @Test
    fun redactsCoordinateLikeDecimals() {
        val text = "Fix: lat=48.137154, lon=11.576124"
        assertEquals("Fix: lat=[COORD], lon=[COORD]", DiagnosticsLogRedactor.redact(text))
    }

    @Test
    fun redactsAmountsAfterTheCurrencySign() {
        assertEquals("Kosten: [AMOUNT]", DiagnosticsLogRedactor.redact("Kosten: 12,34 €"))
    }

    @Test
    fun redactsAmountsBeforeTheCurrencySign() {
        assertEquals("Kosten: [AMOUNT]", DiagnosticsLogRedactor.redact("Kosten: €12.34"))
    }

    @Test
    fun redactsLicensePlates() {
        assertEquals("Fahrzeug [PLATE] eingetragen", DiagnosticsLogRedactor.redact("Fahrzeug M AB 1234 eingetragen"))
    }

    @Test
    fun redactsPathsWithARealUserName() {
        assertEquals("Datei: [PATH]", DiagnosticsLogRedactor.redact("Datei: /storage/emulated/0/Download/foto.jpg"))
    }

    @Test
    fun leavesHarmlessTextUnchanged() {
        val text = "Fix akzeptiert: time=1760000000000, accuracy=12, segmentPointCount=4"
        assertEquals(text, DiagnosticsLogRedactor.redact(text))
    }
}
