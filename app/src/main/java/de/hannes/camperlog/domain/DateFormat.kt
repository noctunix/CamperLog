package de.hannes.camperlog.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formatiert [date] im mittleren Datumsformat von [locale], z. B. `03.05.2026` oder `May 3, 2026`. */
fun formatDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** Zeitraum der Tour, z. B. `03.05.2026 – 05.05.2026` oder nur ein Datum bei Tagestouren. */
fun Tour.period(locale: Locale): String =
    if (startDate == endDate) {
        formatDate(startDate, locale)
    } else {
        "${formatDate(startDate, locale)} – ${formatDate(endDate, locale)}"
    }
