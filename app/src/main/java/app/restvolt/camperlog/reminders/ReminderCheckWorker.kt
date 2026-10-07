package app.restvolt.camperlog.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.restvolt.camperlog.CamperLogApp
import app.restvolt.camperlog.backup.BackupPayload
import app.restvolt.camperlog.backup.backupZipSizeEstimate
import app.restvolt.camperlog.backup.buildBackup
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.backup.writeBackupZip
import app.restvolt.camperlog.data.AndroidBackupFolderWriter
import app.restvolt.camperlog.domain.shouldIncludeFilesInAutoBackup
import app.restvolt.camperlog.ui.data.BackupSettings
import app.restvolt.camperlog.ui.settings.NotificationSettings
import app.restvolt.camperlog.ui.theme.ReminderSettings
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Android-Anbindung des täglichen Hintergrund-Checks: liest die aktuellen Einstellungen und
 * delegiert die eigentliche Logik an [ReminderCheckRunner], die unabhängig von WorkManager testbar ist.
 */
class ReminderCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val notificationSettings = NotificationSettings(applicationContext)
        if (!notificationSettings.enabled) return Result.success()

        val app = applicationContext as CamperLogApp
        val reminderSettings = ReminderSettings(applicationContext)
        val backupSettings = BackupSettings(applicationContext)
        val prefs = backupSettings.values.value

        val runner = ReminderCheckRunner(
            vehicles = app.vehicles,
            documents = app.vehicleDocuments,
            notificationStore = AndroidReminderNotificationStore(applicationContext),
            documentNotificationStore = AndroidDocumentReminderNotificationStore(applicationContext),
            notifier = AndroidReminderNotifier(applicationContext),
            folderWriter = AndroidBackupFolderWriter(applicationContext),
            buildBackupPayload = {
                val backup = buildBackup(
                    app.repository, app.exchangeRates, app.vehicles, app.logbook, app.stations,
                    app.vehicleDocuments, app.diaryEntries, app.checklistTemplates, app.checklists, app.attachments, Instant.now(),
                )
                val json = encodeBackup(backup)
                val includeFiles = backupSettings.autoBackupIncludeFilesOverride
                    ?: shouldIncludeFilesInAutoBackup(backupZipSizeEstimate(json, backup.attachments))
                val writeZip: ((OutputStream) -> Unit)? = if (includeFiles) {
                    { output ->
                        writeBackupZip(output, json, backup.attachments, includeFiles = true) { fileName ->
                            app.attachmentFileStore.file(fileName).takeIf { it.exists() }?.inputStream()
                        }
                    }
                } else {
                    null
                }
                BackupPayload(json, writeZip)
            },
        )
        val outcome = runner.run(
            today = LocalDate.now(),
            now = Instant.now(),
            leadDays = reminderSettings.reminderLeadDays,
            oilIntervalMonths = reminderSettings.oilChangeIntervalMonths,
            backup = BackupReminderInput(
                lastBackupAt = prefs.lastBackupAt,
                reminderWeeks = prefs.reminderWeeks,
                lastNotifiedBackupBaseline = prefs.lastNotifiedBackupBaseline,
                autoBackupToFolder = prefs.autoBackupToFolder,
                folderUri = prefs.folderUri,
            ),
        )
        outcome.backupWrittenAt?.let { backupSettings.lastBackupAt = it }
        outcome.backupNotifiedBaseline?.let { backupSettings.lastNotifiedBackupBaseline = it }
        return Result.success()
    }
}
