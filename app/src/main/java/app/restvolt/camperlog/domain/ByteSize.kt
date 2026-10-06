package app.restvolt.camperlog.domain

import java.text.NumberFormat
import java.util.Locale

private val UNITS = listOf("B", "kB", "MB", "GB")

/** Lesbare Dateigröße in der Gerätesprache, z. B. „12,3 MB“; passt die Einheit automatisch an. */
fun formatByteSize(bytes: Long, locale: Locale): String {
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < UNITS.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = if (unitIndex == 0) 0 else 1
        minimumFractionDigits = 0
    }
    return "${format.format(value)} ${UNITS[unitIndex]}"
}
