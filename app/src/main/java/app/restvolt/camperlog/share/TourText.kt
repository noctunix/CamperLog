package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour

/**
 * Lesbare Zusammenfassung einer Tour zum Teilen per Messenger oder E-Mail, in der Sprache von [res].
 * [stations] sind die Stationen der Tour, [countries] ihre schon um manuelle Anpassungen bereinigten
 * Länder (siehe [app.restvolt.camperlog.domain.tourCountries]); Kosten-, Länder- und
 * "Gerne wieder"-Logik stecken in [tourSummary], gemeinsam mit dem HTML- und dem Markdown-Export.
 */
fun tourShareText(res: Resources, tour: Tour, stations: List<Station>, countries: Set<String>): String {
    val summary = tourSummary(res, tour, stations, countries)
    return buildString {
        appendLine(res.getString(R.string.share_subject, summary.destination))
        appendLine(res.getString(R.string.share_period, summary.period, summary.tourType))
        appendLine(summary.tripStats)
        appendLine(res.getString(R.string.share_cost, summary.totalCosts))
        summary.categoryCosts.forEach { (label, amounts) -> appendLine(res.getString(R.string.station_summary_field, label, amounts)) }
        if (summary.countryNames.isNotEmpty()) {
            val names = summary.countryNames.joinToString(", ")
            appendLine(res.getString(R.string.station_summary_field, res.getString(R.string.tour_section_countries), names))
        }
        appendLine(res.getString(R.string.stations_section_title, summary.stopCount))
        if (summary.wouldReturnNames.isNotEmpty()) {
            val names = summary.wouldReturnNames.joinToString(", ")
            appendLine(res.getString(R.string.station_summary_field, res.getString(R.string.station_would_return), names))
        }
        if (tour.notes.isNotBlank()) appendLine(res.getString(R.string.share_notes, tour.notes))
        tour.mapLink?.let { appendLine(res.getString(R.string.share_map, it)) }
    }.trimEnd()
}
