package app.restvolt.camperlog.ui.vehicle

import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] in Tests aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    @Test
    fun logbookLatest_containsTheExpectedEntriesForTheCurrentVehicle() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        logs.add(1, LogType.CASSETTE_EMPTIED, today.minusDays(2))
        logs.add(1, LogType.GAS_HEATER_RUN, today)
        val viewModel = VehicleViewModel(
            FakeVehicleRepository(),
            FakeVehicleDocumentRepository(),
            FakeChecklistRepository(),
            logs,
        )
        collect(viewModel.uiState)

        val logbookLatest = viewModel.uiState.value.logbookLatest

        assertEquals(today.minusDays(2), logbookLatest[LogType.CASSETTE_EMPTIED])
        assertEquals(today, logbookLatest[LogType.GAS_HEATER_RUN])
        assertNull(logbookLatest[LogType.GREY_WATER_EMPTIED])
    }

    @Test
    fun logbookLatest_updatesLiveWhenANewEntryIsRecorded() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val viewModel = VehicleViewModel(
            FakeVehicleRepository(),
            FakeVehicleDocumentRepository(),
            FakeChecklistRepository(),
            logs,
        )
        collect(viewModel.uiState)
        assertNull(viewModel.uiState.value.logbookLatest[LogType.CASSETTE_EMPTIED])

        logs.add(1, LogType.CASSETTE_EMPTIED, today)

        assertEquals(today, viewModel.uiState.value.logbookLatest[LogType.CASSETTE_EMPTIED])
    }

    @Test
    fun logbookLatest_doesNotShowEntriesOfAnotherVehicle() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        logs.add(2, LogType.CASSETTE_EMPTIED, today)
        val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1), defaultVehicle(2)))
        val viewModel = VehicleViewModel(vehicles, FakeVehicleDocumentRepository(), FakeChecklistRepository(), logs)
        collect(viewModel.uiState)

        assertNull(viewModel.uiState.value.logbookLatest[LogType.CASSETTE_EMPTIED])

        vehicles.setCurrentVehicle(2)

        assertEquals(today, viewModel.uiState.value.logbookLatest[LogType.CASSETTE_EMPTIED])
    }
}
