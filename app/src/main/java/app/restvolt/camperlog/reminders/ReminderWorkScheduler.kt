package app.restvolt.camperlog.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Eindeutiger Name des periodischen Jobs, damit [androidx.work.WorkManager] ihn nicht doppelt anlegt. */
internal const val REMINDER_WORK_NAME = "reminder_check"

private const val REPEAT_INTERVAL_HOURS = 24L
private const val FLEX_INTERVAL_HOURS = 4L

/**
 * Plant oder stoppt den täglichen Hintergrund-Check (1.9.0); eigenes Interface, damit
 * [app.restvolt.camperlog.ui.settings.NotificationSettings] mit einem Fake testbar bleibt.
 */
interface ReminderWorkScheduler {
    /** Plant den Job, falls noch keiner läuft (`KEEP`); sicher auch beim App-Start wiederholt aufzurufen. */
    fun enqueue()

    /** Stoppt den Job. */
    fun cancel()
}

/**
 * [ReminderWorkScheduler] über [WorkManager]: alle 24 h mit 4 h Spielraum, ohne Netzwerk-Constraint
 * (der Check liest nur die lokale Datenbank) und ohne exakte Alarme (ROADMAP.md 1.9.0).
 */
class AndroidReminderWorkScheduler(private val context: Context) : ReminderWorkScheduler {
    override fun enqueue() {
        val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS, FLEX_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
    }
}
