package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class CoordinateParserTest(private val input: String, private val expected: ParsedLocation) {

    @Test
    fun parsesAsExpected() {
        assertEquals(input, expected, parseLocationText(input))
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("68.0912, 13.1023", ParsedLocation.Coordinates(68.0912, 13.1023)),
            arrayOf("68.0912 13.1023", ParsedLocation.Coordinates(68.0912, 13.1023)),
            arrayOf("-33.45, -70.66", ParsedLocation.Coordinates(-33.45, -70.66)),
            arrayOf("68,0912; 13,1023", ParsedLocation.Coordinates(68.0912, 13.1023)),
            arrayOf("68,0912;13,1023", ParsedLocation.Coordinates(68.0912, 13.1023)),
            arrayOf("68,0912 13,1023", ParsedLocation.Coordinates(68.0912, 13.1023)),
            arrayOf(
                """68°05'28"N 13°06'08"E""",
                ParsedLocation.Coordinates(68 + 5 / 60.0 + 28 / 3600.0, 13 + 6 / 60.0 + 8 / 3600.0),
            ),
            arrayOf(
                """33°27'00"S 70°40'00"W""",
                ParsedLocation.Coordinates(-(33 + 27 / 60.0 + 0 / 3600.0), -(70 + 40 / 60.0 + 0 / 3600.0)),
            ),
            arrayOf("geo:68.09,13.10", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf("geo:68.09,13.10;u=35", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf(
                "https://www.openstreetmap.org/?mlat=68.09&mlon=13.10#map=15/68.09/13.10",
                ParsedLocation.Coordinates(68.09, 13.10),
            ),
            arrayOf("https://www.openstreetmap.org/#map=15/68.09/13.10", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf("https://osmand.net/go?lat=68.09&lon=13.10&z=15", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf("https://www.google.com/maps/@68.09,13.10,15z", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf("https://www.google.com/maps?q=68.09,13.10", ParsedLocation.Coordinates(68.09, 13.10)),
            arrayOf("https://maps.app.goo.gl/abcDEF", ParsedLocation.ShortLinkUnsupported),
            arrayOf("https://goo.gl/maps/abcDEF", ParsedLocation.ShortLinkUnsupported),
            arrayOf("https://example.org/campingplatz-moskenes", ParsedLocation.MapLinkOnly("https://example.org/campingplatz-moskenes")),
            arrayOf("Campingplatz Moskenes", ParsedLocation.NotRecognized),
            arrayOf("", ParsedLocation.NotRecognized),
            arrayOf("91, 13.10", ParsedLocation.NotRecognized),
            arrayOf("68.09, 181", ParsedLocation.NotRecognized),
        )
    }
}
