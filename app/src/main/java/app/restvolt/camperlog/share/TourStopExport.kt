package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.domain.compassDirection
import app.restvolt.camperlog.domain.effectiveCosts
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatObservedTime
import app.restvolt.camperlog.domain.sumByCurrency
import app.restvolt.camperlog.domain.supportedLocale
import app.restvolt.camperlog.domain.weatherCondition
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.yesNoRes
import java.util.Locale

/**
 * Eine für Tour.html/Tour.md aufbereitete Station der Zeitleiste. [heading] ist der Name, sonst die
 * Stationsart; die übrigen Felder sind `null`, wenn die Station dazu nichts enthält.
 */
data class TourStopExport(
    val heading: String,
    val typeAndTime: String,
    val address: String?,
    val osmLink: String?,
    val coordinatesText: String?,
    val costsLine: String?,
    val notesLine: String?,
    val weatherLine: String?,
    val pitchDetailsLine: String?,
    val connectivityLine: String?,
    val boardAndAmenitiesLine: String?,
    val photos: List<TourExportPhoto>,
)

/** Baut [TourStopExport] für jede der [stations], in ihrer Reihenfolge; [photosByStation] wie von [buildTourPhotoPaths]. */
fun tourStopExports(res: Resources, stations: List<Station>, photosByStation: Map<Long, List<TourExportPhoto>>): List<TourStopExport> {
    val locale = supportedLocale(res.configuration.locales[0])
    return stations.map { station -> tourStopExport(res, station, locale, photosByStation[station.id].orEmpty()) }
}

private fun tourStopExport(res: Resources, station: Station, locale: Locale, photos: List<TourExportPhoto>): TourStopExport {
    val hasCoordinates = station.latitude != null && station.longitude != null
    val costs = station.effectiveCosts().map { it.amount }.sumByCurrency()
    return TourStopExport(
        heading = station.name.ifBlank { res.getString(station.type.labelRes) },
        typeAndTime = "${res.getString(station.type.labelRes)} · ${stationDateTimeText(station, locale)}",
        address = station.place.takeIf { it.isNotBlank() },
        osmLink = if (hasCoordinates) osmLink(station.latitude, station.longitude) else null,
        coordinatesText = if (hasCoordinates) formatCoordinates(station.latitude, station.longitude, locale) else null,
        costsLine = costs.takeIf { it.isNotEmpty() }
            ?.let { res.getString(R.string.station_summary_field, res.getString(R.string.station_section_costs), formatAmounts(it, locale)) },
        notesLine = station.notes.takeIf { it.isNotBlank() }
            ?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_notes), it) },
        weatherLine = station.weather?.let { res.getString(R.string.station_summary_field, res.getString(R.string.station_section_weather), weatherSummaryText(res, it)) },
        pitchDetailsLine = pitchDetailsText(res, station)?.let { res.getString(R.string.station_summary_field, res.getString(R.string.station_section_pitch_details), it) },
        connectivityLine = connectivityText(res, station, locale),
        boardAndAmenitiesLine = boardAndAmenitiesText(res, station),
        photos = photos,
    )
}

/** Platzarten mit klassischen Stellplatz-Details (Zuweisung, Gefälle, Keile). */
private val PITCH_SITE_KINDS: Set<SiteKind> = setOf(SiteKind.CAMPSITE, SiteKind.MOTORHOME_AREA, SiteKind.WILD)

/** Platzarten mit Verpflegung und Ausstattungsmerkmalen statt Stellplatz-Details. */
private val LODGING_SITE_KINDS: Set<SiteKind> = setOf(SiteKind.HOTEL, SiteKind.VACATION_RENTAL, SiteKind.HOSTEL)

private fun stationDateTimeText(station: Station, locale: Locale): String {
    val date = formatDate(station.date, locale)
    return station.time?.let { "$date, %02d:%02d".format(it.hour, it.minute) } ?: date
}

/** OpenStreetMap-Link auf einen Punkt mit Marker, Zoomstufe 15. */
private fun osmLink(latitude: Double, longitude: Double): String =
    "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=15/$latitude/$longitude"

private fun weatherSummaryText(res: Resources, snapshot: WeatherSnapshot): String {
    val condition = weatherCondition(snapshot.weatherCode)
    val temperature = Math.round(snapshot.temperatureDeciC / 10.0).toInt()
    val time = formatObservedTime(snapshot.observedAt)
    return "${res.getString(R.string.weather_temperature_c, temperature)} · ${res.getString(condition.labelRes)} · ${weatherWindText(res, snapshot)} · $time"
}

private fun weatherWindText(res: Resources, snapshot: WeatherSnapshot): String {
    val direction = snapshot.windDirectionDeg?.let { res.getString(compassDirection(it).labelRes) }
    val gust = snapshot.gustKmh
    return when {
        direction != null && gust != null -> res.getString(R.string.weather_wind_full, snapshot.windKmh, direction, gust)
        direction != null -> res.getString(R.string.weather_wind_no_gust, snapshot.windKmh, direction)
        gust != null -> res.getString(R.string.weather_wind_no_direction_full, snapshot.windKmh, gust)
        else -> res.getString(R.string.weather_wind_plain, snapshot.windKmh)
    }
}

private fun pitchDetailsText(res: Resources, station: Station): String? {
    val showPitchFields = station.siteKind == null || station.siteKind in PITCH_SITE_KINDS
    val parts = listOfNotNull(
        station.siteKind?.let { res.getString(it.labelRes) },
        station.pitchAssigned?.takeIf { showPitchFields }
            ?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_pitch_assigned), res.getString(yesNoRes(it))) },
        station.pitchSlope?.takeIf { showPitchFields }?.let { res.getString(it.labelRes) },
        station.levelingBlocksUsed?.takeIf { showPitchFields }
            ?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_leveling_blocks), res.getString(yesNoRes(it))) },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** LTE- und WLAN-Qualität samt WLAN-Preisinfo; gilt für jede Übernachtung, unabhängig von [Station.siteKind]. */
private fun connectivityText(res: Resources, station: Station, locale: Locale): String? {
    val parts = listOfNotNull(
        station.lteQuality?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_lte), res.getString(it.labelRes)) },
        station.wifiQuality?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_wifi), res.getString(it.labelRes)) },
        station.wifiCost?.let {
            val status = res.getString(if (station.wifiCostIsExpense) R.string.wifi_expense_status_included else R.string.wifi_expense_status_price_info)
            val value = res.getString(R.string.wifi_cost_with_status, formatAmount(it.minor, it.currency, locale), status)
            res.getString(R.string.station_summary_field, res.getString(R.string.field_wifi_cost), value)
        },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** Verpflegung und Ausstattungsmerkmale; nur bei [LODGING_SITE_KINDS]. */
private fun boardAndAmenitiesText(res: Resources, station: Station): String? {
    if (station.siteKind !in LODGING_SITE_KINDS) return null
    val parts = listOfNotNull(
        station.boardType?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_board_type), res.getString(it.labelRes)) },
        station.lodgingAmenities.takeIf { it.isNotEmpty() }?.let { amenities ->
            val values = amenities.sortedBy { it.ordinal }.joinToString(", ") { res.getString(it.labelRes) }
            res.getString(R.string.station_summary_field, res.getString(R.string.station_section_amenities), values)
        },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
