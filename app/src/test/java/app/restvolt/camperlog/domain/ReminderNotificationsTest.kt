package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 6)
private val DUE_SOON = TODAY.plusDays(5)
private val OVERDUE = TODAY.minusDays(3)

class ReminderNotificationsTest {

    @Test
    fun firstSighting_dueSoon_notifiesLeadThresholdOnly() {
        val reminder = Reminder(ReminderKind.INSPECTION, DUE_SOON, overdue = false)

        val (notifications, states) = pendingReminderNotifications(1, listOf(reminder), emptyMap())

        assertEquals(listOf(ReminderNotification(1, ReminderKind.INSPECTION, DUE_SOON, overdue = false)), notifications)
        assertEquals(ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false), states[ReminderKind.INSPECTION])
    }

    @Test
    fun repeatedRun_sameDueDate_doesNotNotifyAgain() {
        val reminder = Reminder(ReminderKind.INSPECTION, DUE_SOON, overdue = false)
        val (_, firstStates) = pendingReminderNotifications(1, listOf(reminder), emptyMap())

        val (notifications, _) = pendingReminderNotifications(1, listOf(reminder), firstStates)

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun becomingOverdue_notifiesOverdueThresholdOnce() {
        val leadState = mapOf(ReminderKind.INSPECTION to ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false))
        val nowOverdue = Reminder(ReminderKind.INSPECTION, DUE_SOON, overdue = true)

        val (notifications, states) = pendingReminderNotifications(1, listOf(nowOverdue), leadState)

        assertEquals(listOf(ReminderNotification(1, ReminderKind.INSPECTION, DUE_SOON, overdue = true)), notifications)
        assertEquals(true, states[ReminderKind.INSPECTION]?.overdueNotified)

        val (repeated, _) = pendingReminderNotifications(1, listOf(nowOverdue), states)
        assertTrue(repeated.isEmpty())
    }

    @Test
    fun dueDateChange_rearmsBothThresholds() {
        val previousState = mapOf(ReminderKind.INSPECTION to ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = true))
        val newDueDate = DUE_SOON.plusDays(20)
        val reminder = Reminder(ReminderKind.INSPECTION, newDueDate, overdue = false)

        val (notifications, states) = pendingReminderNotifications(1, listOf(reminder), previousState)

        assertEquals(listOf(ReminderNotification(1, ReminderKind.INSPECTION, newDueDate, overdue = false)), notifications)
        assertEquals(ReminderNotificationState(newDueDate, leadNotified = true, overdueNotified = false), states[ReminderKind.INSPECTION])
    }

    @Test
    fun severalDueItems_notifyIndependently() {
        val reminders = listOf(
            Reminder(ReminderKind.INSPECTION, DUE_SOON, overdue = false),
            Reminder(ReminderKind.GAS_CHECK, OVERDUE, overdue = true),
            Reminder(ReminderKind.LEAK_TEST, OVERDUE, overdue = true),
        )

        val (notifications, states) = pendingReminderNotifications(1, reminders, emptyMap())

        assertEquals(3, notifications.size)
        assertEquals(setOf(ReminderKind.INSPECTION, ReminderKind.GAS_CHECK, ReminderKind.LEAK_TEST), states.keys)
    }

    @Test
    fun kindNoLongerDue_isDroppedFromTheNextState() {
        val previousState = mapOf(
            ReminderKind.INSPECTION to ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false),
            ReminderKind.OIL_CHANGE to ReminderNotificationState(OVERDUE, leadNotified = true, overdueNotified = true),
        )

        val (_, states) = pendingReminderNotifications(1, listOf(Reminder(ReminderKind.INSPECTION, DUE_SOON, overdue = false)), previousState)

        assertEquals(setOf(ReminderKind.INSPECTION), states.keys)
    }

    private fun documentReminder(documentId: Long, dueDate: LocalDate, overdue: Boolean, label: String = "Dokument $documentId") =
        Reminder(ReminderKind.DOCUMENT_EXPIRY, dueDate, overdue, label = label, documentId = documentId)

    @Test
    fun documentExpiry_firstSighting_dueSoon_notifiesLeadThresholdOnly() {
        val reminder = documentReminder(documentId = 1, dueDate = DUE_SOON, overdue = false)

        val (notifications, states) = pendingDocumentReminderNotifications(1, listOf(reminder), emptyMap())

        assertEquals(listOf(ReminderNotification(1, ReminderKind.DOCUMENT_EXPIRY, DUE_SOON, overdue = false, documentId = 1, label = "Dokument 1")), notifications)
        assertEquals(ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false), states[1L])
    }

    @Test
    fun documentExpiry_severalDocuments_notifyIndependentlyByDocumentIdNotByKind() {
        val reminders = listOf(
            documentReminder(documentId = 1, dueDate = DUE_SOON, overdue = false),
            documentReminder(documentId = 2, dueDate = OVERDUE, overdue = true),
        )

        val (notifications, states) = pendingDocumentReminderNotifications(1, reminders, emptyMap())

        assertEquals(2, notifications.size)
        assertEquals(setOf(1L, 2L), states.keys)
        assertEquals(true, notifications.single { it.documentId == 2L }.overdue)
    }

    @Test
    fun documentExpiry_repeatedRun_sameDueDate_doesNotNotifyAgain() {
        val reminder = documentReminder(documentId = 1, dueDate = DUE_SOON, overdue = false)
        val (_, firstStates) = pendingDocumentReminderNotifications(1, listOf(reminder), emptyMap())

        val (notifications, _) = pendingDocumentReminderNotifications(1, listOf(reminder), firstStates)

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun documentExpiry_becomingOverdue_notifiesOverdueThresholdOnce() {
        val leadState = mapOf(1L to ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false))
        val nowOverdue = documentReminder(documentId = 1, dueDate = DUE_SOON, overdue = true)

        val (notifications, states) = pendingDocumentReminderNotifications(1, listOf(nowOverdue), leadState)

        assertEquals(true, notifications.single().overdue)
        assertEquals(true, states[1L]?.overdueNotified)

        val (repeated, _) = pendingDocumentReminderNotifications(1, listOf(nowOverdue), states)
        assertTrue(repeated.isEmpty())
    }

    @Test
    fun documentExpiry_documentNoLongerDue_isDroppedFromTheNextState() {
        val previousState = mapOf(
            1L to ReminderNotificationState(DUE_SOON, leadNotified = true, overdueNotified = false),
            2L to ReminderNotificationState(OVERDUE, leadNotified = true, overdueNotified = true),
        )

        val (_, states) = pendingDocumentReminderNotifications(1, listOf(documentReminder(documentId = 1, dueDate = DUE_SOON, overdue = false)), previousState)

        assertEquals(setOf(1L), states.keys)
    }
}
