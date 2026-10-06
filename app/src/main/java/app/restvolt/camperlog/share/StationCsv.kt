package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.Station

/** Spaltenreihenfolge des Stationen-CSV-Exports. Neue Spalten nur am Ende anfügen. */
val STATION_CSV_HEADER = listOf(
    "id",
    "datum",
    "uhrzeit",
    "typ",
    "name",
    "ort",
    "breitengrad",
    "laengengrad",
    "koordinatenquelle",
    "genauigkeit_m",
    "kartenlink",
    "naechte",
    "platzart",
    "stellplatz_zugewiesen",
    "strompauschale",
    "lte",
    "neigung",
    "keile_genutzt",
    "versorgung",
    "gerne_wieder",
    "notizen",
    "angelegt",
    "geaendert",
    "tour",
    "fahrzeug",
)

/**
 * Erzeugt eine CSV-Datei nach RFC 4180 (Komma, CRLF) mit Kopfzeile, eine Zeile je Station.
 * `tour` enthält den Zielnamen aus [tourNames] (leer ohne Tour); `fahrzeug` den Anzeigenamen aus
 * [vehicleNames], ein leeres oder fehlendes Fahrzeug ergibt [defaultVehicleName]. Freitextfelder
 * werden per [neutralizeFormula] gegen Formel-Injection entschärft, wie beim Touren-Export.
 */
fun stationsToCsv(
    stations: List<Station>,
    tourNames: Map<Long, String>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
): String = buildString {
    appendCsvRow(STATION_CSV_HEADER)
    stations.forEach { appendCsvRow(it.csvFields(tourNames, vehicleNames, defaultVehicleName)) }
}

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    fields.joinTo(this, separator = ",", transform = ::escapeCsv)
    append("\r\n")
}

private fun Station.csvFields(tourNames: Map<Long, String>, vehicleNames: Map<Long, String>, defaultVehicleName: String): List<String> = listOf(
    id.toString(),
    date.toString(),
    time?.toString().orEmpty(),
    type.csvValue,
    neutralizeFormula(name),
    neutralizeFormula(place),
    latitude?.toString().orEmpty(),
    longitude?.toString().orEmpty(),
    coordinateSource?.csvValue.orEmpty(),
    accuracyM?.toString().orEmpty(),
    neutralizeFormula(mapLink.orEmpty()),
    nights?.toString().orEmpty(),
    siteKind?.csvValue.orEmpty(),
    yesNoOrEmpty(pitchAssigned),
    electricityFlatRate?.csvValue.orEmpty(),
    lteQuality?.csvValue.orEmpty(),
    pitchSlope?.csvValue.orEmpty(),
    yesNoOrEmpty(levelingBlocksUsed),
    services.sortedBy { it.ordinal }.joinToString("; ") { it.csvValue },
    if (favorite) "ja" else "nein",
    neutralizeFormula(notes),
    createdAt.toString(),
    updatedAt.toString(),
    neutralizeFormula(tourId?.let { tourNames[it] }.orEmpty()),
    neutralizeFormula(vehicleNames[vehicleId]?.takeIf(String::isNotBlank) ?: defaultVehicleName),
)

private fun yesNoOrEmpty(value: Boolean?) = when (value) {
    true -> "ja"
    false -> "nein"
    null -> ""
}
