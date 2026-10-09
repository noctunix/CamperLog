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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
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
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }) { }
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
        compose.onNode(hasSetTextAction() and hasText("Länge")).performScrollTo().performTextInput("636")
        compose.onNode(hasSetTextAction() and hasText("Leistung")).performScrollTo().performTextInput("120")
        compose.onNode(hasSetTextAction() and hasText("Diesel")).performScrollTo().performTextInput("90")

        clickSave()

        compose.onNodeWithText("636 cm").assertExists()
        compose.onNodeWithText("120 kW (163 PS)").assertExists()
        compose.onNodeWithText("90 l").assertExists()
    }

    @Test
    fun poweredField_showsLiveUpdatingPsHintBelowKwField() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        val powerField = compose.onNode(hasSetTextAction() and hasText("Leistung")).performScrollTo()
        powerField.performTextInput("120")
        compose.onNodeWithText("≈ 163 PS").assertExists()

        powerField.performTextClearance()
        powerField.performTextInput("60")
        compose.onNodeWithText("≈ 82 PS").assertExists()
        compose.onNodeWithText("≈ 163 PS").assertDoesNotExist()
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
    fun requiredEnergyTypes_hideTankFieldsForUnselectedDriveTypes() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasText("Diesel") and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasText("Strom") and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()

        compose.onNode(hasSetTextAction() and hasText("Diesel")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("AdBlue")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Batteriekapazität")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Frischwasser")).assertExists()
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
