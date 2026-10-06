package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.reminders.AndroidReminderWorkScheduler
import app.restvolt.camperlog.reminders.ReminderWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schalter "Benachrichtigungen" unter Einstellungen → Erinnerungen (1.9.0), aus bis der Nutzer ihn
 * einschaltet. Schaltet den täglichen Hintergrund-Check über [scheduler] mit ein bzw. aus; die
 * Berechtigungsabfrage (Android 13+) liegt in der Oberfläche, da sie eine Activity braucht (siehe
 * [app.restvolt.camperlog.ui.LocationCaptureSection] für das gleiche Muster bei Standort).
 */
class NotificationSettings(context: Context, private val scheduler: ReminderWorkScheduler = AndroidReminderWorkScheduler(context)) {
    private val preferences = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Aktueller Wert des Schalters; Änderungen über [enabled] werden sofort sichtbar. */
    val values: StateFlow<Boolean> = state.asStateFlow()

    var enabled: Boolean
        get() = state.value
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            state.value = value
            if (value) scheduler.enqueue() else scheduler.cancel()
        }

    init {
        // Beim App-Start erneut einplanen: enqueueUniquePeriodicWork mit KEEP ist dafür billig und
        // sicher, falls der Job z. B. nach einem Geräte-Neustart noch fehlt (ROADMAP.md 1.9.0).
        if (state.value) scheduler.enqueue()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
