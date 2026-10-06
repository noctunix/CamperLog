package app.restvolt.camperlog.domain

import java.time.LocalDate
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

/** Display a range or just one date for a day trip. */
fun Tour.period(locale: Locale = Locale.getDefault()): String =
    if (startDate == endDate) {
        formatDate(startDate, locale)
    } else {
        "${formatDate(startDate, locale)} – ${formatDate(endDate, locale)}"
    }
