package app.restvolt.camperlog.ui.data

import android.content.Context
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

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaults() {
        clear()
        try {
            val settings = BackupSettings(context)

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
            val settings = BackupSettings(context)

            settings.autoBackupIncludeFilesOverride = false
            assertEquals(false, BackupSettings(context).autoBackupIncludeFilesOverride)

            settings.autoBackupIncludeFilesOverride = true
            assertEquals(true, BackupSettings(context).autoBackupIncludeFilesOverride)

            settings.autoBackupIncludeFilesOverride = null
            assertNull(BackupSettings(context).autoBackupIncludeFilesOverride)
        } finally {
            clear()
        }
    }

    @Test
    fun changesPersistAcrossInstancesAtMillisecondPrecision() {
        clear()
        try {
            val at = Instant.parse("2026-10-06T08:15:30.123Z")
            val settings = BackupSettings(context)

            settings.reminderWeeks = 8
            settings.folderUri = "content://folder"
            settings.autoBackupToFolder = true
            settings.lastBackupAt = at
            settings.lastNotifiedBackupBaseline = at

            val reloaded = BackupSettings(context)
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
    fun recordBackupMade_setsLastBackupAt() {
        clear()
        try {
            val settings = BackupSettings(context)
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
            val settings = BackupSettings(context)
            settings.folderUri = "content://folder"

            settings.folderUri = null

            assertNull(BackupSettings(context).folderUri)
        } finally {
            clear()
        }
    }
}
