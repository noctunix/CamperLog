package app.restvolt.camperlog.tracking

import android.content.res.Resources
import app.restvolt.camperlog.R

/** Angebotene Pausendauern in Minuten, bis eine pausierte Aufzeichnung automatisch fortsetzt; `0` = ohne automatisches Fortsetzen. */
val PAUSE_RESUME_DURATIONS_MINUTES = listOf(0, 30, 60, 180, 480, 1_440)

/** Kurzes Label einer Pausendauer aus [PAUSE_RESUME_DURATIONS_MINUTES], z. B. "30 min", "1 h" oder "1 Tag". */
fun pauseResumeDurationLabel(resources: Resources, minutes: Int): String = when {
    minutes == 0 -> resources.getString(R.string.tour_track_pause_duration_none)
    minutes < 60 -> resources.getString(R.string.track_interval_minutes, minutes)
    minutes < 1_440 -> resources.getString(R.string.track_interval_hours, minutes / 60)
    else -> resources.getString(R.string.tour_track_pause_duration_day)
}
