package app.restvolt.camperlog.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Setzt eine pausierte Trackaufzeichnung fort, nachdem die beim Pausieren gewählte Dauer
 * abgelaufen ist. Kommt nur über den eigenen `PendingIntent` aus
 * [TrackRecordingService.scheduleAutoResume], nie als Broadcast von außen.
 */
class PauseResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTO_RESUME) return
        val tourId = intent.getLongExtra(EXTRA_TOUR_ID, -1L)
        if (tourId <= 0) return
        TrackRecordingService.resumeFromTimedPause(context, tourId)
    }

    companion object {
        const val ACTION_AUTO_RESUME = "app.restvolt.camperlog.tracking.AUTO_RESUME"
        const val EXTRA_TOUR_ID = "tour_id"
    }
}
