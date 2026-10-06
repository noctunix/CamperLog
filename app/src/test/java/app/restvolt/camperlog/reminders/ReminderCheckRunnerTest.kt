package app.restvolt.camperlog.reminders

import app.restvolt.camperlog.backup.BackupPayload
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.domain.ReminderKind
import app.restvolt.camperlog.domain.ReminderNotification
import app.restvolt.camperlog.domain.ReminderNotificationState
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 6)
private val NOW = Instant.parse("2026-10-06T08:00:00Z")
private val DUE_SOON = TODAY.plusDays(5)
private const val LEAD_DAYS = 30
private const val OIL_INTERVAL_MONTHS = 12
private val BACKUP_OFF = BackupReminderInput(lastBackupAt = NOW, reminderWeeks = 0, lastNotifiedBackupBaseline = null, autoBackupToFolder = false, folderUri = null)

class ReminderCheckRunnerTest {

    private fun runner(
        vehicles: FakeVehicleRepository,
        documents: FakeVehicleDocumentRepository = FakeVehicleDocumentRepository(),
        notificationStore: ReminderNotificationStore = FakeReminderNotificationStore(),
        documentNotificationStore: DocumentReminderNotificationStore = FakeDocumentReminderNotificationStore(),
        notifier: FakeReminderNotifier = FakeReminderNotifier(),
        folderWriter: BackupFolderWriter = FakeBackupFolderWriter(),
    ) = ReminderCheckRunner(
        vehicles, documents, notificationStore, documentNotificationStore, notifier, folderWriter,
        buildBackupPayload = { BackupPayload("{}", null) },
    ) to notifier

