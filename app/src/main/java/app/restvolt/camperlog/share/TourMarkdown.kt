package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour

/**
 * Baut die Markdown-Datei des Tour-Exports (`Tour.md`), inhaltsgleich mit [tourHtml]; Fotos stehen
 * als relative Bildlinks in die Export-ZIP. [photosByStation] wie bei [tourHtml].
 */
fun tourMarkdown(res: Resources, tour: Tour, stations: List<Station>, countries: Set<String>, photosByStation: Map<Long, List<TourExportPhoto>>): String {
    val summary = tourSummary(res, tour, stations, countries)
    val stops = tourStopExports(res, stations, photosByStation)
    return buildString {
        appendLine("# ${res.getString(R.string.share_subject, summary.destination)}")
        appendLine()
        appendLine("- ${res.getString(R.string.share_period, summary.period, summary.tourType)}")
        appendLine("- ${summary.tripStats}")
        appendLine("- ${res.getString(R.string.share_cost, summary.totalCosts)}")
        summary.categoryCosts.forEach { (label, amounts) -> appendLine("- ${res.getString(R.string.station_summary_field, label, amounts)}") }
        if (summary.countryNames.isNotEmpty()) {
            val names = summary.countryNames.joinToString(", ")
            appendLine("- ${res.getString(R.string.station_summary_field, res.getString(R.string.tour_section_countries), names)}")
        }
        if (summary.wouldReturnNames.isNotEmpty()) {
            val names = summary.wouldReturnNames.joinToString(", ")
            appendLine("- ${res.getString(R.string.station_summary_field, res.getString(R.string.station_would_return), names)}")
        }
        appendLine()
        appendLine("## ${res.getString(R.string.stations_section_title, summary.stopCount)}")
        if (stops.isEmpty()) {
            appendLine()
            appendLine(res.getString(R.string.stations_timeline_empty))
        } else {
            stops.forEach { append(stopMarkdown(it)) }
        }
        if (tour.notes.isNotBlank()) {
            appendLine()
            appendLine("## ${res.getString(R.string.field_notes)}")
            appendLine()
            appendLine(tour.notes)
        }
    }.trimEnd() + "\n"
}

private fun stopMarkdown(stop: TourStopExport): String = buildString {
    appendLine()
    appendLine("### ${stop.heading}")
    appendLine()
    appendLine(stop.typeAndTime)
    stop.address?.let { appendLine(); appendLine(it) }
    if (stop.osmLink != null && stop.coordinatesText != null) {
        appendLine()
        appendLine("[${stop.coordinatesText}](${stop.osmLink})")
    }
    stop.costsLine?.let { appendLine(); appendLine(it) }
    stop.notesLine?.let { appendLine(); appendLine(it) }
    stop.weatherLine?.let { appendLine(); appendLine(it) }
    stop.pitchDetailsLine?.let { appendLine(); appendLine(it) }
    stop.photos.forEach { photo ->
        appendLine()
        appendLine("![](${photo.zipPath})")
    }
}
