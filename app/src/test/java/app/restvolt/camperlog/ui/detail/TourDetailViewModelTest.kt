package app.restvolt.camperlog.ui.detail

import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeCountryLookupRepository
import app.restvolt.camperlog.ui.FakeDiaryEntryRepository
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/** Prüft, dass [TourDetailViewModel.uiState] die Bordbuch-Einträge rein über das Datum der Tour zuordnet. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TourDetailViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] im Test aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun tour(id: Long = 1, vehicleId: Long = 1, endDate: LocalDate? = LocalDate.of(2026, 7, 10)) = Tour(
        id = id,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = endDate,
        destination = "Gardasee",
        tourType = TourType.WEEKEND,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 420,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun logEntry(id: Long, vehicleId: Long, date: LocalDate) = LogEntry(
        id = id,
        uuid = "log-$id",
        vehicleId = vehicleId,
        type = LogType.CASSETTE_EMPTIED,
        date = date,
        createdAt = Instant.EPOCH,
    )

    private fun viewModel(
        tours: FakeTourRepository,
        logs: FakeLogRepository,
        tourId: Long = 1,
        today: () -> LocalDate = { LocalDate.of(2026, 7, 15) },
    ) = TourDetailViewModel(
        tours,
        FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A"))),
        FakeStationRepository(),
        FakeDiaryEntryRepository(),
        FakeChecklistRepository(),
        logs,
        FakeExchangeRateRepository(),
        FakeCountryLookupRepository(),
        FakeAttachmentRepository(),
        FakeAttachmentFileStore(),
        object : TourExportFiles {
            override suspend fun writeTourExportZip(baseName: String, writeZip: (OutputStream) -> Unit): String = ""
        },
        FakeTrackRepository(),
        tourId,
        today = today,
    )

    @Test
    fun logEntriesInDateRangeOfTheSameVehicleAreIncluded() = runTest {
        val tours = FakeTourRepository(listOf(tour()))
        val inRange = logEntry(1, vehicleId = 1, date = LocalDate.of(2026, 7, 5))
        val beforeStart = logEntry(2, vehicleId = 1, date = LocalDate.of(2026, 6, 30))
        val afterEnd = logEntry(3, vehicleId = 1, date = LocalDate.of(2026, 7, 11))
        val otherVehicle = logEntry(4, vehicleId = 2, date = LocalDate.of(2026, 7, 5))
        val logs = FakeLogRepository(listOf(inRange, beforeStart, afterEnd, otherVehicle))
        val viewModel = viewModel(tours, logs)
        collect(viewModel.uiState)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as DetailUiState.Loaded
        assertEquals(listOf(inRange.id), loaded.logEntries.map { it.id })
    }

    @Test
    fun runningTourWithoutEndDateUsesTodayAsUpperBound() = runTest {
        val tours = FakeTourRepository(listOf(tour(endDate = null)))
        val withinToday = logEntry(1, vehicleId = 1, date = LocalDate.of(2026, 7, 15))
        val afterToday = logEntry(2, vehicleId = 1, date = LocalDate.of(2026, 7, 16))
        val logs = FakeLogRepository(listOf(withinToday, afterToday))
        val viewModel = viewModel(tours, logs, today = { LocalDate.of(2026, 7, 15) })
        collect(viewModel.uiState)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as DetailUiState.Loaded
        assertEquals(listOf(withinToday.id), loaded.logEntries.map { it.id })
    }

    @Test
    fun newlyAddedMatchingEntryAppearsLive() = runTest {
        val tours = FakeTourRepository(listOf(tour()))
        val logs = FakeLogRepository()
        val viewModel = viewModel(tours, logs)
        collect(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), (viewModel.uiState.value as DetailUiState.Loaded).logEntries.map { it.id })

        logs.add(1, LogType.GREY_WATER_EMPTIED, LocalDate.of(2026, 7, 6))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as DetailUiState.Loaded
        assertEquals(1, loaded.logEntries.size)
        assertEquals(LogType.GREY_WATER_EMPTIED, loaded.logEntries.single().type)
    }
}
