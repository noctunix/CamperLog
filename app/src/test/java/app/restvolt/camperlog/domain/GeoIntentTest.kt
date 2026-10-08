package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoIntentTest {

    @Test
    fun bareCoordinatesAreRecognized() {
        assertEquals(GeoIntentLocation(68.0912, 13.1023), parseGeoIntent("geo:68.0912,13.1023"))
    }

    @Test
    fun queryCoordinatesWithLabelTakePriorityOverPlaceholderBase() {
        assertEquals(
            GeoIntentLocation(68.0912, 13.1023, "Camping Moskenes"),
            parseGeoIntent("geo:0,0?q=68.0912,13.1023(Camping Moskenes)"),
        )
    }

    @Test
    fun queryCoordinatesWithoutLabel() {
        assertEquals(GeoIntentLocation(68.0912, 13.1023), parseGeoIntent("geo:0,0?q=68.0912,13.1023"))
    }

    @Test
    fun freeTextQueryYieldsLabelOnly() {
        assertEquals(GeoIntentLocation(label = "Moskenes Norwegen"), parseGeoIntent("geo:0,0?q=Moskenes+Norwegen"))
    }

    @Test
    fun placeholderWithoutQueryIsUnusable() {
        assertNull(parseGeoIntent("geo:0,0"))
    }

    @Test
    fun nonGeoLinkIsNotParsed() {
        assertNull(parseGeoIntent("https://example.org"))
        assertNull(parseGeoIntent(null))
    }

    @Test
    fun outOfRangeCoordinatesAreRejected() {
        assertNull(parseGeoIntent("geo:95,13"))
    }

    @Test
    fun zoomParameterIsIgnored() {
        assertEquals(GeoIntentLocation(68.0912, 13.1023), parseGeoIntent("geo:68.0912,13.1023?z=14"))
    }

    @Test
    fun overlongLabelsAreTruncatedToTheStationNameLimit() {
        val long = "a".repeat(MAX_STATION_NAME_LENGTH + 100)

        assertEquals(MAX_STATION_NAME_LENGTH, parseGeoIntent("geo:0,0?q=68.1,13.1($long)")?.label?.length)
        assertEquals(MAX_STATION_NAME_LENGTH, parseGeoIntent("geo:0,0?q=$long")?.label?.length)
    }

    @Test
    fun truncationDoesNotSplitASurrogatePair() {
        val label = "a".repeat(MAX_STATION_NAME_LENGTH - 1) + "\uD83D\uDE90"

        assertEquals("a".repeat(MAX_STATION_NAME_LENGTH - 1), parseGeoIntent("geo:0,0?q=$label")?.label)
    }
}
