package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.amountToDecimal
import app.restvolt.camperlog.domain.sumMinor
import app.restvolt.camperlog.domain.totalCosts
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
    "kosten_gesamt",
)

/** Englische Kopfzeile des Touren-CSV-Exports, Spalte für Spalte wie [CSV_HEADER]. */
private val ENGLISH_CSV_HEADER = listOf(
    "id",
    "start_date",
    "end_date",
    "destination",
    "trip_type",
    "travel_days",
    "overnight_stays",
    "distance_km",
    "costs_eur",
    "pitch_assigned",
    "electricity_flat_rate",
    "lte_quality",
    "pitch_slope",
    "leveling_blocks_used",
    "notes",
    "map_link",
    "created_at",
    "updated_at",
    "costs",
    "vehicle",
    "total_costs",
)

/** Kopfzeile des Touren-CSV-Exports in [vocabulary]. */
fun tourCsvHeader(vocabulary: CsvVocabulary): List<String> = when (vocabulary) {
    CsvVocabulary.GERMAN -> CSV_HEADER
    CsvVocabulary.ENGLISH -> ENGLISH_CSV_HEADER
}

/**
 * Erzeugt eine CSV-Datei nach RFC 4180 (Komma, CRLF) mit Kopfzeile, Kopfzeile und Werte in der
 * Sprache von [vocabulary] (Deutsch per Vorgabe, unverändert gegenüber früheren Exporten).
 * Datumswerte sind ISO-8601, Kosten exakte Dezimalzahlen mit Punkt. `kosten_eur` enthält nur den
 * Euro-Anteil, `kosten` alle manuell erfassten Tourkosten mit ISO-Code, z. B.
 * `120.00 EUR; 1450.00 NOK; 3500 ISK`; `kosten_gesamt` zusätzlich dazu die Kosten aller Stationen der
 * Tour (inklusive abgeleiteter Stromkosten), im selben Format. Die Stellplatz-Spalten
 * `stellplatz_zugewiesen` … `keile_genutzt` kommen aus der ersten Übernachtungs-Station jeder Tour
 * nach Datum ([firstOvernightStationsByTour]); ohne eine solche Station bleiben sie leer. `fahrzeug`
 * enthält den Anzeigenamen des Fahrzeugs aus [vehicleNames]; ein leeres oder fehlendes Fahrzeug
 * ergibt [defaultVehicleName]. Freitextfelder werden per [neutralizeFormula] gegen Formel-Injection
 * entschärft.
 */
fun toursToCsv(
    tours: List<Tour>,
    stations: List<Station>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
    vocabulary: CsvVocabulary = CsvVocabulary.GERMAN,
): String {
    val firstOvernightStation = firstOvernightStationsByTour(stations)
    val stationsByTour = stations.filter { it.tourId != null }.groupBy { it.tourId as Long }
    return buildString {
        appendCsvRow(tourCsvHeader(vocabulary))
        tours.forEach { tour ->
            appendCsvRow(
                tour.csvFields(firstOvernightStation[tour.id], stationsByTour[tour.id].orEmpty(), vehicleNames, defaultVehicleName, vocabulary),
            )
        }
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

private fun Tour.csvFields(
    station: Station?,
    tourStations: List<Station>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
    vocabulary: CsvVocabulary,
): List<String> =
    listOf(
        id.toString(),
        startDate.toString(),
        endDate.toString(),
        neutralizeFormula(destination),
        tourType.csvValue(vocabulary),
        travelDays.toString(),
        overnightStays.toString(),
        distanceKm.toString(),
        amountToDecimal(costs.filter { it.currency == EUR }.sumMinor(), EUR),
        yesNoOrEmpty(station?.pitchAssigned, vocabulary),
        station?.electricityBilling?.let(::legacyFlatRate)?.csvValue(vocabulary).orEmpty(),
        station?.lteQuality?.csvValue(vocabulary).orEmpty(),
        station?.pitchSlope?.csvValue(vocabulary).orEmpty(),
        yesNoOrEmpty(station?.levelingBlocksUsed, vocabulary),
        neutralizeFormula(notes),
        neutralizeFormula(mapLink.orEmpty()),
        createdAt.toString(),
        updatedAt.toString(),
        costs.joinToString("; ") { "${amountToDecimal(it.minor, it.currency)} ${it.currency.currencyCode}" },
        neutralizeFormula(vehicleNames[vehicleId]?.takeIf(String::isNotBlank) ?: defaultVehicleName),
        totalCosts(tourStations).joinToString("; ") { "${amountToDecimal(it.minor, it.currency)} ${it.currency.currencyCode}" },
    )

private fun yesNoOrEmpty(value: Boolean?, vocabulary: CsvVocabulary) = when (value) {
    true -> vocabulary.yes
    false -> vocabulary.no
    null -> ""
}

/**
 * Die Spalte `strompauschale` behält ihr bisheriges Vokabular (ja/nein/nicht genutzt), damit Tabellen,
 * die ältere Exporte auswerten, weiter funktionieren; die genaue Abrechnungsart steht in der Stations-CSV.
 */
private fun legacyFlatRate(billing: ElectricityBilling): ElectricityFlatRate = when (billing) {
    ElectricityBilling.NONE -> ElectricityFlatRate.NOT_USED
    ElectricityBilling.INCLUDED, ElectricityBilling.FLAT_PER_NIGHT, ElectricityBilling.FLAT_PER_STAY -> ElectricityFlatRate.YES
    ElectricityBilling.METERED, ElectricityBilling.BASE_PLUS_METERED, ElectricityBilling.COIN -> ElectricityFlatRate.NO
}