    @Test
    fun severalDueItems_notifyAsOneCallWithAllOfThem() = runBlocking {
        val vehicle = defaultVehicle(id = 1).copy(
            nextInspectionDate = DUE_SOON,
            nextGasCheckDate = TODAY.minusDays(2),
        )
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle))
        val (runner, notifier) = runner(vehicles)

        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertEquals(1, notifier.reminderCalls.size)
        assertEquals(2, notifier.reminderCalls.single().size)
        val kinds = notifier.reminderCalls.single().map { it.kind }.toSet()
        assertEquals(setOf(ReminderKind.INSPECTION, ReminderKind.GAS_CHECK), kinds)
    }

    @Test
    fun repeatedRuns_doNotNotifyAgain() = runBlocking {
        val vehicle = defaultVehicle(id = 1).copy(nextInspectionDate = DUE_SOON)
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle))
        val (runner, notifier) = runner(vehicles)

        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)
        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertEquals(1, notifier.reminderCalls.size)
    }

    @Test
    fun dueDateChange_rearmsTheNotification() = runBlocking {
        val vehicle = defaultVehicle(id = 1).copy(nextInspectionDate = DUE_SOON)
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle))
        val (runner, notifier) = runner(vehicles)
        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)
        assertEquals(1, notifier.reminderCalls.size)

        vehicles.save(vehicle.copy(nextInspectionDate = DUE_SOON.plusDays(10)))
        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertEquals(2, notifier.reminderCalls.size)
    }

    @Test
    fun soldVehicles_areIgnored() = runBlocking {
        val sold = defaultVehicle(id = 1, sold = true).copy(nextInspectionDate = DUE_SOON)
        val vehicles = FakeVehicleRepository(initial = listOf(sold))
        val (runner, notifier) = runner(vehicles)

        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertTrue(notifier.reminderCalls.isEmpty())
    }

    @Test
    fun documentExpiry_notifiesSeparatelyPerDocument() = runBlocking {
        val vehicle = defaultVehicle(id = 1)
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle))
        val documents = FakeVehicleDocumentRepository(
            initial = listOf(
                document(id = 1, vehicleId = 1, title = "Registration", expiryDate = DUE_SOON),
                document(id = 2, vehicleId = 1, title = "Insurance", expiryDate = TODAY.minusDays(1)),
            ),
        )
        val (runner, notifier) = runner(vehicles, documents = documents)

        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertEquals(1, notifier.reminderCalls.size)
        val notifications = notifier.reminderCalls.single()
        assertEquals(2, notifications.size)
        assertEquals(setOf("Registration", "Insurance"), notifications.map { it.label }.toSet())
        assertTrue(notifications.all { it.kind == ReminderKind.DOCUMENT_EXPIRY })
    }

    @Test
    fun documentExpiry_repeatedRunDoesNotNotifyAgain() = runBlocking {
        val vehicle = defaultVehicle(id = 1)
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle))
        val documents = FakeVehicleDocumentRepository(initial = listOf(document(id = 1, vehicleId = 1, expiryDate = DUE_SOON)))
        val (runner, notifier) = runner(vehicles, documents = documents)

        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)
        runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertEquals(1, notifier.reminderCalls.size)
    }

    @Test
    fun backupOverdue_withAccessibleAutoFolder_writesInsteadOfNotifying() = runBlocking {
        val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1)))
        val folderWriter = FakeBackupFolderWriter(accessible = true)
        val notifier = FakeReminderNotifier()
        val runner = ReminderCheckRunner(
            vehicles, FakeVehicleDocumentRepository(), FakeReminderNotificationStore(), FakeDocumentReminderNotificationStore(),
            notifier, folderWriter, buildBackupPayload = { BackupPayload("{\"ok\":true}", null) },
        )
        val backup = BackupReminderInput(lastBackupAt = null, reminderWeeks = 4, lastNotifiedBackupBaseline = null, autoBackupToFolder = true, folderUri = "content://folder")

        val outcome = runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, backup)

        assertEquals(NOW, outcome.backupWrittenAt)
        assertEquals(1, folderWriter.written.size)
        assertEquals(0, notifier.backupOverdueCalls)
    }

    @Test
    fun backupOverdue_withoutAccessibleFolder_notifiesInstead() = runBlocking {
        val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1)))
        val folderWriter = FakeBackupFolderWriter(accessible = false)
        val notifier = FakeReminderNotifier()
        val runner = ReminderCheckRunner(
            vehicles, FakeVehicleDocumentRepository(), FakeReminderNotificationStore(), FakeDocumentReminderNotificationStore(),
            notifier, folderWriter, buildBackupPayload = { BackupPayload("{}", null) },
        )
        val backup = BackupReminderInput(lastBackupAt = null, reminderWeeks = 4, lastNotifiedBackupBaseline = null, autoBackupToFolder = true, folderUri = "content://folder")

        val outcome = runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, backup)

        assertNull(outcome.backupWrittenAt)
        assertEquals(1, notifier.backupOverdueCalls)
        assertTrue(folderWriter.written.isEmpty())
    }

    @Test
    fun backupNotOverdue_doesNothing() = runBlocking {
        val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1)))
        val notifier = FakeReminderNotifier()
        val runner = ReminderCheckRunner(
            vehicles, FakeVehicleDocumentRepository(), FakeReminderNotificationStore(), FakeDocumentReminderNotificationStore(),
            notifier, FakeBackupFolderWriter(), buildBackupPayload = { BackupPayload("{}", null) },
        )

        val outcome = runner.run(TODAY, NOW, LEAD_DAYS, OIL_INTERVAL_MONTHS, BACKUP_OFF)

        assertNull(outcome.backupWrittenAt)
        assertNull(outcome.backupNotifiedBaseline)
        assertEquals(0, notifier.backupOverdueCalls)
    }

    private fun document(
        id: Long,
        vehicleId: Long,
        title: String = "Document",
        expiryDate: LocalDate?,
    ) = app.restvolt.camperlog.domain.VehicleDocument(
        id = id,
        uuid = "document-$id",
        vehicleId = vehicleId,
        kind = app.restvolt.camperlog.domain.DocumentKind.OTHER,
        title = title,
        expiryDate = expiryDate,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

private class FakeReminderNotificationStore : ReminderNotificationStore {
    private val byVehicle = mutableMapOf<Long, Map<ReminderKind, ReminderNotificationState>>()

    override fun statesFor(vehicleId: Long): Map<ReminderKind, ReminderNotificationState> = byVehicle[vehicleId] ?: emptyMap()

    override fun saveStatesFor(vehicleId: Long, states: Map<ReminderKind, ReminderNotificationState>) {
        byVehicle[vehicleId] = states
    }
}

private class FakeDocumentReminderNotificationStore : DocumentReminderNotificationStore {
    private val byVehicle = mutableMapOf<Long, Map<Long, ReminderNotificationState>>()

    override fun statesFor(vehicleId: Long): Map<Long, ReminderNotificationState> = byVehicle[vehicleId] ?: emptyMap()

    override fun saveStatesFor(vehicleId: Long, states: Map<Long, ReminderNotificationState>) {
        byVehicle[vehicleId] = states
    }
}

private class FakeReminderNotifier : ReminderNotifier {
    val reminderCalls = mutableListOf<List<ReminderNotification>>()
    var backupOverdueCalls = 0

    override fun notifyReminders(notifications: List<ReminderNotification>, vehicleNames: Map<Long, String>) {
        reminderCalls += notifications
    }

    override fun notifyBackupOverdue() {
        backupOverdueCalls++
    }
}

private class FakeBackupFolderWriter(var accessible: Boolean = true) : BackupFolderWriter {
    val written = mutableListOf<String>()

    override fun isAccessible(folderUri: String): Boolean = accessible

    override fun folderDisplayName(folderUri: String): String? = if (accessible) "Ordner" else null

    override suspend fun writeTimestampedBackup(folderUri: String, json: String): String? {
        if (!accessible) return null
        written += json
        return "backup.json"
    }

    override suspend fun writeTimestampedBackupZip(folderUri: String, zipBytes: ByteArray): String? {
        if (!accessible) return null
        written += "zip:${zipBytes.size}"
        return "backup.zip"
    }
}
