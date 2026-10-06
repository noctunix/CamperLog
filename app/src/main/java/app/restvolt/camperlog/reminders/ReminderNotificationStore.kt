package app.restvolt.camperlog.reminders

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.domain.ReminderKind
import app.restvolt.camperlog.domain.ReminderNotificationState
import java.time.LocalDate

/**
 * Persistiert, welche Erinnerungsschwellen je Fahrzeug und Erinnerungsart (siehe
 * [app.restvolt.camperlog.domain.pendingReminderNotifications]) schon gemeldet wurden, damit der
 * tägliche Hintergrund-Check beim nächsten Lauf nicht erneut benachrichtigt.
 */
interface ReminderNotificationStore {
    /** Gespeicherter Zustand je Erinnerungsart für Fahrzeug [vehicleId]; fehlende Arten sind nie gemeldet worden. */
    fun statesFor(vehicleId: Long): Map<ReminderKind, ReminderNotificationState>

    /** Ersetzt den gespeicherten Zustand für Fahrzeug [vehicleId] vollständig durch [states]. */
    fun saveStatesFor(vehicleId: Long, states: Map<ReminderKind, ReminderNotificationState>)
}

/**
 * [ReminderNotificationStore] über SharedPreferences statt einer Room-Tabelle: Die Daten sind eine
 * reine Zustellungs-Historie ohne fachlichen Wert, stehen in keiner Beziehung zu anderen Tabellen und
 * dürfen folgenlos verloren gehen (schlimmstenfalls eine einmalige doppelte Benachrichtigung) - das
 * spart Schema, Migration und DAO für eine kleine Menge Schlüssel-Wert-Paare.
 */
class AndroidReminderNotificationStore(context: Context) : ReminderNotificationStore {
    private val preferences = context.getSharedPreferences("reminder_notifications", Context.MODE_PRIVATE)

    override fun statesFor(vehicleId: Long): Map<ReminderKind, ReminderNotificationState> =
        ReminderKind.entries.mapNotNull { kind ->
            val raw = preferences.getString(key(vehicleId, kind), null) ?: return@mapNotNull null
            parse(raw)?.let { kind to it }
        }.toMap()

    override fun saveStatesFor(vehicleId: Long, states: Map<ReminderKind, ReminderNotificationState>) {
        preferences.edit {
            for (kind in ReminderKind.entries) {
                val state = states[kind]
                if (state == null) remove(key(vehicleId, kind)) else putString(key(vehicleId, kind), format(state))
            }
        }
    }

    private fun key(vehicleId: Long, kind: ReminderKind) = "$vehicleId:${kind.name}"

    private fun format(state: ReminderNotificationState): String = "${state.dueDate}|${state.leadNotified}|${state.overdueNotified}"

    private fun parse(raw: String): ReminderNotificationState? {
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val dueDate = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val lead = parts[1].toBooleanStrictOrNull() ?: return null
        val overdue = parts[2].toBooleanStrictOrNull() ?: return null
        return ReminderNotificationState(dueDate, lead, overdue)
    }
}
