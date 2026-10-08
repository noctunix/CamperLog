package app.restvolt.camperlog.domain

import java.net.URLDecoder

/** Aus einem eingehenden `geo:`-Link gelesener Ort, zum Vorbelegen einer neuen Station. */
data class GeoIntentLocation(val latitude: Double? = null, val longitude: Double? = null, val label: String? = null)

private val GEO_SCHEME = Regex("""(?i)^geo:""")
private val GEO_BASE_COORDINATES = Regex("""(?i)^geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""")
private val GEO_QUERY_COORDINATES = Regex("""(?i)[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)(?:\(([^)]*)\))?""")
private val GEO_QUERY_TEXT = Regex("""(?i)[?&]q=([^&]+)""")

/**
 * Liest Koordinaten und eine Bezeichnung aus einem `geo:`-URI-String, wie er in einer eingehenden
 * `VIEW`-Intent-Data steht: `geo:lat,lon` oder `geo:0,0?q=lat,lon(Label)`; `geo:0,0?q=Suchtext`
 * liefert nur [GeoIntentLocation.label]. `null`, wenn [raw] kein `geo:`-Link ist oder nichts Nutzbares
 * enthält (z. B. der reine Platzhalter `geo:0,0` ohne Anfrageteil). Die Bezeichnung stammt aus einer
 * fremden App und wird auf [MAX_STATION_NAME_LENGTH] Zeichen gekürzt.
 */
fun parseGeoIntent(raw: String?): GeoIntentLocation? {
    if (raw == null || !GEO_SCHEME.containsMatchIn(raw)) return null

    GEO_QUERY_COORDINATES.find(raw)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        if (lat != null && lon != null && lat in LATITUDE_RANGE && lon in LONGITUDE_RANGE) {
            val label = match.groupValues[3].ifEmpty { null }?.let(::decodeLabel)?.ifEmpty { null }
            return GeoIntentLocation(lat, lon, label)
        }
    }
    GEO_QUERY_TEXT.find(raw)?.let { match ->
        val label = decodeLabel(match.groupValues[1])
        if (label.isNotEmpty()) return GeoIntentLocation(label = label)
    }
    GEO_BASE_COORDINATES.find(raw)?.let { match ->
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0) && lat in LATITUDE_RANGE && lon in LONGITUDE_RANGE) {
            return GeoIntentLocation(lat, lon)
        }
    }
    return null
}

private fun decodeLabel(text: String): String {
    val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text).trim()
    if (decoded.length <= MAX_STATION_NAME_LENGTH) return decoded
    val end = if (decoded[MAX_STATION_NAME_LENGTH - 1].isHighSurrogate()) MAX_STATION_NAME_LENGTH - 1 else MAX_STATION_NAME_LENGTH
    return decoded.substring(0, end).trimEnd()
}
