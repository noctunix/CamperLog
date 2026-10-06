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
}
