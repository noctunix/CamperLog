package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.domain.compassDirection
import app.restvolt.camperlog.domain.effectiveCosts
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
        photos = photos,
    )
}

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
    val parts = listOfNotNull(
        station.siteKind?.let { res.getString(it.labelRes) },
        station.pitchAssigned?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_pitch_assigned), res.getString(yesNoRes(it))) },
        station.lteQuality?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_lte), res.getString(it.labelRes)) },
        station.pitchSlope?.let { res.getString(it.labelRes) },
        station.levelingBlocksUsed?.let { res.getString(R.string.station_summary_field, res.getString(R.string.field_leveling_blocks), res.getString(yesNoRes(it))) },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
