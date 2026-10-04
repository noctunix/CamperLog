package app.restvolt.camperlog.ui.data

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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class DataViewModelTest {

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
            clock = { exportedAt },
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
        DataViewModel(FakeTourRepository(tours), FakeExchangeRateRepository(), importer)

    @Test
    fun loadBackup_validFile_isPendingWithExistingTourCount() = runBlocking {
        val viewModel = viewModel(tours = listOf(tour.copy(id = 1), tour.copy(id = 2)))

        val failure = viewModel.loadBackup { backupText.byteInputStream() }

        assertNull(failure)
        val pending = viewModel.pendingImport.value!!
        assertEquals(listOf(tour), pending.backup.tours)
        assertEquals(2, pending.existingTours)
    }

    @Test
    fun loadBackup_invalidFile_reportsErrorAndClearsPending() = runBlocking {
        val viewModel = viewModel()
        viewModel.loadBackup { backupText.byteInputStream() }

        val failure = viewModel.loadBackup { "{}".byteInputStream() }

        assertEquals(BackupError.NOT_A_BACKUP, failure?.error)
        assertNull(viewModel.pendingImport.value)
    }

    @Test(expected = IOException::class)
    fun loadBackup_missingStream_throwsIOException(): Unit = runBlocking {
        viewModel().loadBackup { null }
    }

    @Test
    fun importPending_usesChosenModeAndClearsPending() = runBlocking {
        val importer = FakeBackupImporter()
        val viewModel = viewModel(importer)
        viewModel.loadBackup { backupText.byteInputStream() }

        val result = viewModel.importPending(ImportMode.REPLACE)

        assertEquals(1, result?.addedTours)
        assertEquals(listOf(ImportMode.REPLACE), importer.calls.map { it.second })
        assertNull(viewModel.pendingImport.value)
        assertNull(viewModel.importPending(ImportMode.MERGE))
    }

    @Test
    fun cancelImport_discardsWithoutImporting() = runBlocking {
        val importer = FakeBackupImporter()
        val viewModel = viewModel(importer)
        viewModel.loadBackup { backupText.byteInputStream() }

        viewModel.cancelImport()

        assertNull(viewModel.pendingImport.value)
        assertTrue(importer.calls.isEmpty())
    }
}
