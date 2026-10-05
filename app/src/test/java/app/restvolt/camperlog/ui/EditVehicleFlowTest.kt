package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Abläufe rund um das Datenblatt und das Formular des Fahrzeug-Reiters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class EditVehicleFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository = FakeVehicleRepository()) {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false) { }
            }
        }
    }

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

    @Test
    fun emptyVehicle_showsHintAndAddDetailsOpensForm() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNodeWithText("Noch keine Angaben erfasst.").assertExists()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
    }

    @Test
    fun savingVehicleDetails_showsFormattedValuesWithUnitsInSheet() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wohnmobil")
        compose.onNode(hasSetTextAction() and hasText("Länge")).performScrollTo().performTextInput("6,36")
        compose.onNode(hasSetTextAction() and hasText("Leistung")).performScrollTo().performTextInput("120")
        compose.onNode(hasSetTextAction() and hasText("Diesel")).performScrollTo().performTextInput("90")

        clickSave()

        compose.onNodeWithText("6,36 m").assertExists()
        compose.onNodeWithText("120 kW (163 PS)").assertExists()
        compose.onNodeWithText("90 l").assertExists()
    }

    @Test
    fun editAction_inTopBarOpensFormForExistingVehicle() {
        start(FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Camper"))))
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNodeWithContentDescription("Fahrzeug bearbeiten").performClick()

        compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
    }

    @Test
    fun manageVehicles_reachableFromTopBarWithOnlyOneVehicle() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Fahrzeuge verwalten").performClick()

        compose.onNodeWithText("Fahrzeuge verwalten").assertExists()
    }

    @Test
    fun nameRequired_forNewVehicle() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Fahrzeuge verwalten").performClick()
        compose.onNodeWithText("Fahrzeug hinzufügen").performClick()

        clickSave()

        compose.onNodeWithText("Name erforderlich").assertExists()
    }
}
