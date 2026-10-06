package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Abläufe rund um Reparaturen: Anlegen, Bearbeiten, Löschen mit Rückgängig. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class RepairFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository = FakeVehicleRepository()): FakeVehicleRepository {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false) { }
            }
        }
        return vehicles
    }

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

    private fun descriptionField() = compose.onNode(hasSetTextAction() and hasText("Beschreibung"))

    @Test
    fun repairsSectionIsAlwaysVisibleEvenWhenSheetIsEmpty() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNodeWithText("Noch keine Angaben erfasst.").assertExists()
        compose.onNodeWithText("Noch keine Reparaturen.").assertExists()
        compose.onNodeWithText("Reparatur hinzufügen").assertExists()
    }

    @Test
    fun addRepair_appearsInTheSheet() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()

        descriptionField().performTextInput("Reifen gewechselt")
        compose.onNode(hasSetTextAction() and hasText("km-Stand")).performTextInput("42000")
        clickSave()

        compose.onNodeWithText("Reifen gewechselt").assertExists()
        compose.onNodeWithText("42.000 km").assertExists()
        compose.onNodeWithText("Noch keine Reparaturen.").assertDoesNotExist()
    }

    @Test
    fun descriptionRequired_forNewRepair() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()

        clickSave()

        compose.onNodeWithText("Beschreibung erforderlich").assertExists()
    }

    @Test
    fun editRepair_updatesTheSheet() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()
        descriptionField().performTextInput("Reifen gewechselt")
        clickSave()

        compose.onNodeWithText("Reifen gewechselt").performClick()
        descriptionField().performTextReplacement("Bremsen gewechselt")
        clickSave()

        compose.onNodeWithText("Bremsen gewechselt").assertExists()
        compose.onNodeWithText("Reifen gewechselt").assertDoesNotExist()
    }

    @Test
    fun deleteRepair_removesItAndOffersUndo() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()
        descriptionField().performTextInput("Ölwechsel")
        clickSave()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNodeWithContentDescription("Reparatur löschen").performClick()

        compose.onNodeWithText("Ölwechsel").assertDoesNotExist()
        compose.onNodeWithText("Reparatur gelöscht").assertExists()
    }

    @Test
    fun deleteRepair_undo_restoresIt() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()
        descriptionField().performTextInput("Ölwechsel")
        clickSave()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNodeWithContentDescription("Reparatur löschen").performClick()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Ölwechsel").assertExists()
    }

    @Test
    fun deleteRepair_fromFormWithoutChanges_doesNotAskToDiscard() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()
        descriptionField().performTextInput("Ölwechsel")
        clickSave()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNodeWithContentDescription("Reparatur löschen").performClick()

        compose.onNodeWithText("Änderungen verwerfen?").assertDoesNotExist()
    }
}
