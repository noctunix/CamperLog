package app.restvolt.camperlog.domain

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.math.cos
import kotlin.math.hypot

/** Mikrograd je Grad (1e-5°, rund 1,1 m am Äquator), die Quantisierung von `countries.bin`. */
private const val COORD_SCALE = 100_000.0

/** Erwartete Kennung am Dateianfang von `countries.bin`. */
private const val MAGIC = "CLC1"

/** Kilometer je Breitengrad, wie in `build-country-shapes.py`'s lokaler äquirechteckiger Projektion. */
private const val KM_PER_DEGREE = 111.32

/** Toleranz für die Grenznähe-Zuordnung in [countryAt], wenn kein Polygon den Punkt einschließt. */
private const val NEARBY_BORDER_TOLERANCE_KM = 5.0

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
 * ISO-3166-1-alpha-2-Code des Landes, in dem ([latitude], [longitude]) liegt, oder dessen das
 * nächstgelegene, wenn kein Polygon den Punkt einschließt, aber eine Landgrenze innerhalb von
 * [NEARBY_BORDER_TOLERANCE_KM] liegt (siehe [nearestCountryWithin]); sonst `null`. Prüft je Land
 * zuerst die Bounding-Box seiner Ringe, dann per Ray-Casting die Ringe selbst; eine ungerade Anzahl
 * umschließender Ringe gilt als "innerhalb" (die Even-Odd-Regel deckt damit Multi-Polygone und
 * Löcher ab, ohne die Ringrichtung zu prüfen). Der Toleranzbereich federt Punkte ab, die wegen der
 * vereinfachten Geometrien (siehe `build-country-shapes.py`) knapp außerhalb aller Polygone liegen,
 * etwa Küstenorte direkt am Wasser oder Lücken zwischen benachbarten Landesgrenzen.
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
    return nearestCountryWithin(latitude, longitude, shapes, NEARBY_BORDER_TOLERANCE_KM)
}

/**
 * Ländercode des Landes, dessen nächster Ring-Abschnitt am wenigsten als [toleranceKm] von
 * ([latitude], [longitude]) entfernt ist, oder `null`, wenn kein Ring innerhalb der Toleranz liegt.
 * Rechnet in einer lokal um [latitude] äquirechteckig skalierten Ebene (siehe [KM_PER_DEGREE]), wie
 * `build-country-shapes.py`'s Simplifizierung; für die hier relevanten Distanzen von wenigen
 * Kilometern ist das genau genug. Prüft je Ring zuerst dessen auf [toleranceKm] erweiterte
 * Bounding-Box, um die teurere Abstandsberechnung auf nahegelegene Ringe zu beschränken.
 */
private fun nearestCountryWithin(latitude: Double, longitude: Double, shapes: List<CountryShape>, toleranceKm: Double): String? {
    val lonScale = (KM_PER_DEGREE * cos(Math.toRadians(latitude))).coerceAtLeast(KM_PER_DEGREE * 0.01)
    val latToleranceDeg = toleranceKm / KM_PER_DEGREE
    val lonToleranceDeg = toleranceKm / lonScale
    var bestCode: String? = null
    var bestDistanceKm = Double.MAX_VALUE
    for (shape in shapes) {
        for (ring in shape.rings) {
            if (latitude < ring.minLat - latToleranceDeg || latitude > ring.maxLat + latToleranceDeg ||
                longitude < ring.minLon - lonToleranceDeg || longitude > ring.maxLon + lonToleranceDeg
            ) {
                continue
            }
            val distanceKm = ring.distanceKmTo(latitude, longitude, lonScale)
            if (distanceKm < bestDistanceKm) {
                bestDistanceKm = distanceKm
                bestCode = shape.code
            }
        }
    }
    return bestCode.takeIf { bestDistanceKm <= toleranceKm }
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

/** Kürzeste Entfernung in Kilometern von ([lat], [lon]) zu einem Segment dieses Rings, siehe [nearestCountryWithin]. */
private fun CountryRing.distanceKmTo(lat: Double, lon: Double, lonScale: Double): Double {
    val py = lat * KM_PER_DEGREE
    val px = lon * lonScale
    var best = Double.MAX_VALUE
    var j = points.size - 1
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        val distance = distancePointToSegmentKm(
            px, py,
            a.longitude * lonScale, a.latitude * KM_PER_DEGREE,
            b.longitude * lonScale, b.latitude * KM_PER_DEGREE,
        )
        if (distance < best) best = distance
        j = i
    }
    return best
}

/** Abstand des Punkts ([px], [py]) zum Segment von ([ax], [ay]) nach ([bx], [by]), alle in derselben Längeneinheit. */
private fun distancePointToSegmentKm(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
