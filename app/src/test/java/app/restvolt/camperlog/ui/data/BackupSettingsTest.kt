package app.restvolt.camperlog.ui.data

import android.content.Context
import app.restvolt.camperlog.reminders.ReminderWorkScheduler
import app.restvolt.camperlog.ui.settings.NotificationSettings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupSettingsTest {

    private class Scheduler : ReminderWorkScheduler {
        var enqueueCalls = 0
        var cancelCalls = 0
        override fun enqueue() { enqueueCalls++ }
        override fun cancel() { cancelCalls++ }
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun automaticBackupKeepsWorkingWithoutNotifications() {
        clear()
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
        val scheduler = Scheduler()
        val settings = BackupSettings(context, scheduler)
        settings.folderUri = "content://folder"
        settings.autoBackupToFolder = true
        val before = scheduler.enqueueCalls
        val notifications = NotificationSettings(context, scheduler)
        notifications.enabled = false
        assertEquals(before + 1, scheduler.enqueueCalls)
        val reloadedScheduler = Scheduler()
        BackupSettings(context, reloadedScheduler)
        assertEquals(1, reloadedScheduler.enqueueCalls)
        settings.autoBackupToFolder = false
        assertEquals(2, scheduler.cancelCalls)
        clear()
    }

    @Test
    fun defaults() {
        clear()
        try {
            val settings = BackupSettings(context, Scheduler())

            assertEquals(DEFAULT_BACKUP_REMINDER_WEEKS, settings.reminderWeeks)
            assertNull(settings.lastBackupAt)
            assertNull(settings.lastNotifiedBackupBaseline)
            assertNull(settings.folderUri)
            assertEquals(false, settings.values.value.autoBackupToFolder)
            assertNull(settings.values.value.autoBackupIncludeFilesOverride)
        } finally {
            clear()
        }
    }

    @Test
    fun autoBackupIncludeFilesOverride_persistsTrueAndFalseAndClearsToNull() {
        clear()
        try {
            val settings = BackupSettings(context, Scheduler())

            settings.autoBackupIncludeFilesOverride = false
            assertEquals(false, BackupSettings(context, Scheduler()).autoBackupIncludeFilesOverride)

            settings.autoBackupIncludeFilesOverride = true
            assertEquals(true, BackupSettings(context, Scheduler()).autoBackupIncludeFilesOverride)

            settings.autoBackupIncludeFilesOverride = null
            assertNull(BackupSettings(context, Scheduler()).autoBackupIncludeFilesOverride)
        } finally {
            clear()
        }
    }

    @Test
    fun changesPersistAcrossInstancesAtMillisecondPrecision() {
        clear()
        try {
            val at = Instant.parse("2026-10-06T08:15:30.123Z")
            val settings = BackupSettings(context, Scheduler())

            settings.reminderWeeks = 8
            settings.folderUri = "content://folder"
            settings.autoBackupToFolder = true
            settings.lastBackupAt = at
            settings.lastNotifiedBackupBaseline = at

            val reloaded = BackupSettings(context, Scheduler())
            assertEquals(8, reloaded.reminderWeeks)
            assertEquals("content://folder", reloaded.folderUri)
            assertEquals(true, reloaded.values.value.autoBackupToFolder)
            assertEquals(at, reloaded.lastBackupAt)
            assertEquals(at, reloaded.lastNotifiedBackupBaseline)
        } finally {
            clear()
        }
    }

    @Test
    fun failedFolderAttemptPreservesConfirmedTargetTimeAndScope() {
        clear()
        val settings = BackupSettings(context, Scheduler())
        val at = Instant.parse("2026-10-22T10:00:00Z")
        settings.recordFolderBackupResult("content://first", true, at)
        settings.recordFolderBackupResult("content://second", false, null)
        val reloaded = BackupSettings(context, Scheduler()).values.value
        assertEquals(FolderBackupRecord(at, "content://first", true), reloaded.lastFolderBackup)
        assertEquals(at, reloaded.lastBackupAt)
        assertEquals("content://second", reloaded.lastFolderBackupFailedFor)
        settings.recordFolderBackupResult("content://second", false, at.plusSeconds(1))
        assertEquals(false, settings.values.value.lastFolderBackup?.includeFiles)
        assertNull(settings.values.value.lastFolderBackupFailedFor)
        clear()
    }

    @Test
    fun recordBackupMade_setsLastBackupAt() {
        clear()
        try {
            val settings = BackupSettings(context, Scheduler())
            val at = Instant.parse("2026-10-06T08:15:30Z")

            settings.recordBackupMade(at)

            assertEquals(at, settings.values.value.lastBackupAt)
        } finally {
            clear()
        }
    }

    @Test
    fun settingFolderUriToNull_clearsIt() {
        clear()
        try {
            val settings = BackupSettings(context, Scheduler())
            settings.folderUri = "content://folder"

            settings.folderUri = null

            assertNull(BackupSettings(context, Scheduler()).folderUri)
        } finally {
            clear()
        }
    }
}
