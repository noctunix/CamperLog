package app.restvolt.camperlog.ui.logbook

import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
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
import java.time.Instant
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
        val viewModel = LogHistoryViewModel(logs, FakeStationRepository(), vehicleId = 1, type = LogType.CASSETTE_EMPTIED)
        collect(viewModel.uiState)

        val dates = viewModel.uiState.value.entries.map { it.date }

        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1)), dates)
        assertEquals(newest.id, viewModel.uiState.value.entries.first().id)
    }

    @Test
    fun delete_removesAnUnlinkedEntryAndUndoRestoresIt() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val entry = logs.add(1, LogType.DIESEL_HEATER_RUN, LocalDate.of(2026, 5, 1))
        val viewModel = LogHistoryViewModel(logs, FakeStationRepository(), vehicleId = 1, type = LogType.DIESEL_HEATER_RUN)
        collect(viewModel.uiState)

        viewModel.delete(entry)

        assertEquals(emptyList<Long>(), viewModel.uiState.value.entries.map { it.id })
        assertEquals(LogHistoryMessage.Deleted(entry, station = null), viewModel.message.value)

        viewModel.undoDelete(viewModel.message.value as LogHistoryMessage.Deleted)

        assertEquals(listOf(entry.id), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun delete_withAStationLinkedEntryAndAnotherServiceLeft_removesOnlyThatServiceAndUndoRestoresBoth() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val stations = FakeStationRepository(logs = logs)
        val date = LocalDate.of(2026, 5, 1)
        val station = Station(
            vehicleId = 1,
            type = StationType.SUPPLY,
            date = date,
            services = setOf(StationService.CASSETTE, StationService.GREY_WATER),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val stationId = stations.save(station)
        val cassetteEntry = logs.entries.single { it.type == LogType.CASSETTE_EMPTIED }
        val greyWaterEntry = logs.entries.single { it.type == LogType.GREY_WATER_EMPTIED }
        val viewModel = LogHistoryViewModel(logs, stations, vehicleId = 1, type = LogType.CASSETTE_EMPTIED)
        collect(viewModel.uiState)

        viewModel.delete(cassetteEntry)

        assertEquals(emptyList<Long>(), viewModel.uiState.value.entries.map { it.id })
        assertEquals(setOf(StationService.GREY_WATER), stations.stations.single().services)
        assertEquals(listOf(greyWaterEntry.id), logs.entries.map { it.id })

        viewModel.undoDelete(viewModel.message.value as LogHistoryMessage.Deleted)

        assertEquals(listOf(cassetteEntry.id), viewModel.uiState.value.entries.map { it.id })
        val restoredStation = stations.stations.single()
        assertEquals(stationId, restoredStation.id)
        assertEquals(setOf(StationService.CASSETTE, StationService.GREY_WATER), restoredStation.services)
        assertEquals(setOf(cassetteEntry.id, greyWaterEntry.id), logs.entries.map { it.id }.toSet())
        assertEquals(cassetteEntry, logs.entries.single { it.id == cassetteEntry.id })
    }

    @Test
    fun delete_withAFueledEntryOfANamedFuelStation_keepsTheStationAndUndoRelinksTheEntry() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val stations = FakeStationRepository(logs = logs)
        val stationId = stations.save(
            Station(
                vehicleId = 1,
                type = StationType.FUEL,
                date = LocalDate.of(2026, 5, 1),
                name = "Shell",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val entry = logs.entries.single()
        assertEquals(LogType.FUELED, entry.type)
        val viewModel = LogHistoryViewModel(logs, stations, vehicleId = 1, type = LogType.FUELED)
        collect(viewModel.uiState)

        viewModel.delete(entry)

        assertEquals(emptyList<Long>(), logs.entries.map { it.id })
        assertEquals(listOf(stationId), stations.stations.map { it.id })

        viewModel.undoDelete(viewModel.message.value as LogHistoryMessage.Deleted)

        assertEquals(entry, logs.entries.single())
    }

    @Test
    fun delete_withAStationLinkedEntryAsTheOnlyService_deletesTheStationAndUndoRestoresBoth() = runTest(dispatcher) {
        val logs = FakeLogRepository()
        val stations = FakeStationRepository(logs = logs)
        val date = LocalDate.of(2026, 5, 1)
        val station = Station(
            vehicleId = 1,
            type = StationType.SUPPLY,
            date = date,
            services = setOf(StationService.CASSETTE),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val stationId = stations.save(station)
        val entry = logs.entries.single()
        val viewModel = LogHistoryViewModel(logs, stations, vehicleId = 1, type = LogType.CASSETTE_EMPTIED)
        collect(viewModel.uiState)

        viewModel.delete(entry)

        assertEquals(emptyList<Long>(), viewModel.uiState.value.entries.map { it.id })
        assertEquals(emptyList<Station>(), stations.stations)
        assertEquals(emptyList<Long>(), logs.entries.map { it.id })

        viewModel.undoDelete(viewModel.message.value as LogHistoryMessage.Deleted)

        assertEquals(listOf(entry.id), viewModel.uiState.value.entries.map { it.id })
        val restoredStation = stations.stations.single()
        assertEquals(stationId, restoredStation.id)
        assertEquals(setOf(StationService.CASSETTE), restoredStation.services)
        assertEquals(entry, logs.entries.single())
    }
}
