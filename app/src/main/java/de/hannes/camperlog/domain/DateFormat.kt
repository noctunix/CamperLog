package de.hannes.camperlog.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val germanDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/** Formatiert [date] als `dd.MM.yyyy`. */
fun formatDate(date: LocalDate): String = germanDate.format(date)

/** Zeitraum der Tour, z. B. `03.05.2026 – 05.05.2026` oder nur ein Datum bei Tagestouren. */
val Tour.period: String
    get() = if (startDate == endDate) formatDate(startDate) else "${formatDate(startDate)} – ${formatDate(endDate)}"
