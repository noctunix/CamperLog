package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.formatDate
import java.util.Locale

/** Überschrift eines Tagebucheintrags im Export: Datum, mit Uhrzeit, wenn gesetzt. */
internal fun diaryEntryHeading(entry: DiaryEntry, locale: Locale): String {
    val date = formatDate(entry.date, locale)
    return entry.time?.let { "$date, %02d:%02d".format(it.hour, it.minute) } ?: date
}

/** Wetterzeile eines Tagebucheintrags im Export, `null` ohne Eintrag. */
internal fun diaryEntryWeatherLine(res: Resources, entry: DiaryEntry): String? =
    entry.weather.takeIf { it.isNotBlank() }
        ?.let { res.getString(R.string.station_summary_field, res.getString(R.string.diary_field_weather), it) }

/** Kilometerstandzeile eines Tagebucheintrags im Export, `null` ohne Eintrag. */
internal fun diaryEntryOdometerLine(res: Resources, entry: DiaryEntry): String? =
    entry.odometerKm?.let {
        res.getString(R.string.station_summary_field, res.getString(R.string.field_odometer_km), res.getString(R.string.distance_km, it))
    }
