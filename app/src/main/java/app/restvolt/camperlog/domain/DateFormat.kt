package app.restvolt.camperlog.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Use English for unsupported device languages, matching the default Android resources. */
fun supportedLocale(locale: Locale): Locale =
    if (locale.language == "de" || locale.language == "en") locale else Locale.US

/** Format dates in the language of the UI. */
fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(supportedLocale(locale)).format(date)

/** Month and year in upper case for a sticky list header, e.g. "JULY 2026" / "JULI 2026". */
fun formatMonthYear(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    val resolved = supportedLocale(locale)
    return DateTimeFormatter.ofPattern("LLLL yyyy", resolved).format(date).uppercase(resolved)
}

/** Time of day in the device's time zone and the language of the UI, e.g. "14:05" or "2:05 PM". */
fun formatTimeOfDay(atMillis: Long, locale: Locale = Locale.getDefault()): String {
    val time = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(supportedLocale(locale)).format(time)
}

/** Display a range or just one date for a day trip. */
fun Tour.period(locale: Locale = Locale.getDefault()): String =
    if (endDate == null || startDate == endDate) {
        formatDate(startDate, locale)
    } else {
        "${formatDate(startDate, locale)} – ${formatDate(endDate, locale)}"
    }
