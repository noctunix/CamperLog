package app.restvolt.camperlog.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.restvolt.camperlog.MainActivity
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ReminderNotification
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.labelRes
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Intent-Extra mit der Fahrzeug-id, die der Fahrzeug-Reiter beim Start auswählen soll. */
const val EXTRA_OPEN_VEHICLE_ID = "app.restvolt.camperlog.EXTRA_OPEN_VEHICLE_ID"

/** Intent-Extra, das beim Start direkt den Daten-Screen öffnet (Sicherungs-Erinnerung). */
const val EXTRA_OPEN_DATA = "app.restvolt.camperlog.EXTRA_OPEN_DATA"

internal const val CHANNEL_ID = "maintenance_reminders"
private const val GROUP_KEY = "app.restvolt.camperlog.REMINDERS"
private const val SUMMARY_NOTIFICATION_ID = Int.MAX_VALUE
private const val BACKUP_NOTIFICATION_ID = Int.MAX_VALUE - 1

/**
 * Postet die Benachrichtigungen des täglichen Hintergrund-Checks (1.9.0): fällige
 * Wartungserinnerungen (Kanal "Maintenance reminders / Wartungserinnerungen") und die
 * Sicherungs-Erinnerung.
 */
interface ReminderNotifier {
    /** Eine Benachrichtigung je [notifications], gruppiert unter einer Zusammenfassung ab zwei Posten. */
    fun notifyReminders(notifications: List<ReminderNotification>, vehicleNames: Map<Long, String>)

    /** Einzelne Benachrichtigung, dass die Sicherung überfällig ist. */
    fun notifyBackupOverdue()
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    override fun notifyReminders(notifications: List<ReminderNotification>, vehicleNames: Map<Long, String>) {
        if (notifications.isEmpty() || !ensureChannelAndPermission()) return
        val manager = NotificationManagerCompat.from(context)
        val defaultVehicleName = context.getString(R.string.vehicle_default_name)
        val locale = context.resources.configuration.locales.get(0) ?: Locale.getDefault()
        val today = LocalDate.now()
        notifications.forEachIndexed { index, item ->
            val vehicleName = vehicleNames[item.vehicleId]?.ifBlank { null } ?: defaultVehicleName
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_directions_car)
                .setContentTitle(vehicleName)
                .setContentText(reminderNotificationBody(context.resources, item, today, locale))
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .setContentIntent(vehiclePendingIntent(context, item.vehicleId, requestCode = index))
                .build()
            notify(manager, notificationId(item), notification)
        }
        if (notifications.size > 1) {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_directions_car)
                .setContentTitle(context.getString(R.string.notification_reminders_summary_title))
                .setContentText(
                    context.resources.getQuantityString(
                        R.plurals.notification_reminders_summary_text,
                        notifications.size,
                        notifications.size,
                    ),
                )
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            notify(manager, SUMMARY_NOTIFICATION_ID, summary)
        }
    }

    override fun notifyBackupOverdue() {
        if (!ensureChannelAndPermission()) return
        val manager = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_directions_car)
            .setContentTitle(context.getString(R.string.notification_backup_title))
            .setContentText(context.getString(R.string.notification_backup_text))
            .setAutoCancel(true)
            .setContentIntent(openDataPendingIntent(context))
            .build()
        notify(manager, BACKUP_NOTIFICATION_ID, notification)
    }

    /** Legt den Kanal an, falls nötig, und liefert, ob überhaupt benachrichtigt werden darf. */
    private fun ensureChannelAndPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_maintenance_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_maintenance_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return hasNotificationPermission(context)
    }

    @SuppressLint("MissingPermission") // hasNotificationPermission/ensureChannelAndPermission already gate this call.
    private fun notify(manager: NotificationManagerCompat, id: Int, notification: android.app.Notification) {
        manager.notify(id, notification)
    }
}

/** Ob die App auf diesem Gerät Benachrichtigungen posten darf; vor Android 13 immer `true`. */
fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Stabile Benachrichtigungs-id je Fahrzeug, Erinnerungsart und Schwelle, damit erneutes Posten dieselbe Zeile ersetzt. */
private fun notificationId(item: ReminderNotification): Int =
    (item.vehicleId.toString() + item.kind.name + item.overdue).hashCode()

/** Öffnet die App auf dem Fahrzeug-Reiter mit [vehicleId] vorausgewählt (`MainActivity`/NavHost, 1.9.0). */
internal fun vehiclePendingIntent(context: Context, vehicleId: Long, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_VEHICLE_ID, vehicleId)
    }
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Öffnet die App auf dem Daten-Screen (Sicherungs-Erinnerung, 1.9.0). */
internal fun openDataPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_DATA, true)
    }
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Anzeigetext einer Erinnerung außerhalb von Compose; inhaltlich wie die Erinnerungskarten im Fahrzeug-Reiter. */
internal fun reminderNotificationBody(resources: Resources, item: ReminderNotification, today: LocalDate, locale: Locale): String {
    val kindLabel = resources.getString(item.kind.labelRes)
    val date = formatDate(item.dueDate, locale)
    return if (item.overdue) {
        resources.getString(R.string.reminder_overdue, kindLabel, date)
    } else {
        val days = ChronoUnit.DAYS.between(today, item.dueDate).toInt()
        resources.getQuantityString(R.plurals.reminder_due_soon, days, kindLabel, days, date)
    }
}
