package app.restvolt.camperlog.ui.data

import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupError
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.decodeBackup
import app.restvolt.camperlog.backup.encodeBackup
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.share.CsvVocabulary
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeBackupImporter
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeChecklistTemplateRepository
import app.restvolt.camperlog.ui.FakeDiaryEntryRepository
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

private const val DEFAULT_VEHICLE_NAME = "Mein Wohnmobil"
private const val VEHICLE_UUID = "11111111-1111-4111-8111-111111111111"

@OptIn(ExperimentalCoroutinesApi::class)
class DataViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val files = FakeDataFiles()
    private val folderWriter = FakeBackupFolderWriter()
    private val backupSavedAt = mutableListOf<Instant>()

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
        notes = "",
        mapLink = null,
        createdAt = Instant.parse("2026-07-04T08:00:00Z"),
        updatedAt = Instant.parse("2026-07-04T08:00:00Z"),
    )

    @Test
    fun backupJson_containsToursRatesMainCurrencyAndVehicle() = runBlocking {
        val rate = ExchangeRate(nok, BigDecimal("11.5"), LocalDate.of(2026, 10, 1), "EZB")
        val exportedAt = Instant.parse("2026-10-04T12:00:00Z")
        val viewModel = DataViewModel(
            FakeTourRepository(listOf(tour)),
            FakeExchangeRateRepository(listOf(rate), mainCurrency = nok),
            FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1).copy(uuid = VEHICLE_UUID))),
            FakeLogRepository(),
            FakeStationRepository(),
            FakeVehicleDocumentRepository(),
            FakeDiaryEntryRepository(), FakeChecklistTemplateRepository(), FakeChecklistRepository(),
            FakeAttachmentRepository(),
            FakeAttachmentFileStore(),
            FakeBackupImporter(),
            files,
            folderWriter,
            clock = { exportedAt },
            background = dispatcher,
        )

        val backup = (decodeBackup(viewModel.backupJson()) as BackupReadResult.Success).backup

        assertEquals(exportedAt, backup.exportedAt)
        assertEquals(nok, backup.mainCurrency)
        assertEquals(listOf(rate.currency), backup.rates.map { it.currency })
        assertEquals(0, rate.perEuro.compareTo(backup.rates.single().perEuro))
        assertEquals(listOf(tour), backup.tours)
        assertEquals(mapOf(tour.uuid to VEHICLE_UUID), backup.tourVehicleUuid)
        assertEquals(listOf(VEHICLE_UUID), backup.vehicles.map { it.vehicle.uuid })
        assertEquals(VEHICLE_UUID, backup.currentVehicleUuid)
    }

    private val backupText = Backup(Instant.parse("2026-10-04T12:00:00Z"), nok, emptyList(), listOf(tour)).let(::encodeBackup)

    private fun viewModel(
        importer: FakeBackupImporter = FakeBackupImporter(),
        tours: List<Tour> = emptyList(),
        vocabulary: CsvVocabulary = CsvVocabulary.GERMAN,
    ) =
        DataViewModel(
            FakeTourRepository(tours),
            FakeExchangeRateRepository(),
            FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1, name = "Standard").copy(uuid = VEHICLE_UUID))),
            FakeLogRepository(),
            FakeStationRepository(),
            FakeVehicleDocumentRepository(),
            FakeDiaryEntryRepository(), FakeChecklistTemplateRepository(), FakeChecklistRepository(),
            FakeAttachmentRepository(),
            FakeAttachmentFileStore(),
            importer,
            files,
            folderWriter,
            onBackupSaved = { backupSavedAt += it },
            background = dispatcher,
            vocabulary = { vocabulary },
        ).also { files.sources["backup"] = backupText; files.sources["empty"] = "{}" }

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

        viewModel.exportCsv(DEFAULT_VEHICLE_NAME)

        assertEquals(ShareRequest.Csv("csv:1"), viewModel.share.value)
        assertEquals(1, files.csvExports.size)
        val export = files.csvExports.single()
        assertEquals(listOf(tour.copy(vehicleId = 1)), export.tours)
        assertEquals(mapOf(1L to "Standard"), export.vehicleNames)
        assertEquals(DEFAULT_VEHICLE_NAME, export.defaultVehicleName)
        assertEquals(CsvVocabulary.GERMAN, export.vocabulary)
        viewModel.shareHandled(started = false)
        assertNull(viewModel.share.value)
        assertEquals(DataMessage.Text(R.string.no_share_app), viewModel.message.value)
        viewModel.messageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun exportCsv_withEnglishVocabulary_passesItToTheCsvExport() {
        val viewModel = viewModel(tours = listOf(tour), vocabulary = CsvVocabulary.ENGLISH)

        viewModel.exportCsv(DEFAULT_VEHICLE_NAME)

        assertEquals(CsvVocabulary.ENGLISH, files.csvExports.single().vocabulary)
    }

    @Test
    fun exportCsv_withoutTours_reportsNothingToExport() {
        val viewModel = viewModel()

        viewModel.exportCsv(DEFAULT_VEHICLE_NAME)

        assertNull(viewModel.share.value)
        assertEquals(DataMessage.Text(R.string.export_nothing), viewModel.message.value)
        assertTrue(files.csvExports.isEmpty())
    }

    @Test
    fun exportStationsCsv_withStations_requestsShare() {
        val station = Station(
            vehicleId = 1,
            tourId = null,
            type = StationType.SIGHT,
            date = LocalDate.of(2026, 7, 2),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val repository = FakeTourRepository()
        val viewModel = DataViewModel(
            repository,
            FakeExchangeRateRepository(),
            FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1, name = "Standard").copy(uuid = VEHICLE_UUID))),
            FakeLogRepository(),
            FakeStationRepository(listOf(station)),
            FakeVehicleDocumentRepository(),
            FakeDiaryEntryRepository(), FakeChecklistTemplateRepository(), FakeChecklistRepository(),
            FakeAttachmentRepository(),
            FakeAttachmentFileStore(),
            FakeBackupImporter(),
            files,
            folderWriter,
            background = dispatcher,
            vocabulary = { CsvVocabulary.ENGLISH },
        )

        viewModel.exportStationsCsv(DEFAULT_VEHICLE_NAME)

        assertEquals(ShareRequest.StationsCsv("stations-csv:1"), viewModel.share.value)
        assertEquals(1, files.stationsCsvExports.size)
        val export = files.stationsCsvExports.single()
        assertEquals(listOf(station), export.stations)
        assertEquals(mapOf(1L to "Standard"), export.vehicleNames)
        assertEquals(DEFAULT_VEHICLE_NAME, export.defaultVehicleName)
        assertEquals(CsvVocabulary.ENGLISH, export.vocabulary)
    }

    @Test
    fun exportStationsCsv_withoutStations_reportsNothingToExport() {
        val viewModel = viewModel()

        viewModel.exportStationsCsv(DEFAULT_VEHICLE_NAME)

        assertNull(viewModel.share.value)
        assertEquals(DataMessage.Text(R.string.export_stations_nothing), viewModel.message.value)
        assertTrue(files.stationsCsvExports.isEmpty())
    }

    @Test
    fun saveBackup_writesTargetAndReportsSuccess() {
        val viewModel = viewModel(tours = listOf(tour))

        viewModel.saveBackup("content://target", includeFiles = false)

        val json = files.written.getValue("content://target")
        assertEquals(listOf(tour), (decodeBackup(json) as BackupReadResult.Success).backup.tours)
        assertEquals(DataMessage.Text(R.string.backup_saved), viewModel.message.value)
        assertEquals(1, backupSavedAt.size)
    }

    @Test
    fun saveBackup_withFilesAndLargeAttachment_streamsFileContentInsteadOfBufferingWholeArchive() {
        val fileStore = FakeAttachmentFileStore()
        val fileName = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1.jpg"
        val largeContent = ByteArray(300 * 1024) { (it % 256).toByte() }
        fileStore.file(fileName).apply { parentFile?.mkdirs() }.writeBytes(largeContent)
        val document = VehicleDocument(
            id = 1,
            uuid = "11111111-2222-4333-8444-555555555555",
            vehicleId = 1,
            kind = DocumentKind.OTHER,
            title = "Dokument",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val attachment = Attachment(
            id = 1,
            uuid = "22222222-3333-4444-8555-666666666666",
            ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
            ownerId = 1,
            fileName = fileName,
            mimeType = "image/jpeg",
            sizeBytes = largeContent.size.toLong(),
            createdAt = Instant.EPOCH,
        )
        val viewModel = DataViewModel(
            FakeTourRepository(listOf(tour)),
            FakeExchangeRateRepository(),
            FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1).copy(uuid = VEHICLE_UUID))),
            FakeLogRepository(),
            FakeStationRepository(),
            FakeVehicleDocumentRepository(initial = listOf(document)),
            FakeDiaryEntryRepository(), FakeChecklistTemplateRepository(), FakeChecklistRepository(),
            FakeAttachmentRepository(initial = listOf(attachment)),
            fileStore,
            FakeBackupImporter(),
            files,
            folderWriter,
            background = dispatcher,
        )

        viewModel.saveBackup("content://target", includeFiles = true)

        assertEquals(DataMessage.Text(R.string.backup_saved), viewModel.message.value)
        assertTrue(files.writtenZipChunkSizes.isNotEmpty())
        assertTrue(
            "the sink received a chunk as large as the whole attachment, the archive was buffered whole instead of streamed",
            files.writtenZipChunkSizes.all { it < largeContent.size },
        )
    }

    @Test
    fun saveBackup_writeFailure_reportsBackupFailed() {
        files.failure = IOException("voll")
        val viewModel = viewModel()

        viewModel.saveBackup("content://target", includeFiles = false)

        assertEquals(DataMessage.Text(R.string.backup_failed), viewModel.message.value)
        assertEquals(false, viewModel.busy.value)
        assertTrue(backupSavedAt.isEmpty())
    }

    @Test
    fun backUpToFolder_writesTimestampedBackupAndReportsFolderName() {
        folderWriter.folderName = "Sicherungen"
        val viewModel = viewModel(tours = listOf(tour))

        viewModel.backUpToFolder("content://folder")

        assertEquals(1, folderWriter.written.size)
        assertEquals(listOf(tour), (decodeBackup(folderWriter.written.single()) as BackupReadResult.Success).backup.tours)
        assertEquals(DataMessage.BackedUpToFolder("Sicherungen"), viewModel.message.value)
        assertEquals(1, backupSavedAt.size)
    }

    @Test
    fun backUpToFolder_inaccessibleFolder_reportsFailureWithoutWriting() {
        folderWriter.accessible = false
        val viewModel = viewModel(tours = listOf(tour))

        viewModel.backUpToFolder("content://folder")

        assertTrue(folderWriter.written.isEmpty())
        assertEquals(DataMessage.Text(R.string.data_backup_folder_failed), viewModel.message.value)
        assertTrue(backupSavedAt.isEmpty())
    }

    @Test
    fun shareBackup_whileBusy_isIgnored() {
        val gate = CompletableDeferred<Unit>()
        files.gate = gate
        val viewModel = viewModel()

        viewModel.shareBackup(includeFiles = false)
        viewModel.exportCsv(DEFAULT_VEHICLE_NAME)

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

/**
 * [BackupFolderWriter]-Fake: [folderName] und [accessible] steuern das Ergebnis, [written] sammelt die
 * geschriebenen Sicherungen als JSON-Text - bei einer ZIP-Sicherung wird dafür ihr `backup.json`-Eintrag
 * entpackt, damit bestehende Prüfungen unverändert mit [app.restvolt.camperlog.backup.decodeBackup] weiterlesen können.
 */
private class FakeBackupFolderWriter : BackupFolderWriter {
    var folderName: String? = "Ordner"
    var accessible: Boolean = true
    val written = mutableListOf<String>()

    override fun isAccessible(folderUri: String): Boolean = accessible

    override fun folderDisplayName(folderUri: String): String? = folderName

    override suspend fun writeTimestampedBackup(folderUri: String, json: String): String? {
        if (!accessible) return null
        written += json
        return "backup.json"
    }

    override suspend fun writeTimestampedBackupZip(folderUri: String, writeZip: (OutputStream) -> Unit): String? {
        if (!accessible) return null
        val buffer = ByteArrayOutputStream()
        writeZip(buffer)
        written += extractBackupJson(buffer.toByteArray())
        return "backup.zip"
    }
}

/** Entpackt den `backup.json`-Eintrag einer ZIP-Sicherung. */
private fun extractBackupJson(zipBytes: ByteArray): String {
    java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: error("backup.json nicht in der ZIP-Datei gefunden")
            if (entry.name == "backup.json") return zip.readBytes().toString(Charsets.UTF_8)
        }
    }
}

