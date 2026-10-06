package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

private val NOW = Instant.parse("2026-10-06T12:00:00Z")

class BackupReminderTest {

    @Test
    fun isBackupOverdue_off_isNeverOverdue() {
        assertFalse(isBackupOverdue(null, reminderWeeks = 0, now = NOW))
        assertFalse(isBackupOverdue(NOW.minus(1000, ChronoUnit.DAYS), reminderWeeks = 0, now = NOW))
    }

    @Test
    fun isBackupOverdue_neverBackedUp_isOverdue() {
        assertTrue(isBackupOverdue(null, reminderWeeks = 4, now = NOW))
    }

    @Test
    fun isBackupOverdue_withinInterval_isNotOverdue() {
        val lastBackup = NOW.minus(27, ChronoUnit.DAYS)
        assertFalse(isBackupOverdue(lastBackup, reminderWeeks = 4, now = NOW))
    }

    @Test
    fun isBackupOverdue_pastInterval_isOverdue() {
        val lastBackup = NOW.minus(29, ChronoUnit.DAYS)
        assertTrue(isBackupOverdue(lastBackup, reminderWeeks = 4, now = NOW))
    }

    @Test
    fun shouldNotifyBackupOverdue_firstTime_notifies() {
        assertTrue(shouldNotifyBackupOverdue(null, reminderWeeks = 4, now = NOW, lastNotifiedBackupBaseline = null))
    }

    @Test
    fun shouldNotifyBackupOverdue_alreadyNotifiedForThisBaseline_doesNotRepeat() {
        val baseline = backupNotificationBaseline(null)

        assertFalse(shouldNotifyBackupOverdue(null, reminderWeeks = 4, now = NOW, lastNotifiedBackupBaseline = baseline))
    }

    @Test
    fun shouldNotifyBackupOverdue_newBackupRearmsTheNextOverduePeriod() {
        val firstBaseline = backupNotificationBaseline(null)
        val freshBackup = NOW.minus(1, ChronoUnit.DAYS)

        // Right after a fresh backup, it is not overdue, regardless of the old baseline.
        assertFalse(shouldNotifyBackupOverdue(freshBackup, reminderWeeks = 4, now = NOW, lastNotifiedBackupBaseline = firstBaseline))

        val muchLater = freshBackup.plus(30, ChronoUnit.DAYS)
        assertTrue(shouldNotifyBackupOverdue(freshBackup, reminderWeeks = 4, now = muchLater, lastNotifiedBackupBaseline = firstBaseline))
    }

    @Test
    fun shouldShowBackupReminderCard_onlyWhenNotificationsAreOffAndOverdue() {
        assertFalse(shouldShowBackupReminderCard(notificationsEnabled = true, lastBackupAt = null, reminderWeeks = 4, now = NOW))
        assertTrue(shouldShowBackupReminderCard(notificationsEnabled = false, lastBackupAt = null, reminderWeeks = 4, now = NOW))
        assertFalse(shouldShowBackupReminderCard(notificationsEnabled = false, lastBackupAt = NOW, reminderWeeks = 4, now = NOW))
    }
}
