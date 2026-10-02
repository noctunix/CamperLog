package de.hannes.camperlog.share

import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.centsToDecimal

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
)

/**
 * Erzeugt eine CSV-Datei nach RFC 4180 (Komma, CRLF) mit Kopfzeile.
 * Datumswerte sind ISO-8601, Kosten eine exakte Dezimalzahl mit Punkt.
 */
fun toursToCsv(tours: List<Tour>): String = buildString {
    appendCsvRow(CSV_HEADER)
    tours.forEach { appendCsvRow(it.csvFields()) }
}

/** Setzt [field] in Anführungszeichen, falls es Komma, Anführungszeichen, Zeilenumbruch oder Randleerzeichen enthält. */
fun escapeCsv(field: String): String {
    val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
        field.trim().length != field.length
    return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    fields.joinTo(this, separator = ",", transform = ::escapeCsv)
    append("\r\n")
}

private fun Tour.csvFields(): List<String> = listOf(
    id.toString(),
    startDate.toString(),
    endDate.toString(),
    destination,
    tourType.label,
    travelDays.toString(),
    overnightStays.toString(),
    distanceKm.toString(),
    centsToDecimal(costCents),
    yesNo(pitchAssigned),
    electricityFlatRate.label,
    lteQuality.label,
    pitchSlope.label,
    yesNo(levelingBlocksUsed),
    notes,
    mapLink.orEmpty(),
    createdAt.toString(),
    updatedAt.toString(),
)

private fun yesNo(value: Boolean) = if (value) "ja" else "nein"