/** Ein über [FakeDataFiles.writeCsvExport] erfasster Export der Touren-CSV. */
private data class CsvExport(val tours: List<Tour>, val vehicleNames: Map<Long, String>, val defaultVehicleName: String, val vocabulary: CsvVocabulary)

/** Ein über [FakeDataFiles.writeStationsCsvExport] erfasster Export der Stationen-CSV. */
private data class StationsCsvExport(
    val stations: List<Station>,
    val vehicleNames: Map<Long, String>,
    val defaultVehicleName: String,
    val vocabulary: CsvVocabulary,
)

/** Speichert Exporte im Speicher; [gate] hält Exporte an, [failure] lässt Schreibzugriffe scheitern. */
private class FakeDataFiles : DataFiles {
    val sources = mutableMapOf<String, String>()
    val written = mutableMapOf<String, String>()
    val csvExports = mutableListOf<CsvExport>()
    val stationsCsvExports = mutableListOf<StationsCsvExport>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: IOException? = null
    private var backups = 0

    /** Größe jedes einzelnen `write`-Aufrufs beim letzten [writeBackupZip]; prüft, dass die ZIP-Sicherung gestreamt statt als Ganzes gepuffert wird. */
    val writtenZipChunkSizes = mutableListOf<Int>()

    override suspend fun writeCsvExport(
        tours: List<Tour>,
        stations: List<Station>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
        vocabulary: CsvVocabulary,
    ): String {
        gate?.await()
        failure?.let { throw it }
        csvExports += CsvExport(tours, vehicleNames, defaultVehicleName, vocabulary)
        return "csv:${csvExports.size}"
    }

