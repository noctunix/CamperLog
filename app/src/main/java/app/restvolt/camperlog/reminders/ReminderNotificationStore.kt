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

/**
 * Wie [ReminderNotificationStore], aber für Erinnerungen von [app.restvolt.camperlog.domain.ReminderKind.DOCUMENT_EXPIRY]:
 * der Zustand wird je Dokument-id verfolgt statt je Erinnerungsart, siehe
 * [app.restvolt.camperlog.domain.pendingDocumentReminderNotifications].
 */
interface DocumentReminderNotificationStore {
    /** Gespeicherter Zustand je Dokument-id für Fahrzeug [vehicleId]; fehlende Dokumente sind nie gemeldet worden. */
    fun statesFor(vehicleId: Long): Map<Long, ReminderNotificationState>

    /** Ersetzt den gespeicherten Zustand für Fahrzeug [vehicleId] vollständig durch [states]. */
    fun saveStatesFor(vehicleId: Long, states: Map<Long, ReminderNotificationState>)
}

/**
 * [DocumentReminderNotificationStore] über SharedPreferences, siehe [AndroidReminderNotificationStore].
 * Anders als die feste Anzahl [app.restvolt.camperlog.domain.ReminderKind]-Werte ist die Menge der
 * Dokument-ids eines Fahrzeugs nicht im Voraus bekannt, daher liegen alle Zustände eines Fahrzeugs in
 * einem einzigen Schlüssel als durch `;` getrennte `id|Datum|lead|overdue`-Einträge.
 */
class AndroidDocumentReminderNotificationStore(context: Context) : DocumentReminderNotificationStore {
    private val preferences = context.getSharedPreferences("document_reminder_notifications", Context.MODE_PRIVATE)

    override fun statesFor(vehicleId: Long): Map<Long, ReminderNotificationState> {
        val raw = preferences.getString(key(vehicleId), null) ?: return emptyMap()
        return raw.split(';').filter { it.isNotEmpty() }.mapNotNull(::parseEntry).toMap()
    }

    override fun saveStatesFor(vehicleId: Long, states: Map<Long, ReminderNotificationState>) {
        preferences.edit {
            if (states.isEmpty()) {
                remove(key(vehicleId))
            } else {
                putString(key(vehicleId), states.entries.joinToString(";") { (id, state) -> format(id, state) })
            }
        }
    }

    private fun key(vehicleId: Long) = "$vehicleId:documents"

    private fun format(documentId: Long, state: ReminderNotificationState): String =
        "$documentId|${state.dueDate}|${state.leadNotified}|${state.overdueNotified}"

    private fun parseEntry(entry: String): Pair<Long, ReminderNotificationState>? {
        val parts = entry.split('|')
        if (parts.size != 4) return null
        val documentId = parts[0].toLongOrNull() ?: return null
        val dueDate = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val lead = parts[2].toBooleanStrictOrNull() ?: return null
        val overdue = parts[3].toBooleanStrictOrNull() ?: return null
        return documentId to ReminderNotificationState(dueDate, lead, overdue)
    }
}
