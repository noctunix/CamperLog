package app.restvolt.camperlog.ui.data

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.reminders.ReminderWorkScheduler
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeBackupImporter
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.settings.NotificationSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class NoOpReminderWorkScheduler : ReminderWorkScheduler {
    override fun enqueue() = Unit
    override fun cancel() = Unit
}

/** [BackupFolderWriter]-Fake für den Daten-Screen: [folderName] und [accessible] steuern das Ergebnis. */
private class FakeScreenBackupFolderWriter(var folderName: String? = "Sicherungen", var accessible: Boolean = true) : BackupFolderWriter {
    val written = mutableListOf<String>()

    override fun isAccessible(folderUri: String): Boolean = accessible

    override fun folderDisplayName(folderUri: String): String? = folderName

    override suspend fun writeTimestampedBackup(folderUri: String, json: String): String? {
        if (!accessible) return null
        written += json
        return "backup.json"
    }

    override suspend fun writeTimestampedBackupZip(folderUri: String, writeZip: (java.io.OutputStream) -> Unit): String? {
        if (!accessible) return null
        val buffer = java.io.ByteArrayOutputStream()
        writeZip(buffer)
        written += "zip:${buffer.size()}"
        return "backup.zip"
    }
}

/** Abläufe des Daten-Screens rund um den Sicherungsordner und die In-App-Sicherungs-Erinnerung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class DataScreenFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clearPreferences() {
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start(folderWriter: FakeScreenBackupFolderWriter = FakeScreenBackupFolderWriter(), notificationsEnabled: Boolean = false): BackupSettings {
        clearPreferences()
        val backupSettings = BackupSettings(context)
        val notificationSettings = NotificationSettings(context, NoOpReminderWorkScheduler())
        notificationSettings.enabled = notificationsEnabled
        compose.setContent {
            CamperLogTheme {
                DataScreen(
                    viewModel = viewModel {
                        DataViewModel(
                            FakeTourRepository(),
                            FakeExchangeRateRepository(),
                            FakeVehicleRepository(),
                            FakeLogRepository(),
                            FakeStationRepository(),
                            FakeVehicleDocumentRepository(),
                            FakeAttachmentRepository(),
                            FakeAttachmentFileStore(),
                            FakeBackupImporter(),
                            NoOpDataFiles,
                            folderWriter,
                            // Unconfined statt des echten Dispatchers.IO: der Compose-Test steuert sonst nur die
                            // virtuelle Zeit des Main-Dispatchers, nicht einen echten Thread-Wechsel.
                            background = Dispatchers.Unconfined,
                        )
                    },
                    backupSettings = backupSettings,
                    notificationSettings = notificationSettings,
                    folderWriter = folderWriter,
                    onBack = {},
                )
            }
        }
        return backupSettings
    }

    @Test
    fun chosenFolder_showsItsNameAndBacksUpOnTap() {
        val folderWriter = FakeScreenBackupFolderWriter(folderName = "Sicherungen")
        val backupSettings = start(folderWriter)
        backupSettings.folderUri = "content://folder"
        compose.waitForIdle()

        // Der Ordnername wird außerhalb von Compose ermittelt; waitForIdle allein reicht dafür nicht.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Ordner: Sicherungen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Jetzt sichern").performClick()
        compose.waitForIdle()

        assertEquals(1, folderWriter.written.size)
        compose.onNode(hasText("Sicherung in Sicherungen gespeichert", substring = true)).assertExists()
    }

    @Test
    fun overdueCard_shownOnlyWhileNotificationsAreOff() {
        start(notificationsEnabled = false)

        compose.onNode(hasText("Du hast länger nicht gesichert", substring = true)).assertExists()
    }

    @Test
    fun overdueCard_hiddenWhileNotificationsAreOn() {
        start(notificationsEnabled = true)

        compose.onNode(hasText("Du hast länger nicht gesichert", substring = true)).assertDoesNotExist()
    }
}

/** [DataFiles]-Fake ohne Funktion: Der Daten-Screen ruft sie in diesen Tests nie auf. */
private object NoOpDataFiles : DataFiles {
    override suspend fun writeCsvExport(
        tours: List<app.restvolt.camperlog.domain.Tour>,
        stations: List<app.restvolt.camperlog.domain.Station>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String = error("not used in this test")

    override suspend fun writeStationsCsvExport(
        stations: List<app.restvolt.camperlog.domain.Station>,
        tourNames: Map<Long, String>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String = error("not used in this test")

    override suspend fun writeBackupExport(json: String): String = error("not used in this test")

    override suspend fun writeBackup(target: String, json: String) = error("not used in this test")

    override suspend fun writeBackupZipExport(writeZip: (java.io.OutputStream) -> Unit): String = error("not used in this test")

    override suspend fun writeBackupZip(target: String, writeZip: (java.io.OutputStream) -> Unit) = error("not used in this test")

    override fun open(source: String): java.io.InputStream? = error("not used in this test")

    override fun newImportStagingDir(): java.io.File = error("not used in this test")
}
