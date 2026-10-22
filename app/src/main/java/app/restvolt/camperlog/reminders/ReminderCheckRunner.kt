package app.restvolt.camperlog.reminders

import app.restvolt.camperlog.backup.BackupPayload
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.domain.ReminderNotification
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.backupNotificationBaseline
import app.restvolt.camperlog.domain.documentReminders
import app.restvolt.camperlog.domain.dueReminders
import app.restvolt.camperlog.domain.pendingDocumentReminderNotifications
import app.restvolt.camperlog.domain.pendingReminderNotifications
import app.restvolt.camperlog.domain.shouldNotifyBackupOverdue
import app.restvolt.camperlog.domain.isBackupOverdue
import java.time.Instant
import java.time.LocalDate

/** Zur Sicherungs-Erinnerung gehörende Einstellungen für einen Lauf von [ReminderCheckRunner]. */
data class BackupReminderInput(
    val lastBackupAt: Instant?,
    val reminderWeeks: Int,
    val lastNotifiedBackupBaseline: Instant?,
    val autoBackupToFolder: Boolean,
    val folderUri: String?,
)

/**
 * Was ein Lauf von [ReminderCheckRunner] an Sicherungs-Zustand zu persistieren hat; `null` heißt
 * "unverändert". Getrennt von den Seiteneffekten (Benachrichtigungen, Dateischreiben), damit
 * [ReminderCheckRunner] mit Fakes vollständig ohne Android/Robolectric testbar bleibt.
 */
data class ReminderCheckOutcome(
    val backupWrittenAt: Instant? = null,
    val backupNotifiedBaseline: Instant? = null,
    val folderBackupAttempted: Boolean = false,
)

/**
 * Logik des täglichen Hintergrund-Checks, losgelöst von [androidx.work.CoroutineWorker]:
 * liest alle Fahrzeuge, ermittelt fällige Wartungserinnerungen mit [dueReminders] und
 * [pendingReminderNotifications] sowie fällige Dokument-Erinnerungen mit [documentReminders] und
 * [pendingDocumentReminderNotifications], benachrichtigt über [notifier] und aktualisiert
 * [notificationStore] bzw. [documentNotificationStore]. Ist die Sicherung überfällig, schreibt sie bei
 * aktivierter Automatik eine neue Sicherung in den gewählten Ordner, sonst benachrichtigt sie
 * stattdessen - nie beides, und nie stillschweigend nichts.
 */
class ReminderCheckRunner(
    private val vehicles: VehicleRepository,
    private val documents: VehicleDocumentRepository,
    private val notificationStore: ReminderNotificationStore,
    private val documentNotificationStore: DocumentReminderNotificationStore,
    private val notifier: ReminderNotifier,
    private val folderWriter: BackupFolderWriter,
    private val buildBackupPayload: suspend () -> BackupPayload,
) {
    suspend fun run(today: LocalDate, now: Instant, leadDays: Int, oilIntervalMonths: Int, backup: BackupReminderInput, notificationsEnabled: Boolean = true): ReminderCheckOutcome {
        if (!notificationsEnabled) return checkBackup(now, backup, notificationsEnabled = false)
        val allVehicles = vehicles.allVehicles()
        val documentsByVehicle = documents.allDocuments().groupBy { it.vehicleId }
        val pending = mutableListOf<ReminderNotification>()
        for (vehicle in allVehicles) {
            val reminders = dueReminders(vehicle, today, leadDays, oilIntervalMonths)
            val previous = notificationStore.statesFor(vehicle.id)
            val (dueNotifications, nextStates) = pendingReminderNotifications(vehicle.id, reminders, previous)
            notificationStore.saveStatesFor(vehicle.id, nextStates)
            pending += dueNotifications

            if (!vehicle.isSold) {
                val docReminders = documentReminders(documentsByVehicle[vehicle.id].orEmpty(), today, leadDays)
                val previousDocStates = documentNotificationStore.statesFor(vehicle.id)
                val (docNotifications, nextDocStates) = pendingDocumentReminderNotifications(vehicle.id, docReminders, previousDocStates)
                documentNotificationStore.saveStatesFor(vehicle.id, nextDocStates)
                pending += docNotifications
            }
        }
        if (pending.isNotEmpty()) {
            notifier.notifyReminders(pending, allVehicles.associate { it.id to it.name })
        }
        return checkBackup(now, backup)
    }

    private suspend fun checkBackup(now: Instant, backup: BackupReminderInput, notificationsEnabled: Boolean = true): ReminderCheckOutcome {
        if (!isBackupOverdue(backup.lastBackupAt, backup.reminderWeeks, now)) {
            return ReminderCheckOutcome()
        }
        val baseline = backupNotificationBaseline(backup.lastBackupAt)
        val folderUri = backup.folderUri
        val wroteAutomatically = backup.autoBackupToFolder && folderUri != null &&
            folderWriter.isAccessible(folderUri) &&
            run {
                val payload = buildBackupPayload()
                val writeZip = payload.writeZip
                val writtenName = if (writeZip != null) {
                    folderWriter.writeTimestampedBackupZip(folderUri, writeZip)
                } else {
                    folderWriter.writeTimestampedBackup(folderUri, payload.json)
                }
                writtenName != null
            }
        return (if (wroteAutomatically) {
            ReminderCheckOutcome(backupWrittenAt = now, backupNotifiedBaseline = baseline)
        } else if (notificationsEnabled && shouldNotifyBackupOverdue(backup.lastBackupAt, backup.reminderWeeks, now, backup.lastNotifiedBackupBaseline)) {
            notifier.notifyBackupOverdue()
            ReminderCheckOutcome(backupNotifiedBaseline = baseline)
        } else {
            ReminderCheckOutcome()
        }).copy(folderBackupAttempted = backup.autoBackupToFolder && folderUri != null)
    }
}
