package app.restvolt.camperlog.domain

import java.net.URI
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Ergebnis von [parseLocationText] (5.1). */
sealed interface ParsedLocation {
    /** Koordinaten wurden erkannt. */
    data class Coordinates(val latitude: Double, val longitude: Double) : ParsedLocation

    /** Ein gültiger http(s)-Link ohne erkennbare Koordinaten; wird als `mapLink` gespeichert. */
    data class MapLinkOnly(val link: String) : ParsedLocation

    /** Ein erkannter Kurzlink (z. B. `maps.app.goo.gl`), der offline nicht aufgelöst werden kann. */
    data object ShortLinkUnsupported : ParsedLocation

    /** Weder Koordinaten noch ein verwertbarer Link. */
    data object NotRecognized : ParsedLocation
}

private val DOT_DECIMAL_PAIR = Regex("""^(-?\d{1,3}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)$""")
private val COMMA_DECIMAL_PAIR = Regex("""^(-?\d{1,3},\d+)\s*(?:;\s*|\s+)(-?\d{1,3},\d+)$""")
private val DMS_PAIR = Regex(
    """(\d{1,3})°(\d{1,2})'(\d{1,2}(?:\.\d+)?)"?\s*([NSns])[,\s]+(\d{1,3})°(\d{1,2})'(\d{1,2}(?:\.\d+)?)"?\s*([EWew])""",
)
private val GEO_URI = Regex("""(?i)^geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""")
private val OSM_MLAT_MLON = Regex("""(?:^|[?&])mlat=(-?\d+\.?\d*)""") to Regex("""(?:^|[?&])mlon=(-?\d+\.?\d*)""")
private val OSM_MAP_HASH = Regex("""#map=\d+(?:\.\d+)?/(-?\d+\.?\d*)/(-?\d+\.?\d*)""")
private val GENERIC_LAT_LON = Regex("""(?:^|[?&])lat=(-?\d+\.?\d*)""") to Regex("""(?:^|[?&])lon=(-?\d+\.?\d*)""")
private val GOOGLE_AT = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")
private val GOOGLE_QUERY = Regex("""(?:^|[?&])q=(-?\d+\.?\d*),(-?\d+\.?\d*)""")

private val SHORT_LINK_HOSTS = setOf("maps.app.goo.gl", "goo.gl")

/**
 * Liest Koordinaten offline aus eingefügtem Text: Dezimalpaare (auch mit deutschem Komma), DMS,
 * `geo:`-URIs sowie OSM-, OsmAnd- und Google-Maps-Links mit Koordinaten (5.1). Organic Maps teilt
 * Standorte über dieselben `geo:`-URIs wie OsmAnd und braucht daher kein eigenes Muster.
 * Links ohne erkennbare Koordinaten ergeben [ParsedLocation.MapLinkOnly], sofern es ein gültiger
 * http(s)-Link ist; erkannte Kurzlinks ergeben [ParsedLocation.ShortLinkUnsupported].
 */
fun parseLocationText(text: String): ParsedLocation {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedLocation.NotRecognized

    decimalPair(trimmed)?.let { return it }
    dmsPair(trimmed)?.let { return it }
    geoUri(trimmed)?.let { return it }
    linkCoordinates(trimmed)?.let { return it }

    if (isWebUrl(trimmed)) {
        val host = runCatching { URI(trimmed).host }.getOrNull()?.lowercase()
        return if (host in SHORT_LINK_HOSTS) ParsedLocation.ShortLinkUnsupported else ParsedLocation.MapLinkOnly(trimmed)
    }
    return ParsedLocation.NotRecognized
}

private fun decimalPair(text: String): ParsedLocation? {
    DOT_DECIMAL_PAIR.matchEntire(text)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return validCoordinates(lat, lon)
    }
    COMMA_DECIMAL_PAIR.matchEntire(text)?.let { match ->
        val lat = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        return validCoordinates(lat, lon)
    }
    return null
}

private fun dmsPair(text: String): ParsedLocation? {
    val match = DMS_PAIR.find(text) ?: return null
    val (latDeg, latMin, latSec, latHemi, lonDeg, lonMin, lonSec, lonHemi) = match.destructured
    val lat = dmsToDecimal(latDeg, latMin, latSec, latHemi.uppercase() == "S")
    val lon = dmsToDecimal(lonDeg, lonMin, lonSec, lonHemi.uppercase() == "W")
    return validCoordinates(lat, lon)
}

private fun dmsToDecimal(degrees: String, minutes: String, seconds: String, negative: Boolean): Double {
    val value = degrees.toDouble() + minutes.toDouble() / 60.0 + seconds.toDouble() / 3600.0
    return if (negative) -value else value
}

private fun geoUri(text: String): ParsedLocation? {
    val match = GEO_URI.find(text) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return validCoordinates(lat, lon)
}

/** OSM-, OsmAnd- und Google-Maps-Links mit Koordinaten in der URL. */
private fun linkCoordinates(text: String): ParsedLocation? {
    OSM_MAP_HASH.find(text)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return validCoordinates(lat, lon)
    }
    val mlat = OSM_MLAT_MLON.first.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val mlon = OSM_MLAT_MLON.second.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    if (mlat != null && mlon != null) return validCoordinates(mlat, mlon)

    GOOGLE_AT.find(text)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return validCoordinates(lat, lon)
    }
    GOOGLE_QUERY.find(text)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return validCoordinates(lat, lon)
    }
    val lat = GENERIC_LAT_LON.first.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val lon = GENERIC_LAT_LON.second.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    if (lat != null && lon != null) return validCoordinates(lat, lon)
    return null
}

private fun validCoordinates(latitude: Double, longitude: Double): ParsedLocation? =
    if (latitude in LATITUDE_RANGE && longitude in LONGITUDE_RANGE) ParsedLocation.Coordinates(latitude, longitude) else null

/**
 * Lesbare Darstellung von Koordinaten, z. B. „68,0912° N · 13,1023° E" (6.6). Die Himmelsrichtungen
 * bleiben sprachunabhängig N/S/E/W; nur das Zahlenformat richtet sich nach [locale].
 */
fun formatCoordinates(latitude: Double, longitude: Double, locale: Locale): String {
    val lat = "${formatDegrees(abs(latitude), locale)}° ${if (latitude < 0) "S" else "N"}"
    val lon = "${formatDegrees(abs(longitude), locale)}° ${if (longitude < 0) "W" else "E"}"
    return "$lat · $lon"
}

/** Betrag von [latitude]/[longitude] auf vier Nachkommastellen, lokalisiertes Dezimaltrennzeichen. */
fun formatDegrees(value: Double, locale: Locale): String =
    DecimalFormat("0.0000", DecimalFormatSymbols.getInstance(locale)).format(value)
