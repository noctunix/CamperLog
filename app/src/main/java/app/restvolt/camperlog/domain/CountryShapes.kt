package app.restvolt.camperlog.domain

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Mikrograd je Grad (1e-5°, rund 1,1 m am Äquator), die Quantisierung von `countries.bin`. */
private const val COORD_SCALE = 100_000.0

/** Erwartete Kennung am Dateianfang von `countries.bin`. */
private const val MAGIC = "CLC1"

/**
 * Ein geschlossener Ring aus Punkten (Breite, Länge in Grad) mit vorab berechneter Bounding-Box
 * für den Schnelltest in [countryAt].
 */
data class CountryRing(val points: List<LatLon>, val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/**
 * Die vereinfachte Fläche eines Landes für die Offline-Ländererkennung: alle Ringe von [code]
 * (Außenränder, Löcher und die Teile eines Multi-Polygons, siehe [decodeCountryShapes]).
 */
data class CountryShape(val code: String, val rings: List<CountryRing>)

/**
 * Liest die von `scripts/build-country-shapes.py` erzeugte Binärdatei `countries.bin`
 * (vereinfachte Natural-Earth-Admin-0-Landesgrenzen, öffentlich, naturalearthdata.com).
 *
 * Format, big-endian:
 * - 4 Bytes ASCII-Kennung `"CLC1"`
 * - UInt16 Länderzahl, danach je Land:
 *   - 2 ASCII-Bytes ISO-3166-1-alpha-2-Code
 *   - UInt16 Ringzahl, danach je Ring:
 *     - UInt16 Punktzahl, danach je Punkt zwei Int32 (big-endian): Breite, Länge in Mikrograd (°×1e5)
 *
 * @param bytes der vollständige Inhalt von `countries.bin`
 * @return die enthaltenen Länderflächen in Dateireihenfolge
 * @throws IllegalStateException wenn [bytes] nicht mit der Kennung `"CLC1"` beginnt
 * @throws java.io.EOFException wenn [bytes] kürzer ist, als der Header ankündigt
 */
fun decodeCountryShapes(bytes: ByteArray): List<CountryShape> =
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val magic = ByteArray(MAGIC.length).also { input.readFully(it) }
        check(magic.decodeToString() == MAGIC) { "countries.bin hat nicht die erwartete Kennung" }
        val countryCount = input.readUnsignedShort()
        List(countryCount) {
            val code = ByteArray(2).also { input.readFully(it) }.decodeToString()
            val ringCount = input.readUnsignedShort()
            val rings = List(ringCount) {
                val pointCount = input.readUnsignedShort()
                val points = List(pointCount) {
                    val lat = input.readInt() / COORD_SCALE
                    val lon = input.readInt() / COORD_SCALE
                    LatLon(lat, lon)
                }
                CountryRing(
                    points = points,
                    minLat = points.minOf(LatLon::latitude),
                    maxLat = points.maxOf(LatLon::latitude),
                    minLon = points.minOf(LatLon::longitude),
                    maxLon = points.maxOf(LatLon::longitude),
                )
            }
            CountryShape(code, rings)
        }
    }

/**
 * ISO-3166-1-alpha-2-Code des Landes, in dem ([latitude], [longitude]) liegt, oder `null` über
 * See oder außerhalb aller [shapes]. Prüft je Land zuerst die Bounding-Box seiner Ringe, dann per
 * Ray-Casting die Ringe selbst; eine ungerade Anzahl umschließender Ringe gilt als "innerhalb" (die
 * Even-Odd-Regel deckt damit Multi-Polygone und Löcher ab, ohne die Ringrichtung zu prüfen). Wegen
 * der vereinfachten Geometrien (siehe `build-country-shapes.py`) ist das Ergebnis nahe an
 * Landesgrenzen nicht immer eindeutig.
 */
fun countryAt(latitude: Double, longitude: Double, shapes: List<CountryShape>): String? {
    for (shape in shapes) {
        var inside = 0
        for (ring in shape.rings) {
            if (latitude < ring.minLat || latitude > ring.maxLat || longitude < ring.minLon || longitude > ring.maxLon) continue
            if (ring.containsRayCast(latitude, longitude)) inside++
        }
        if (inside % 2 == 1) return shape.code
    }
    return null
}

private fun CountryRing.containsRayCast(lat: Double, lon: Double): Boolean {
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.latitude > lat) != (b.latitude > lat)) {
            val lonAtLat = a.longitude + (lat - a.latitude) / (b.latitude - a.latitude) * (b.longitude - a.longitude)
            if (lon < lonAtLat) inside = !inside
        }
        j = i
    }
    return inside
}
