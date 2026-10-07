package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.ui.labelRes
import java.time.format.DateTimeFormatter

private val GPX_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/**
 * Baut GPX 1.1 aus den Wegpunkten aller [stations] mit Koordinaten und dem aufgezeichneten
 * [track] (ein `<trk>`, je Segment ein `<trkseg>`); `null`, wenn es weder Koordinaten noch
 * Trackpunkte gibt. [Station.time] ist Ortszeit ohne bekannte Zeitzone und wird deshalb ohne
 * Zonenangabe geschrieben (gültiges `xsd:dateTime`); ein `Z` würde die Wegpunkte um Stunden
 * verschieben. Trackpunkte haben echte Zeitpunkte und werden in UTC mit `Z` geschrieben.
 */
fun tourGpx(res: Resources, stations: List<Station>, track: List<TrackPoint> = emptyList(), trackName: String = ""): String? {
    val withCoordinates = stations.filter { it.latitude != null && it.longitude != null }
    if (withCoordinates.isEmpty() && track.isEmpty()) return null
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="CamperLog" xmlns="http://www.topografix.com/GPX/1/1">""")
        withCoordinates.forEach { station -> append(waypoint(res, station)) }
        if (track.isNotEmpty()) append(trackElement(track, trackName))
        appendLine("</gpx>")
    }
}

/** GPX-Schema verlangt `<wpt>` vor `<trk>`; Segmente und Punkte aufsteigend sortiert. */
private fun trackElement(track: List<TrackPoint>, name: String): String = buildString {
    appendLine("  <trk>")
    if (name.isNotBlank()) appendLine("    <name>${xmlEscape(name)}</name>")
    track.groupBy { it.segment }.toSortedMap().values.forEach { segment ->
        appendLine("    <trkseg>")
        segment.sortedBy { it.recordedAt }.forEach { point ->
            appendLine("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
            point.altitudeM?.let { appendLine("        <ele>$it</ele>") }
            appendLine("        <time>${DateTimeFormatter.ISO_INSTANT.format(point.recordedAt)}</time>")
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
    }
    appendLine("  </trk>")
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
