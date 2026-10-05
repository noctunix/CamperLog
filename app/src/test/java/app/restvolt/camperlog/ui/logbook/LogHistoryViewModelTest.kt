package app.restvolt.camperlog.ui.logbook

import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.FakeLogRepository
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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LogHistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] in Tests aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    @Test
    fun entries_areNewestFirstAndIgnoreOtherVehiclesAndTypes() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        logs.add(1, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 1))
        val newest = logs.add(1, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 10))
        logs.add(1, LogType.GREY_WATER_EMPTIED, LocalDate.of(2026, 1, 20))
        logs.add(2, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 30))
        val viewModel = LogHistoryViewModel(logs, vehicleId = 1, type = LogType.CASSETTE_EMPTIED)
        collect(viewModel.uiState)

        val dates = viewModel.uiState.value.entries.map { it.date }

        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1)), dates)
        assertEquals(newest.id, viewModel.uiState.value.entries.first().id)
    }

    @Test
    fun delete_removesTheEntryAndUndoRestoresIt() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val entry = logs.add(1, LogType.DIESEL_HEATER_RUN, LocalDate.of(2026, 5, 1))
        val viewModel = LogHistoryViewModel(logs, vehicleId = 1, type = LogType.DIESEL_HEATER_RUN)
        collect(viewModel.uiState)

        viewModel.delete(entry)

        assertEquals(emptyList<Long>(), viewModel.uiState.value.entries.map { it.id })
        assertEquals(LogHistoryMessage.Deleted(entry), viewModel.message.value)

        viewModel.undoDelete(entry)

        assertEquals(listOf(entry.id), viewModel.uiState.value.entries.map { it.id })
    }
}
