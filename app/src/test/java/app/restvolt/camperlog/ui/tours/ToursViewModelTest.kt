package app.restvolt.camperlog.ui.tours

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeChecklistTemplateRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.VehicleScopeSettings
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Deckt den Fahrzeugfilter der Tourenliste ab: Touren dürfen nie unter einem Fahrzeug erscheinen,
 * dem sie nicht wirklich zugeordnet sind, auch wenn Fahrzeugliste und aktuelles Fahrzeug aus
 * getrennten Datenquellen (Room-Flows) kommen und zu unterschiedlichen Zeitpunkten aktualisieren.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToursViewModelTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
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
        vehicles: FakeVehicleRepository,
        tours: FakeTourRepository,
        checklistTemplates: FakeChecklistTemplateRepository = FakeChecklistTemplateRepository(),
        checklists: FakeChecklistRepository = FakeChecklistRepository(),
    ) = ToursViewModel(
        tours,
        vehicles,
        FakeStationRepository(),
        FakeTrackRepository(),
        VehicleScopeSettings(context),
        checklistTemplates,
        checklists,
    )

    @Test
    fun addingANewVehicleLeavesTheCurrentVehiclesToursUntouched() = runTest(dispatcher) {
        val hannes = defaultVehicle(id = 1, name = "Hannes")
        val vehicles = FakeVehicleRepository(listOf(hannes), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(id = 1, vehicleId = 1), tour(id = 2, vehicleId = 1), runningTour(id = 3, vehicleId = 1)))
        val viewModel = viewModel(vehicles, tours)
        collect(viewModel.uiState)

        val before = viewModel.uiState.value.tours.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), before)

        // Entspricht dem gemeldeten Ablauf: ein neues Fahrzeug kommt dazu, ohne dass das aktuelle
        // Fahrzeug wechselt - die Fahrzeugliste und die Auswahl kommen aus getrennten Flows.
        vehicles.save(defaultVehicle(id = 0, name = "Audi"))

        val after = viewModel.uiState.value
        assertEquals(1L, after.currentVehicleId)
        assertEquals(setOf(1L, 2L, 3L), after.tours.map { it.id }.toSet())
        assertEquals(3, after.tours.count { it.vehicleId == 1L })
    }

    @Test
    fun finishingARunningTourRightAfterAddingAVehicleKeepsItUnderItsOwnVehicle() = runTest(dispatcher) {
        val hannes = defaultVehicle(id = 1, name = "Hannes")
        val vehicles = FakeVehicleRepository(listOf(hannes), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(id = 1, vehicleId = 1), runningTour(id = 2, vehicleId = 1)))
        val viewModel = viewModel(vehicles, tours)
        collect(viewModel.uiState)

        val audiId = vehicles.save(defaultVehicle(id = 0, name = "Audi"))
        viewModel.finish(checkNotNull(tours.tours.firstOrNull { it.id == 2L }), LocalDate.of(2026, 7, 10))

        assertEquals(1L, checkNotNull(tours.tours.firstOrNull { it.id == 1L }).vehicleId)
        assertEquals(1L, checkNotNull(tours.tours.firstOrNull { it.id == 2L }).vehicleId)
        assertEquals(0, tours.tours.count { it.vehicleId == audiId })
    }

    @Test
    fun pinnedTemplatesExposesOnlyTemplatesMarkedPinned() = runTest(dispatcher) {
        val hannes = defaultVehicle(id = 1, name = "Hannes")
        val vehicles = FakeVehicleRepository(listOf(hannes), currentVehicleId = 1)
        val templates = FakeChecklistTemplateRepository(
            listOf(template(id = 1, name = "Abfahrt", pinned = true), template(id = 2, name = "Ankunft", pinned = false)),
        )
        val viewModel = viewModel(vehicles, FakeTourRepository(), templates)
        collect(viewModel.uiState)

        assertEquals(listOf("Abfahrt"), viewModel.uiState.value.pinnedTemplates.map { it.name })
    }

    @Test
    fun startPinnedChecklistAttachesItToTheCurrentVehiclesRunningTour() = runTest(dispatcher) {
        val hannes = defaultVehicle(id = 1, name = "Hannes")
        val vehicles = FakeVehicleRepository(listOf(hannes), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(runningTour(id = 5, vehicleId = 1)))
        val templates = FakeChecklistTemplateRepository(listOf(template(id = 1, name = "Abfahrt", pinned = true)))
        val checklists = FakeChecklistRepository()
        val viewModel = viewModel(vehicles, tours, templates, checklists)
        collect(viewModel.uiState)

        viewModel.startPinnedChecklist(viewModel.uiState.value.pinnedTemplates.single())

        val started = checklists.checklists.single()
        assertEquals(1L, started.vehicleId)
        assertEquals(5L, started.tourId)
        assertEquals(started.id, viewModel.startedChecklistId.value)
    }

    private fun template(id: Long, name: String, pinned: Boolean) =
        ChecklistTemplate(id = id, name = name, pinned = pinned, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun tour(id: Long, vehicleId: Long) = Tour(
        id = id,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 3),
        destination = "Ziel $id",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 100,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun runningTour(id: Long, vehicleId: Long) = tour(id, vehicleId).copy(endDate = null, travelDays = 0, overnightStays = 0, distanceKm = 0)
}
