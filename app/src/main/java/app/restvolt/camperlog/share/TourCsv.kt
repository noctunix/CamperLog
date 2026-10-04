package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.amountToDecimal
import app.restvolt.camperlog.domain.sumMinor

/** Spaltenreihenfolge des CSV-Exports. Neue Spalten nur am Ende anfügen. */
val CSV_HEADER = listOf(
    "id",
    "startdatum",
    "enddatum",
    "ziel",
    "tourart",
    "reisetage",
    "uebernachtungen",
    "km",
    "kosten_eur",
    "stellplatz_zugewiesen",
    "strompauschale",
    "lte",
    "stellplatz_neigung",
    "keile_genutzt",
    "notizen",
    "kartenlink",
    "angelegt",
    "geaendert",
    "kosten",
)

/**
 * Erzeugt eine CSV-Datei nach RFC 4180 (Komma, CRLF) mit Kopfzeile.
 * Datumswerte sind ISO-8601, Kosten exakte Dezimalzahlen mit Punkt. `kosten_eur` enthält nur den
 * Euro-Anteil, `kosten` alle Beträge mit ISO-Code, z. B. `120.00 EUR; 1450.00 NOK; 3500 ISK`.
 * Freitextfelder werden per [neutralizeFormula] gegen Formel-Injection entschärft.
 */
fun toursToCsv(tours: List<Tour>): String = buildString {
    appendCsvRow(CSV_HEADER)
    tours.forEach { appendCsvRow(it.csvFields()) }
}

/**
 * Setzt [field] in Anführungszeichen, falls es Komma, Semikolon, Anführungszeichen, Zeilenumbruch
 * oder Randleerzeichen enthält. Das Semikolon wird mit gequotet, weil deutsches Excel es als
 * Trennzeichen nutzt; sonst könnte Text nach `;` als eigene (Formel-)Zelle gelesen werden.
 */
fun escapeCsv(field: String): String {
    val needsQuotes = field.any { it in QUOTE_TRIGGERS } ||
        field.trim().length != field.length
    return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

/**
 * Entschärft Freitext gegen CSV-/Formel-Injection in Tabellenkalkulationen:
 * Beginnt [field] (nach führenden Leerzeichen) mit `=`, `+`, `-`, `@`, Tab oder CR,
 * wird ein `'` vorangestellt, sodass der Inhalt als Text statt als Formel gilt.
 */
fun neutralizeFormula(field: String): String {
    val first = field.trimStart(' ').firstOrNull() ?: return field
    return if (first in FORMULA_TRIGGERS) "'$field" else field
}

private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
private val QUOTE_TRIGGERS = setOf(',', ';', '"', '\n', '\r')

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    fields.joinTo(this, separator = ",", transform = ::escapeCsv)
    append("\r\n")
}

private fun Tour.csvFields(): List<String> = listOf(
    id.toString(),
    startDate.toString(),
    endDate.toString(),
    neutralizeFormula(destination),
    tourType.csvValue,
    travelDays.toString(),
    overnightStays.toString(),
    distanceKm.toString(),
    amountToDecimal(costs.filter { it.currency == EUR }.sumMinor(), EUR),
    yesNo(pitchAssigned),
    electricityFlatRate.csvValue,
    lteQuality.csvValue,
    pitchSlope.csvValue,
    yesNo(levelingBlocksUsed),
    neutralizeFormula(notes),
    neutralizeFormula(mapLink.orEmpty()),
    createdAt.toString(),
    updatedAt.toString(),
    costs.joinToString("; ") { "${amountToDecimal(it.minor, it.currency)} ${it.currency.currencyCode}" },
)

private fun yesNo(value: Boolean) = if (value) "ja" else "nein"
