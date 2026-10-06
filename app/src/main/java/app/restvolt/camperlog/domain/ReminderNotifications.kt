package app.restvolt.camperlog.domain

import java.time.LocalDate

/**
 * Zuletzt für ein (Fahrzeug, Erinnerungsart)-Paar benachrichtigte Schwellen; [dueDate] erkennt eine
 * geänderte Fälligkeit (z. B. nach Bearbeiten des Fahrzeugs), die beide Schwellen zurücksetzt.
 */
data class ReminderNotificationState(val dueDate: LocalDate, val leadNotified: Boolean = false, val overdueNotified: Boolean = false)

/**
 * Eine zu sendende Benachrichtigung für eine fällige Erinnerung; [overdue] unterscheidet die beiden
 * Schwellen. [documentId] und [label] sind nur bei [ReminderKind.DOCUMENT_EXPIRY] gesetzt, siehe [Reminder].
 */
data class ReminderNotification(
    val vehicleId: Long,
    val kind: ReminderKind,
    val dueDate: LocalDate,
    val overdue: Boolean,
    val documentId: Long? = null,
    val label: String? = null,
)

/**
 * Entscheidet für die fälligen [reminders] eines Fahrzeugs ([vehicleId]), welche noch eine
 * Benachrichtigung brauchen, und liefert sie zusammen mit dem neuen Zustand je Erinnerungsart.
 *
 * Jede Erinnerung überschreitet im Lauf der Zeit bis zu zwei Schwellen: das Eintreten in den Vorlauf
 * (`overdue == false`) und das Überfälligwerden (`overdue == true`); jede wird genau einmal gemeldet,
 * nie mehrere fällige Posten zu einem einzigen zusammengefasst oder stillschweigend weggelassen.
 * Ändert sich [Reminder.dueDate] gegenüber [previousStates], gilt das als neuer Zyklus:
 * beide Schwellen gelten wieder als nicht gemeldet. Erinnerungsarten, die in [reminders] nicht mehr
 * vorkommen, fehlen auch im zurückgegebenen Zustand - der Aufrufer ersetzt den gespeicherten Zustand
 * des Fahrzeugs vollständig damit, statt ihn zusammenzuführen.
 */
fun pendingReminderNotifications(
    vehicleId: Long,
    reminders: List<Reminder>,
    previousStates: Map<ReminderKind, ReminderNotificationState>,
): Pair<List<ReminderNotification>, Map<ReminderKind, ReminderNotificationState>> {
    val notifications = mutableListOf<ReminderNotification>()
    val nextStates = mutableMapOf<ReminderKind, ReminderNotificationState>()
    for (reminder in reminders) {
        val previous = previousStates[reminder.kind]?.takeIf { it.dueDate == reminder.dueDate }
        val leadNotified = previous?.leadNotified ?: false
        val overdueNotified = previous?.overdueNotified ?: false
        val crossedLead = !reminder.overdue && !leadNotified
        val crossedOverdue = reminder.overdue && !overdueNotified
        if (crossedOverdue) {
            notifications += ReminderNotification(vehicleId, reminder.kind, reminder.dueDate, overdue = true)
        } else if (crossedLead) {
            notifications += ReminderNotification(vehicleId, reminder.kind, reminder.dueDate, overdue = false)
        }
        nextStates[reminder.kind] = ReminderNotificationState(
            dueDate = reminder.dueDate,
            leadNotified = leadNotified || crossedLead,
            overdueNotified = overdueNotified || crossedOverdue,
        )
    }
    return notifications to nextStates
}

/**
 * Wie [pendingReminderNotifications], aber für [reminders] von [ReminderKind.DOCUMENT_EXPIRY]
 * (siehe [documentReminders]): der Zustand wird je [Reminder.documentId] verfolgt statt je Art, weil
 * mehrere Dokumente gleichzeitig ablaufen können und sonst nur eines davon gemeldet würde.
 */
fun pendingDocumentReminderNotifications(
    vehicleId: Long,
    reminders: List<Reminder>,
    previousStates: Map<Long, ReminderNotificationState>,
): Pair<List<ReminderNotification>, Map<Long, ReminderNotificationState>> {
    val notifications = mutableListOf<ReminderNotification>()
    val nextStates = mutableMapOf<Long, ReminderNotificationState>()
    for (reminder in reminders) {
        val documentId = checkNotNull(reminder.documentId) { "Dokument-Erinnerung ohne Dokument-id" }
        val previous = previousStates[documentId]?.takeIf { it.dueDate == reminder.dueDate }
        val leadNotified = previous?.leadNotified ?: false
        val overdueNotified = previous?.overdueNotified ?: false
        val crossedLead = !reminder.overdue && !leadNotified
        val crossedOverdue = reminder.overdue && !overdueNotified
        if (crossedOverdue) {
            notifications += ReminderNotification(vehicleId, reminder.kind, reminder.dueDate, overdue = true, documentId = documentId, label = reminder.label)
        } else if (crossedLead) {
            notifications += ReminderNotification(vehicleId, reminder.kind, reminder.dueDate, overdue = false, documentId = documentId, label = reminder.label)
        }
        nextStates[documentId] = ReminderNotificationState(
            dueDate = reminder.dueDate,
            leadNotified = leadNotified || crossedLead,
            overdueNotified = overdueNotified || crossedOverdue,
        )
    }
    return notifications to nextStates
}