    override suspend fun writeStationsCsvExport(
        stations: List<Station>,
        tourNames: Map<Long, String>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
        vocabulary: CsvVocabulary,
    ): String {
        gate?.await()
        failure?.let { throw it }
        stationsCsvExports += StationsCsvExport(stations, vehicleNames, defaultVehicleName, vocabulary)
        return "stations-csv:${stationsCsvExports.size}"
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

    override suspend fun writeBackupZipExport(writeZip: (OutputStream) -> Unit): String {
        gate?.await()
        failure?.let { throw it }
        return "backup-zip:${++backups}"
    }

    override suspend fun writeBackupZip(target: String, writeZip: (OutputStream) -> Unit) {
        failure?.let { throw it }
        val capture = ByteArrayOutputStream()
        writtenZipChunkSizes.clear()
        val tracking = object : OutputStream() {
            override fun write(b: Int) {
                capture.write(b)
                writtenZipChunkSizes += 1
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                capture.write(b, off, len)
                writtenZipChunkSizes += len
            }
        }
        writeZip(tracking)
        written[target] = extractBackupJson(capture.toByteArray())
    }

    override fun open(source: String): InputStream? = sources[source]?.byteInputStream()

    override fun newImportStagingDir(): File =
        java.nio.file.Files.createTempDirectory("camperlog-test-staging").toFile().also { stagingDirs += it }

    val stagingDirs = mutableListOf<File>()
}
