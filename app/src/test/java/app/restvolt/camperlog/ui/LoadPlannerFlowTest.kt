package app.restvolt.camperlog.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadProfileItemState
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.tools.LoadPlannerScreen
import app.restvolt.camperlog.ui.tools.LoadPlannerViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Abläufe des Beladungsplaners direkt auf [LoadPlannerScreen], nicht über den vollen
 * Navigationsbaum: Dort kommt Robolectric mit einem Formulardialog über der vollen Reiter-Seite
 * nicht verlässlich zur Ruhe, siehe CountryPickerTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class LoadPlannerFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun togglingAnItemWithAPosition_changesTheRearAxleValue_andTogglingItOffRevertsIt() {
        val vehicle = defaultVehicle(name = "Camper").copy(
            measuredFrontAxleKg = 1000,
            measuredRearAxleKg = 500,
            wheelbaseCm = 300,
        )
        val item = LoadItem(
            id = 5,
            vehicleId = 1,
            name = "Grill",
            category = LoadCategory.EQUIPMENT,
            massGrams = 50_000,
            quantity = 1,
            positionCm = 300,
            inWeighing = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val loadPlans = FakeLoadPlanRepository(items = listOf(item), profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")))
        val viewModel = LoadPlannerViewModel(FakeVehicleRepository(listOf(vehicle)), loadPlans, requestedVehicleId = null)
        compose.setContent {
            CamperLogTheme {
                LoadPlannerScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onEditVehicle = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("500 kg").assertExists()

        compose.onNodeWithContentDescription("Grill in diesem Profil aktiv").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("550 kg").assertExists()
        compose.onNodeWithText("500 kg").assertDoesNotExist()

        compose.onNodeWithContentDescription("Grill in diesem Profil aktiv").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("500 kg").assertExists()
        compose.onNodeWithText("550 kg").assertDoesNotExist()
    }

    @Test
    fun sideView_contentDescriptionReflectsTheRearAxleChangeAfterToggling() {
        val vehicle = defaultVehicle(name = "Camper").copy(
            measuredFrontAxleKg = 1000,
            measuredRearAxleKg = 500,
            wheelbaseCm = 300,
        )
        val item = LoadItem(
            id = 5,
            vehicleId = 1,
            name = "Grill",
            category = LoadCategory.EQUIPMENT,
            massGrams = 50_000,
            quantity = 1,
            positionCm = 300,
            inWeighing = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val loadPlans = FakeLoadPlanRepository(items = listOf(item), profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")))
        val viewModel = LoadPlannerViewModel(FakeVehicleRepository(listOf(vehicle)), loadPlans, requestedVehicleId = null)
        compose.setContent {
            CamperLogTheme {
                LoadPlannerScreen(viewModel = viewModel, onBack = {}, onEditVehicle = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("hinten 500 kg", substring = true).assertExists()

        compose.onNodeWithContentDescription("Grill in diesem Profil aktiv").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("hinten 550 kg", substring = true).assertExists()
    }

    @Test
    fun sideView_offersAPlacementChipForAnActiveItemWithoutAPosition() {
        val vehicle = defaultVehicle(name = "Camper").copy(
            measuredFrontAxleKg = 1000,
            measuredRearAxleKg = 500,
            wheelbaseCm = 300,
        )
        val item = LoadItem(
            id = 5,
            vehicleId = 1,
            name = "Grill",
            category = LoadCategory.EQUIPMENT,
            massGrams = 50_000,
            quantity = 1,
            positionCm = null,
            inWeighing = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val loadPlans = FakeLoadPlanRepository(
            items = listOf(item),
            profiles = listOf(LoadProfile(id = 1, vehicleId = 1, name = "Standard")),
            itemStates = listOf(LoadProfileItemState(profileId = 1, itemId = 5, active = true)),
        )
        val viewModel = LoadPlannerViewModel(FakeVehicleRepository(listOf(vehicle)), loadPlans, requestedVehicleId = null)
        compose.setContent {
            CamperLogTheme {
                LoadPlannerScreen(viewModel = viewModel, onBack = {}, onEditVehicle = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Platzieren: Grill").assertExists()
    }

}
