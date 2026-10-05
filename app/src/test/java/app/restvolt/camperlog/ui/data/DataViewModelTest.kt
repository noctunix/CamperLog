package app.restvolt.camperlog.ui.data

import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupError
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.decodeBackup
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeBackupImporter
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class DataViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val files = FakeDataFiles()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()


    private val nok = Currency.getInstance("NOK")

    private val tour = Tour(
        uuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60",
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 3),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 800,
        costs = listOf(Money(320000, nok)),
        pitchAssigned = false,
        electricityFlatRate = ElectricityFlatRate.NOT_USED,
        lteQuality = LteQuality.OK,
        pitchSlope = PitchSlope.SLOPED,
        levelingBlocksUsed = false,
        notes = "",
        mapLink = null,
        createdAt = Instant.parse("2026-07-04T08:00:00Z"),
        updatedAt = Instant.parse("2026-07-04T08:00:00Z"),
    )

    @Test
    fun backupJson_containsToursRatesAndMainCurrency() = runBlocking {
        val rate = ExchangeRate(nok, BigDecimal("11.5"), LocalDate.of(2026, 10, 1), "EZB")
        val exportedAt = Instant.parse("2026-10-04T12:00:00Z")
        val viewModel = DataViewModel(
            FakeTourRepository(listOf(tour)),
            FakeExchangeRateRepository(listOf(rate), mainCurrency = nok),
            FakeBackupImporter(),
            files,
            clock = { exportedAt },
            background = dispatcher,
        )

        val backup = (decodeBackup(viewModel.backupJson()) as BackupReadResult.Success).backup

        assertEquals(exportedAt, backup.exportedAt)
        assertEquals(nok, backup.mainCurrency)
        assertEquals(listOf(rate.currency), backup.rates.map { it.currency })
        assertEquals(0, rate.perEuro.compareTo(backup.rates.single().perEuro))
        assertEquals(listOf(tour), backup.tours)
    }

    private val backupText = Backup(Instant.parse("2026-10-04T12:00:00Z"), nok, emptyList(), listOf(tour)).let(::encodeBackup)

    private fun viewModel(importer: FakeBackupImporter = FakeBackupImporter(), tours: List<Tour> = emptyList()) =
        DataViewModel(FakeTourRepository(tours), FakeExchangeRateRepository(), importer, files, background = dispatcher)
            .also { files.sources["backup"] = backupText; files.sources["empty"] = "{}" }

    @Test
    fun loadBackup_validFile_isPendingWithExistingTourCount() = runBlocking {
        val viewModel = viewModel(tours = listOf(tour.copy(id = 1), tour.copy(id = 2)))

        viewModel.loadBackup("backup")

        assertNull(viewModel.message.value)
        assertEquals(false, viewModel.busy.value)
        val pending = viewModel.pendingImport.value!!
        assertEquals(listOf(tour), pending.backup.tours)
        assertEquals(2, pending.existingTours)
    }

    @Test
    fun loadBackup_invalidFile_reportsErrorAndClearsPending() = runBlocking {
        val viewModel = viewModel()
        viewModel.loadBackup("backup")

        viewModel.loadBackup("empty")

        val message = viewModel.message.value as DataMessage.LoadFailed
        assertEquals(BackupError.NOT_A_BACKUP, message.failure.error)
        assertNull(viewModel.pendingImport.value)
    }

    @Test
    fun loadBackup_missingStream_reportsUnreadable() {
        val viewModel = viewModel()

        viewModel.loadBackup("missing")

        assertEquals(DataMessage.Text(R.string.import_unreadable), viewModel.message.value)
        assertNull(viewModel.pendingImport.value)
        assertEquals(false, viewModel.busy.value)
    }

    @Test
    fun exportCsv_withTours_requestsShareAndReportsMissingApp() {
        val viewModel = viewModel(tours = listOf(tour))

        viewModel.exportCsv()

        assertEquals(ShareRequest.Csv("csv:1"), viewModel.share.value)
        assertEquals(listOf(listOf(tour.copy(vehicleId = 1))), files.csvExports)
        viewModel.shareHandled(started = false)
        assertNull(viewModel.share.value)
        assertEquals(DataMessage.Text(R.string.no_share_app), viewModel.message.value)
        viewModel.messageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun exportCsv_withoutTours_reportsNothingToExport() {
        val viewModel = viewModel()

        viewModel.exportCsv()

        assertNull(viewModel.share.value)
        assertEquals(DataMessage.Text(R.string.export_nothing), viewModel.message.value)
        assertTrue(files.csvExports.isEmpty())
    }

    @Test
    fun saveBackup_writesTargetAndReportsSuccess() {
        val viewModel = viewModel(tours = listOf(tour))

        viewModel.saveBackup("content://target")

        val json = files.written.getValue("content://target")
        assertEquals(listOf(tour), (decodeBackup(json) as BackupReadResult.Success).backup.tours)
        assertEquals(DataMessage.Text(R.string.backup_saved), viewModel.message.value)
    }

    @Test
    fun saveBackup_writeFailure_reportsBackupFailed() {
        files.failure = IOException("voll")
        val viewModel = viewModel()

        viewModel.saveBackup("content://target")

        assertEquals(DataMessage.Text(R.string.backup_failed), viewModel.message.value)
        assertEquals(false, viewModel.busy.value)
    }

    @Test
    fun shareBackup_whileBusy_isIgnored() {
        val gate = CompletableDeferred<Unit>()
        files.gate = gate
        val viewModel = viewModel()

        viewModel.shareBackup()
        viewModel.exportCsv()

        assertTrue(viewModel.busy.value)
        gate.complete(Unit)
        assertEquals(ShareRequest.Backup("backup:1"), viewModel.share.value)
        assertNull(viewModel.message.value)
        assertEquals(false, viewModel.busy.value)
    }

    @Test
    fun startImport_usesChosenModeAndPublishesResult() = runBlocking {
        val importer = FakeBackupImporter()
        val viewModel = viewModel(importer)
        viewModel.loadBackup("backup")

        viewModel.startImport(ImportMode.REPLACE)

        assertEquals(1, (viewModel.message.value as DataMessage.Imported).result.addedTours)
        assertEquals(listOf(ImportMode.REPLACE), importer.calls.map { it.second })
        assertNull(viewModel.pendingImport.value)
        viewModel.messageShown()
        assertNull(viewModel.message.value)
        viewModel.startImport(ImportMode.MERGE)
        assertEquals(1, importer.calls.size)
    }

    @Test
    fun startImport_whileRunning_ignoresRepeatAndCancel() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val importer = FakeBackupImporter().apply { this.gate = gate }
        val viewModel = viewModel(importer)
        viewModel.loadBackup("backup")

        viewModel.startImport(ImportMode.MERGE)
        viewModel.startImport(ImportMode.MERGE)
        viewModel.cancelImport()

        assertTrue(viewModel.pendingImport.value!!.running)
        assertEquals(1, importer.calls.size)
        gate.complete(Unit)
        assertNull(viewModel.pendingImport.value)
        assertEquals(1, (viewModel.message.value as DataMessage.Imported).result.addedTours)
    }

    @Test
    fun startImport_failure_keepsBackupForRetry() = runBlocking {
        val importer = FakeBackupImporter().apply { failure = IOException("disk") }
        val viewModel = viewModel(importer)
        viewModel.loadBackup("backup")

        viewModel.startImport(ImportMode.MERGE)

        val pending = viewModel.pendingImport.value!!
        assertTrue(pending.failed)
        assertEquals(false, pending.running)
        assertNull(viewModel.message.value)
    }

    @Test
    fun cancelImport_discardsWithoutImporting() = runBlocking {
        val importer = FakeBackupImporter()
        val viewModel = viewModel(importer)
        viewModel.loadBackup("backup")

        viewModel.cancelImport()

        assertNull(viewModel.pendingImport.value)
        assertTrue(importer.calls.isEmpty())
    }
}

/** Speichert Exporte im Speicher; [gate] hält Exporte an, [failure] lässt Schreibzugriffe scheitern. */
private class FakeDataFiles : DataFiles {
    val sources = mutableMapOf<String, String>()
    val written = mutableMapOf<String, String>()
    val csvExports = mutableListOf<List<Tour>>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: IOException? = null
    private var backups = 0

    override suspend fun writeCsvExport(tours: List<Tour>): String {
        gate?.await()
        failure?.let { throw it }
        csvExports += tours
        return "csv:${csvExports.size}"
    }

    override suspend fun writeBackupExport(json: String): String {
        gate?.await()
        failure?.let { throw it }
        return "backup:${++backups}"
    }

    override suspend fun writeBackup(target: String, json: String) {
        failure?.let { throw it }
        written[target] = json
    }

    override fun open(source: String): InputStream? = sources[source]?.byteInputStream()
}
