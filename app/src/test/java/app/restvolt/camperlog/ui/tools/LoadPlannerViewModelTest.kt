package app.restvolt.camperlog.ui.tools

import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.ui.FakeLoadPlanRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LoadPlannerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun weighedVehicle(id: Long = 1) = defaultVehicle(id = id).copy(
        measuredFrontAxleKg = 1000,
        measuredRearAxleKg = 1200,
        wheelbaseCm = 350,
    )

    @Test
    fun ensureProfile_createsADefaultProfileOnOpen() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val loadPlans = FakeLoadPlanRepository()
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)

        viewModel.ensureProfile(1, "Standard")

        val profiles = viewModel.uiState.value.plan.profiles
        assertEquals(1, profiles.size)
        assertEquals("Standard", profiles.single().name)
        assertEquals(profiles.single().id, viewModel.uiState.value.selectedProfileId)
    }

    @Test
    fun savingANewNotWeighedItem_activatesItOnlyInTheCurrentProfile() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val loadPlans = FakeLoadPlanRepository(profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")))
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)
        val otherProfileId = loadPlans.saveProfile(LoadProfile(vehicleId = 1, name = "Langreise"))

        viewModel.saveItem(newItem(name = "Grill", inWeighing = false), currentProfileId = 1)

        val item = viewModel.uiState.value.plan.items.single { it.name == "Grill" }
        val states = viewModel.uiState.value.plan.itemStates.filter { it.itemId == item.id }
        assertTrue(states.single { it.profileId == 1L }.active)
        assertTrue(states.none { it.profileId == otherProfileId })
    }

    @Test
    fun togglingAnItem_changesTheForecast() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val item = newItem(id = 5, name = "Kiste", massGrams = 20_000, quantity = 1, positionCm = 100, inWeighing = false)
        val loadPlans = FakeLoadPlanRepository(items = listOf(item), profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")))
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)

        val before = viewModel.uiState.value.forecast?.totalKg

        viewModel.setItemActive(1, item, true)

        val after = viewModel.uiState.value.forecast?.totalKg
        assertNotNull(before)
        assertNotNull(after)
        assertEquals(20.0, after!! - before!!, 0.001)
    }

    @Test
    fun duplicateProfile_copiesItsSwitchesAndSelectsTheCopy() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val item = newItem(id = 5, name = "Kiste", inWeighing = false)
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Standard")
        val loadPlans = FakeLoadPlanRepository(items = listOf(item), profiles = listOf(profile))
        loadPlans.setItemActive(1, 5, true)
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)

        viewModel.duplicateProfile(profile, "Standard (copy)")

        val plan = viewModel.uiState.value.plan
        val copy = plan.profiles.single { it.name == "Standard (copy)" }
        assertEquals(copy.id, viewModel.uiState.value.selectedProfileId)
        assertTrue(plan.itemStates.any { it.profileId == copy.id && it.itemId == 5L && it.active })
    }

    @Test
    fun deleteProfile_doesNothingForTheLastRemainingProfile() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val profile = LoadProfile(id = 1, vehicleId = 1, name = "Standard")
        val loadPlans = FakeLoadPlanRepository(profiles = listOf(profile))
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)

        viewModel.deleteProfile(profile)

        assertEquals(1, viewModel.uiState.value.plan.profiles.size)
    }

    @Test
    fun deleteItem_removesItAndRestoreItemBringsItBack() = runTest(dispatcher) {
        val vehicles = FakeVehicleRepository(listOf(weighedVehicle()))
        val item = newItem(id = 5, name = "Kiste")
        val loadPlans = FakeLoadPlanRepository(items = listOf(item), profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")))
        val viewModel = LoadPlannerViewModel(vehicles, loadPlans, requestedVehicleId = null)
        collect(viewModel.uiState)

        viewModel.deleteItem(item)
        assertTrue(viewModel.uiState.value.plan.items.isEmpty())
        assertEquals(item, (viewModel.message.value as LoadPlannerMessage.ItemDeleted).item)

        viewModel.restoreItem(item)
        assertEquals(listOf(item), viewModel.uiState.value.plan.items)
    }

    private fun newItem(
        id: Long = 0,
        name: String,
        massGrams: Long = 1_000,
        quantity: Int = 1,
        positionCm: Int? = null,
        inWeighing: Boolean = false,
    ) = LoadItem(
        id = id,
        vehicleId = 1,
        name = name,
        category = LoadCategory.EQUIPMENT,
        massGrams = massGrams,
        quantity = quantity,
        positionCm = positionCm,
        inWeighing = inWeighing,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
