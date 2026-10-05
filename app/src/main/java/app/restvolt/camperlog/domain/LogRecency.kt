package app.restvolt.camperlog.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Grobe zeitliche Einordnung eines Bordbuch-Datums gegenüber dem heutigen Tag. */
sealed interface LogRecency {
    data object Today : LogRecency
    data object Yesterday : LogRecency
    data class DaysAgo(val days: Int) : LogRecency
}

/** Ordnet [date] gegenüber [today] ein; negative Werte (zukünftige Daten) zählen wie [LogRecency.DaysAgo]. */
fun logRecency(date: LocalDate, today: LocalDate): LogRecency =
    when (val days = ChronoUnit.DAYS.between(date, today).toInt()) {
        0 -> LogRecency.Today
        1 -> LogRecency.Yesterday
        else -> LogRecency.DaysAgo(days)
    }
