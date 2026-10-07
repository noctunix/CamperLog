package app.restvolt.camperlog.ui.search

import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeChecklistTemplateRepository
import app.restvolt.camperlog.ui.FakeDiaryEntryRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] im Test aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun viewModel(
        tours: FakeTourRepository = FakeTourRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
        stations: FakeStationRepository = FakeStationRepository(),
        diaryEntries: FakeDiaryEntryRepository = FakeDiaryEntryRepository(),
        logbook: FakeLogRepository = FakeLogRepository(),
        documents: FakeVehicleDocumentRepository = FakeVehicleDocumentRepository(),
        checklists: FakeChecklistRepository = FakeChecklistRepository(),
        checklistTemplates: FakeChecklistTemplateRepository = FakeChecklistTemplateRepository(),
    ) = SearchViewModel(
        tours, vehicles, stations, diaryEntries, logbook, documents, checklists, checklistTemplates,
        computationDispatcher = dispatcher,
    )

    /** Setzt die Suche und lässt die 200-ms-Entprellung sowie die Auswertung durchlaufen. */
    private fun TestScope.search(viewModel: SearchViewModel, query: String) {
        viewModel.onQueryChange(query)
        advanceTimeBy(250)
    }

    private fun tour(id: Long, vehicleId: Long = 1, destination: String, notes: String = "", startDate: LocalDate = LocalDate.of(2026, 6, 1)) = Tour(
        id = id,
        vehicleId = vehicleId,
        startDate = startDate,
        endDate = startDate,
        destination = destination,
        tourType = TourType.WEEKEND,
        travelDays = 1,
        overnightStays = 0,
        distanceKm = 0,
        costs = emptyList(),
        notes = notes,
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(id: Long, vehicleId: Long = 1, name: String, date: LocalDate = LocalDate.of(2026, 6, 1)) = Station(
        id = id,
        vehicleId = vehicleId,
        type = StationType.SIGHT,
        date = date,
        name = name,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun shortQuery_showsHintAndNoGroups() = runTest(dispatcher) {
        val viewModel = viewModel(tours = FakeTourRepository(listOf(tour(1, destination = "Gardasee"))))
        collect(viewModel.uiState)

        search(viewModel, "g")

        assertTrue(viewModel.uiState.value.isQueryTooShort)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun noMatch_showsEmptyGroupsButNotTooShort() = runTest(dispatcher) {
        val viewModel = viewModel(tours = FakeTourRepository(listOf(tour(1, destination = "Gardasee"))))
        collect(viewModel.uiState)

        search(viewModel, "ostsee")

        assertEquals(false, viewModel.uiState.value.isQueryTooShort)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun matchingDestination_isGroupedUnderTours() = runTest(dispatcher) {
        val viewModel = viewModel(tours = FakeTourRepository(listOf(tour(1, destination = "Gardasee"))))
        collect(viewModel.uiState)

        search(viewModel, "garda")

        val group = viewModel.uiState.value.groups.single()
        assertEquals(SearchResultType.TOUR, group.type)
        val result = group.results.single() as SearchResult.TourResult
        assertEquals(1L, result.tourId)
    }

    @Test
    fun resultsAcrossTypes_appearAsSeparateGroupsInFixedOrder() = runTest(dispatcher) {
        val viewModel = viewModel(
            tours = FakeTourRepository(listOf(tour(1, destination = "Gardasee ruhig"))),
            stations = FakeStationRepository(listOf(station(1, name = "Ruhiger Stellplatz"))),
        )
        collect(viewModel.uiState)

        search(viewModel, "ruhig")

        assertEquals(listOf(SearchResultType.TOUR, SearchResultType.STOP), viewModel.uiState.value.groups.map { it.type })
    }

    @Test
    fun resultsWithinAGroup_areSortedNewestDateFirst() = runTest(dispatcher) {
        val older = tour(1, destination = "Gardasee alt", startDate = LocalDate.of(2024, 1, 1))
        val newer = tour(2, destination = "Gardasee neu", startDate = LocalDate.of(2026, 1, 1))
        val viewModel = viewModel(tours = FakeTourRepository(listOf(older, newer)))
        collect(viewModel.uiState)

        search(viewModel, "gardasee")

        val results = viewModel.uiState.value.groups.single().results
        assertEquals(listOf(2L, 1L), results.map { (it as SearchResult.TourResult).tourId })
    }

    @Test
    fun vehicleName_isNullWithOnlyOneVehicleAndSetWithSeveral() = runTest(dispatcher) {
        val singleVehicleModel = viewModel(
            vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1, name = "Fiat"))),
            tours = FakeTourRepository(listOf(tour(1, vehicleId = 1, destination = "Gardasee"))),
        )
        collect(singleVehicleModel.uiState)
        search(singleVehicleModel, "garda")
        assertNull(singleVehicleModel.uiState.value.groups.single().results.single().vehicleName)

        val twoVehiclesModel = viewModel(
            vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1, name = "Fiat"), defaultVehicle(2, name = "Ford"))),
            tours = FakeTourRepository(listOf(tour(1, vehicleId = 2, destination = "Gardasee"))),
        )
        collect(twoVehiclesModel.uiState)
        search(twoVehiclesModel, "garda")
        assertEquals("Ford", twoVehiclesModel.uiState.value.groups.single().results.single().vehicleName)
    }

    @Test
    fun checklistResult_matchesByTitleOrItemText() = runTest(dispatcher) {
        val checklist = Checklist(
            id = 1,
            vehicleId = 1,
            title = "Abfahrt",
            items = listOf(ChecklistItem("Reifendruck prüfen"), ChecklistItem("Fenster schließen")),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val viewModel = viewModel(checklists = FakeChecklistRepository(listOf(checklist)))
        collect(viewModel.uiState)

        search(viewModel, "reifendruck")

        val result = viewModel.uiState.value.groups.single().results.single() as SearchResult.ChecklistResult
        assertEquals(1L, result.checklistId)
    }

    @Test
    fun checklistTemplateResult_hasNoVehicleName() = runTest(dispatcher) {
        val template = ChecklistTemplate(id = 1, name = "Winterfest machen", items = listOf("Frischwasser ablassen"), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModel(
            vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1, name = "Fiat"), defaultVehicle(2, name = "Ford"))),
            checklistTemplates = FakeChecklistTemplateRepository(listOf(template)),
        )
        collect(viewModel.uiState)

        search(viewModel, "winterfest")

        val result = viewModel.uiState.value.groups.single().results.single()
        assertNull(result.vehicleName)
    }

    @Test
    fun repairResult_matchesDescription() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository()
        vehicles.saveRepair(Repair(vehicleId = 1, date = LocalDate.of(2026, 5, 1), description = "Bremsen erneuert", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val viewModel = viewModel(vehicles = vehicles)
        collect(viewModel.uiState)

        search(viewModel, "bremsen")

        val result = viewModel.uiState.value.groups.single().results.single() as SearchResult.RepairResult
        assertEquals("Bremsen erneuert", result.description)
    }

    @Test
    fun diaryResult_showsTourDestination() = runTest(dispatcher) {
        val entry = DiaryEntry(id = 1, tourId = 1, date = LocalDate.of(2026, 7, 1), text = "Schöner Sonnenuntergang", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModel(
            tours = FakeTourRepository(listOf(tour(1, destination = "Lofoten"))),
            diaryEntries = FakeDiaryEntryRepository(listOf(entry)),
        )
        collect(viewModel.uiState)

        search(viewModel, "sonnenuntergang")

        val result = viewModel.uiState.value.groups.single().results.single() as SearchResult.DiaryResult
        assertEquals("Lofoten", result.tourDestination)
    }

    @Test
    fun vehicleDocumentResult_matchesTitle() = runTest(dispatcher) {
        val document = VehicleDocument(id = 1, vehicleId = 1, kind = DocumentKind.INSURANCE, title = "Kaskoversicherung", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModel(documents = FakeVehicleDocumentRepository(listOf(document)))
        collect(viewModel.uiState)

        search(viewModel, "kasko")

        val result = viewModel.uiState.value.groups.single().results.single() as SearchResult.VehicleDocumentResult
        assertEquals(1L, result.documentId)
    }

    @Test
    fun vehicleResult_matchesNameOrNotes() = runTest(dispatcher) {
        val viewModel = viewModel(
            vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(1, name = "Pössl Vanster").copy(notes = "Hubbett eingebaut"))),
        )
        collect(viewModel.uiState)

        search(viewModel, "hubbett")

        val result = viewModel.uiState.value.groups.single().results.single() as SearchResult.VehicleResult
        assertEquals(1L, result.vehicleId)
    }
}
