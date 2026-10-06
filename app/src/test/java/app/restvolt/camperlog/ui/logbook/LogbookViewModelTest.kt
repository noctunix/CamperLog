package app.restvolt.camperlog.ui.logbook

import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.FakeLogRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 5)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] in Tests aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    @Test
    fun tiles_haveOneEntryPerTypeInFixedOrderAndMissingTypesAreNull() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository()
        logs.add(1, LogType.GAS_HEATER_RUN, today)
        logs.add(1, LogType.CASSETTE_EMPTIED, today.minusDays(3))
        val viewModel = LogbookViewModel(logs, vehicles) { today }
        collect(viewModel.uiState)

        val tiles = viewModel.uiState.value.tiles

        assertEquals(
            listOf(
                LogType.CASSETTE_EMPTIED, LogType.GREY_WATER_EMPTIED, LogType.DIESEL_HEATER_RUN,
                LogType.GAS_HEATER_RUN, LogType.GAS_BOTTLE_SWAPPED,
            ),
            tiles.map { it.type },
        )
        assertEquals(today.minusDays(3), tiles.first { it.type == LogType.CASSETTE_EMPTIED }.lastDate)
        assertNull(tiles.first { it.type == LogType.GREY_WATER_EMPTIED }.lastDate)
        assertEquals(today, tiles.first { it.type == LogType.GAS_HEATER_RUN }.lastDate)
    }

    @Test
    fun tiles_onlyReflectTheCurrentVehicle() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1), defaultVehicle(2)))
        logs.add(1, LogType.CASSETTE_EMPTIED, today)
        logs.add(2, LogType.CASSETTE_EMPTIED, today.minusDays(5))
        val viewModel = LogbookViewModel(logs, vehicles) { today }
        collect(viewModel.uiState)

        assertEquals(today, viewModel.uiState.value.tiles.first { it.type == LogType.CASSETTE_EMPTIED }.lastDate)

        vehicles.setCurrentVehicle(2)

        assertEquals(today.minusDays(5), viewModel.uiState.value.tiles.first { it.type == LogType.CASSETTE_EMPTIED }.lastDate)
    }

    @Test
    fun recordToday_addsAnEntryWithTodaysDateAndReportsIt() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository()
        val viewModel = LogbookViewModel(logs, vehicles) { today }
        collect(viewModel.uiState)

        viewModel.recordToday(LogType.CASSETTE_EMPTIED)

        val message = viewModel.message.value as LogbookMessage.Recorded
        assertEquals(today, message.entry.date)
        assertTrue(message.wasToday)
        assertEquals(today, viewModel.uiState.value.tiles.first { it.type == LogType.CASSETTE_EMPTIED }.lastDate)
    }

    @Test
    fun recordDate_withAnOlderDate_reportsItAsNotToday() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository()
        val viewModel = LogbookViewModel(logs, vehicles) { today }

        viewModel.recordDate(LogType.GREY_WATER_EMPTIED, today.minusDays(2))

        val message = viewModel.message.value as LogbookMessage.Recorded
        assertEquals(today.minusDays(2), message.entry.date)
        assertEquals(false, message.wasToday)
    }

    @Test
    fun undoRecord_removesTheEntryAgain() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository()
        val viewModel = LogbookViewModel(logs, vehicles) { today }
        collect(viewModel.uiState)
        viewModel.recordToday(LogType.DIESEL_HEATER_RUN)
        val entry = (viewModel.message.value as LogbookMessage.Recorded).entry

        viewModel.undoRecord(entry)

        assertNull(viewModel.uiState.value.tiles.first { it.type == LogType.DIESEL_HEATER_RUN }.lastDate)
    }

    @Test
    fun onMessageShown_clearsOnlyTheMessageItWasCalledFor() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val vehicles = FakeVehicleRepository()
        val viewModel = LogbookViewModel(logs, vehicles) { today }
        viewModel.recordToday(LogType.CASSETTE_EMPTIED)
        val stale = viewModel.message.value as LogbookMessage.Recorded
        viewModel.recordToday(LogType.GREY_WATER_EMPTIED)

        viewModel.onMessageShown(stale)

        assertTrue(viewModel.message.value is LogbookMessage.Recorded)
    }
}
