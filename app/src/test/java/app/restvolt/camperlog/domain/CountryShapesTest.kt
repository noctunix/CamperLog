package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Binärformat und Punkt-in-Land-Test über eine selbst kodierte, kleine Testgeometrie, siehe [decodeCountryShapes]. */
class CountryShapesTest {

    /** Quadrat "AA" plus ein Donut "BB" (Außenring mit einem Loch), wie von `build-country-shapes.py` erzeugt. */
    private val shapes = decodeCountryShapes(
        encode(
            "AA" to listOf(square(0.0, 0.0, 10.0)),
            "BB" to listOf(square(20.0, 20.0, 20.0), square(25.0, 25.0, 10.0)),
        ),
    )

    @Test
    fun pointInsideSquareResolvesToItsCountry() {
        assertEquals("AA", countryAt(5.0, 5.0, shapes))
    }

    @Test
    fun pointOutsideAllCountriesIsNull() {
        assertNull(countryAt(-5.0, -5.0, shapes))
        assertNull(countryAt(50.0, 50.0, shapes))
    }

    @Test
    fun pointInsideOuterRingButOutsideHoleResolvesToTheCountry() {
        // Zwischen Außenring (20..40) und Loch (25..35): z. B. (22, 30).
        assertEquals("BB", countryAt(22.0, 30.0, shapes))
    }

    @Test
    fun pointInsideHoleIsNull() {
        // Even-Odd-Regel: innerhalb von Außenring UND Loch zugleich zählt als außen.
        assertNull(countryAt(30.0, 30.0, shapes))
    }

    @Test
    fun decodeRejectsWrongMagic() {
        val bytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).writeBytes("XXXX")
        }.toByteArray()
        val error = runCatching { decodeCountryShapes(bytes) }.exceptionOrNull()
        assert(error is IllegalStateException) { "expected IllegalStateException, got $error" }
    }

    private fun square(originLat: Double, originLon: Double, size: Double): List<Pair<Double, Double>> = listOf(
        originLat to originLon,
        originLat to originLon + size,
        originLat + size to originLon + size,
        originLat + size to originLon,
        originLat to originLon,
    )

    /** Kodiert [countries] exakt wie `build-country-shapes.py`, ohne Quantisierungsverlust bei den hier genutzten Ganzzahl-Testkoordinaten. */
    private fun encode(vararg countries: Pair<String, List<List<Pair<Double, Double>>>>): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeBytes("CLC1")
        data.writeShort(countries.size)
        for ((code, rings) in countries) {
            data.writeBytes(code)
            data.writeShort(rings.size)
            for (ring in rings) {
                data.writeShort(ring.size)
                for ((lat, lon) in ring) {
                    data.writeInt((lat * 100_000).toInt())
                    data.writeInt((lon * 100_000).toInt())
                }
            }
        }
        return out.toByteArray()
    }
}
