package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.amountToDecimal
import app.restvolt.camperlog.domain.sumMinor
import java.time.LocalTime

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
    "fahrzeug",
)

/**
 * Erzeugt eine CSV-Datei nach RFC 4180 (Komma, CRLF) mit Kopfzeile.
 * Datumswerte sind ISO-8601, Kosten exakte Dezimalzahlen mit Punkt. `kosten_eur` enthält nur den
 * Euro-Anteil, `kosten` alle Beträge mit ISO-Code, z. B. `120.00 EUR; 1450.00 NOK; 3500 ISK`.
 * Die Stellplatz-Spalten `stellplatz_zugewiesen` … `keile_genutzt` kommen aus der ersten
 * Übernachtungs-Station jeder Tour nach Datum ([firstOvernightStationsByTour]); ohne eine solche
 * Station bleiben sie leer. `fahrzeug` enthält den Anzeigenamen des Fahrzeugs aus [vehicleNames];
 * ein leeres oder fehlendes Fahrzeug ergibt [defaultVehicleName]. Freitextfelder werden per
 * [neutralizeFormula] gegen Formel-Injection entschärft.
 */
fun toursToCsv(tours: List<Tour>, stations: List<Station>, vehicleNames: Map<Long, String>, defaultVehicleName: String): String {
    val firstOvernightStation = firstOvernightStationsByTour(stations)
    return buildString {
        appendCsvRow(CSV_HEADER)
        tours.forEach { appendCsvRow(it.csvFields(firstOvernightStation[it.id], vehicleNames, defaultVehicleName)) }
    }
}

/** Je Tour die früheste Übernachtungs-Station nach `(date, time NULLS LAST, createdAt)`. */
fun firstOvernightStationsByTour(stations: List<Station>): Map<Long, Station> = stations
    .asSequence()
    .filter { it.type == StationType.OVERNIGHT && it.tourId != null }
    .groupBy { it.tourId as Long }
    .mapValues { (_, candidates) -> candidates.minWith(compareBy({ it.date }, { it.time ?: LocalTime.MAX }, { it.createdAt })) }

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

private fun Tour.csvFields(station: Station?, vehicleNames: Map<Long, String>, defaultVehicleName: String): List<String> = listOf(
    id.toString(),
    startDate.toString(),
    endDate.toString(),
    neutralizeFormula(destination),
    tourType.csvValue,
    travelDays.toString(),
    overnightStays.toString(),
    distanceKm.toString(),
    amountToDecimal(costs.filter { it.currency == EUR }.sumMinor(), EUR),
    yesNoOrEmpty(station?.pitchAssigned),
    station?.electricityFlatRate?.csvValue.orEmpty(),
    station?.lteQuality?.csvValue.orEmpty(),
    station?.pitchSlope?.csvValue.orEmpty(),
    yesNoOrEmpty(station?.levelingBlocksUsed),
    neutralizeFormula(notes),
    neutralizeFormula(mapLink.orEmpty()),
    createdAt.toString(),
    updatedAt.toString(),
    costs.joinToString("; ") { "${amountToDecimal(it.minor, it.currency)} ${it.currency.currencyCode}" },
    neutralizeFormula(vehicleNames[vehicleId]?.takeIf(String::isNotBlank) ?: defaultVehicleName),
)

private fun yesNoOrEmpty(value: Boolean?) = when (value) {
    true -> "ja"
    false -> "nein"
    null -> ""
}
