package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.supportedLocale

/**
 * Baut die eigenständige HTML-Datei des Tour-Exports (`Tour.html`): eingebettetes CSS, kein
 * JavaScript, keine externen Adressen außer einfachen Links. Jede Station bricht beim Drucken
 * nicht über eine Seite, ihre Fotos bleiben innerhalb der Seitenbreite. [photosByStation] liefert
 * die ZIP-Pfade der Fotos je Station (siehe [buildTourPhotoPaths]); alle Nutzertexte werden
 * HTML-escaped. [diaryEntries] stehen nach den Stationen, aufsteigend nach Datum.
 */
fun tourHtml(
    res: Resources,
    tour: Tour,
    stations: List<Station>,
    countries: Set<String>,
    photosByStation: Map<Long, List<TourExportPhoto>>,
    diaryEntries: List<DiaryEntry> = emptyList(),
): String {
    val summary = tourSummary(res, tour, stations, countries)
    val stops = tourStopExports(res, stations, photosByStation)
    val title = res.getString(R.string.share_subject, summary.destination)
    val locale = supportedLocale(res.configuration.locales[0])
    val language = locale.language
    return buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("""<html lang="$language">""")
        appendLine("<head>")
        appendLine("""<meta charset="UTF-8">""")
        appendLine("<title>${htmlEscape(title)}</title>")
        appendLine("<style>$TOUR_HTML_CSS</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>${htmlEscape(title)}</h1>")
        append(summaryHtml(res, summary))
        appendLine("<h2>${htmlEscape(res.getString(R.string.stations_section_title, summary.stopCount))}</h2>")
        if (stops.isEmpty()) {
            appendLine("<p>${htmlEscape(res.getString(R.string.stations_timeline_empty))}</p>")
        } else {
            appendLine("""<ol class="stops">""")
            stops.forEach { append(stopHtml(it)) }
            appendLine("</ol>")
        }
        if (diaryEntries.isNotEmpty()) {
            appendLine("<h2>${htmlEscape(res.getString(R.string.diary_section_title))}</h2>")
            diaryEntries.sortedBy { it.date }.forEach { entry ->
                appendLine("""<section class="diary-entry">""")
                appendLine("<h3>${htmlEscape(formatDate(entry.date, locale))}</h3>")
                appendLine("<p>${htmlEscape(entry.text)}</p>")
                appendLine("</section>")
            }
        }
        if (tour.notes.isNotBlank()) {
            appendLine("<h2>${htmlEscape(res.getString(R.string.field_notes))}</h2>")
            appendLine("<p>${htmlEscape(tour.notes)}</p>")
        }
        appendLine("</body>")
        appendLine("</html>")
    }
}

private fun summaryHtml(res: Resources, summary: TourSummary): String = buildString {
    appendLine("""<section class="summary">""")
    appendLine("<ul>")
    appendLine("<li>${htmlEscape(res.getString(R.string.share_period, summary.period, summary.tourType))}</li>")
    appendLine("<li>${htmlEscape(summary.tripStats)}</li>")
    appendLine("<li>${htmlEscape(res.getString(R.string.share_cost, summary.totalCosts))}</li>")
    summary.categoryCosts.forEach { (label, amounts) ->
        appendLine("<li>${htmlEscape(res.getString(R.string.station_summary_field, label, amounts))}</li>")
    }
    if (summary.countryNames.isNotEmpty()) {
        val names = summary.countryNames.joinToString(", ")
        appendLine("<li>${htmlEscape(res.getString(R.string.station_summary_field, res.getString(R.string.tour_section_countries), names))}</li>")
    }
    if (summary.wouldReturnNames.isNotEmpty()) {
        val names = summary.wouldReturnNames.joinToString(", ")
        appendLine("<li>${htmlEscape(res.getString(R.string.station_summary_field, res.getString(R.string.station_would_return), names))}</li>")
    }
    appendLine("</ul>")
    appendLine("</section>")
}

private fun stopHtml(stop: TourStopExport): String = buildString {
    appendLine("""<li class="stop">""")
    appendLine("<h3>${htmlEscape(stop.heading)}</h3>")
    appendLine("<p>${htmlEscape(stop.typeAndTime)}</p>")
    stop.address?.let { appendLine("<p>${htmlEscape(it)}</p>") }
    if (stop.osmLink != null && stop.coordinatesText != null) {
        appendLine("""<p><a href="${htmlEscape(stop.osmLink)}">${htmlEscape(stop.coordinatesText)}</a></p>""")
    }
    stop.costsLine?.let { appendLine("<p>${htmlEscape(it)}</p>") }
    stop.notesLine?.let { appendLine("<p>${htmlEscape(it)}</p>") }
    stop.weatherLine?.let { appendLine("<p>${htmlEscape(it)}</p>") }
    stop.pitchDetailsLine?.let { appendLine("<p>${htmlEscape(it)}</p>") }
    stop.photos.forEach { photo -> appendLine("""<img src="${htmlEscape(photo.relativeUrl)}" alt="">""") }
    appendLine("</li>")
}

/** HTML-escaped [text]: `&`, `<`, `>`, `"` und `'`, für Text- wie Attributinhalte. */
internal fun htmlEscape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private val TOUR_HTML_CSS = """
    body { font-family: -apple-system, Roboto, Segoe UI, Helvetica, Arial, sans-serif; max-width: 720px; margin: 0 auto; padding: 16px; color: #1a1a1a; background: #fff; }
    h1, h2, h3 { line-height: 1.25; }
    .summary ul { list-style: none; padding: 0; }
    .summary li { padding: 2px 0; border-bottom: 1px solid #e0e0e0; }
    ol.stops { list-style: none; padding: 0; }
    .stop { border-top: 1px solid #ccc; padding: 12px 0; break-inside: avoid; page-break-inside: avoid; }
    .stop img { max-width: 100%; height: auto; display: block; margin-top: 8px; }
    a { color: #0d47a1; }
    @media print {
        body { max-width: none; }
        .stop { break-inside: avoid; page-break-inside: avoid; }
        img { max-width: 100%; }
    }
""".trimIndent()
