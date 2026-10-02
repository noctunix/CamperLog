package app.restvolt.camperlog.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Sprache aller Datumsangaben. Die App hat nur deutsche Texte, daher folgen Datum und Kalender
 * nicht der Gerätesprache, sonst stünde z. B. `May 3, 2026` neben deutschen Beschriftungen.
 */
val DATE_LOCALE: Locale = Locale.GERMANY

/** Formatiert [date] im mittleren deutschen Datumsformat, z. B. `03.05.2026`. */
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(DATE_LOCALE).format(date)

/** Zeitraum der Tour, z. B. `03.05.2026 – 05.05.2026` oder nur ein Datum bei Tagestouren. */
fun Tour.period(): String =
    if (startDate == endDate) {
        formatDate(startDate)
    } else {
        "${formatDate(startDate)} – ${formatDate(endDate)}"
    }
