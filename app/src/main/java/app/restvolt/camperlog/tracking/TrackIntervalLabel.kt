package app.restvolt.camperlog.tracking

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.TrackInterval

/** Kurzes Label eines Intervalls, z. B. "30 s" oder "15 min". */
fun trackIntervalLabel(resources: Resources, interval: TrackInterval): String =
    if (interval.seconds < 60) resources.getString(R.string.track_interval_seconds, interval.seconds)
    else resources.getString(R.string.track_interval_minutes, interval.seconds / 60)
