package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.ui.labelRes
import java.time.format.DateTimeFormatter

private val GPX_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

/**
 * Baut die GPX-1.1-Wegpunkte aller [stations] mit Koordinaten; `null`, wenn keine Station welche
 * hat (die ZIP-Datei lässt `Tour.gpx` dann weg). [Station.time] ist eine Wanduhrzeit ohne
 * verlässliche Zeitzone (siehe [app.restvolt.camperlog.domain.Station]) und wird trotzdem mit
 * `Z`-Suffix geschrieben, da GPX sonst keinen gültigen Zeitstempel akzeptiert.
 */
fun tourGpx(res: Resources, stations: List<Station>): String? {
    val withCoordinates = stations.filter { it.latitude != null && it.longitude != null }
    if (withCoordinates.isEmpty()) return null
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="CamperLog" xmlns="http://www.topografix.com/GPX/1/1">""")
        withCoordinates.forEach { station -> append(waypoint(res, station)) }
        appendLine("</gpx>")
    }
}

private fun waypoint(res: Resources, station: Station): String = buildString {
    appendLine("""  <wpt lat="${station.latitude}" lon="${station.longitude}">""")
    station.time?.let { time -> appendLine("    <time>${station.date.atTime(time).format(GPX_TIME_FORMAT)}</time>") }
    appendLine("    <name>${xmlEscape(station.name.ifBlank { res.getString(station.type.labelRes) })}</name>")
    if (station.notes.isNotBlank()) appendLine("    <desc>${xmlEscape(station.notes)}</desc>")
    appendLine("    <type>${xmlEscape(res.getString(station.type.labelRes))}</type>")
    appendLine("  </wpt>")
}

/** XML-escaped [text]: `&`, `<`, `>`, `"` und `'`. */
internal fun xmlEscape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
