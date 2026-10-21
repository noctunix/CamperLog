package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasClickAction
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

    /** Verlässt das Fahrzeugformular über "Zurück" und bestätigt die Verwerfen/Speichern-Abfrage mit "Speichern". */
    private fun leaveFormAndKeep() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitForIdle()
    }

    /**
     * Der Haken in der Kopfzeile verlässt sofort, ohne die Verwerfen/Speichern-Abfrage zu zeigen -
     * Autosave hat die Eingabe schon gespeichert.
     */
    @Test
    fun saveCheckmark_leavesImmediatelyWithoutLeaveDialog() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wohnmobil")

        compose.onNodeWithContentDescription("Speichern").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Wohnmobil").assertExists()
    }

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
        compose.onNode(hasSetTextAction() and hasText("Kraftstoff")).performScrollTo().performTextInput("90")

        leaveFormAndKeep()

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
    fun transmissionField_isVisibleWithoutExpandingEngineDetails() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNodeWithText("Automatik").performScrollTo().assertExists()
    }

    @Test
    fun engineDetailsSection_startsCollapsedAndRevealsDisplacement() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("Hubraum")).assertDoesNotExist()

        compose.onNodeWithText("Hubraum").performScrollTo().performClick()

        compose.onNode(hasSetTextAction() and hasText("Hubraum")).assertExists()
    }

    @Test
    fun saleSection_hasOdometerFieldNextToSaleDateAndPrice() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("km-Stand bei Verkauf")).performScrollTo().assertExists()
    }

    @Test
    fun purchaseSaleSection_hasSellerAndBuyerNameFieldsAndShowsThemInSheet() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wohnmobil")
        compose.onNode(hasSetTextAction() and hasText("Verkäufer")).performScrollTo().performTextInput("Max Verkäufer")
        compose.onNode(hasSetTextAction() and hasText("Käufer")).performScrollTo().performTextInput("Erika Käufer")

        leaveFormAndKeep()

        compose.onNodeWithText("Max Verkäufer").assertExists()
        compose.onNodeWithText("Erika Käufer").assertExists()
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

        compose.onNode(hasSetTextAction() and hasText("Kraftstoff")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("AdBlue")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Batteriekapazität")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Frischwasser")).assertExists()
    }

    @Test
    fun tiresSection_showsTwoAxlesByDefaultAndAddButtonAppendsAThird() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNodeWithText("Achse 1").performScrollTo().assertExists()
        compose.onNodeWithText("Achse 2").assertExists()
        compose.onNodeWithText("Achse 3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Achse entfernen").assertDoesNotExist()

        compose.onNodeWithText("Achse hinzufügen").performScrollTo().performClick()

        compose.onNodeWithText("Achse 3").assertExists()
        compose.onNodeWithContentDescription("Achse entfernen").assertExists()

        compose.onNodeWithContentDescription("Achse entfernen").performClick()

        compose.onNodeWithText("Achse 3").assertDoesNotExist()
    }

    @Test
    fun tiresSection_dualTiresTogglesSecondSizeFieldAndShowsBothSizesInSheet() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Details hinzufügen").performClick()

        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wohnmobil")
        compose.onAllNodes(hasSetTextAction() and hasText("Reifengröße"))[0].performScrollTo().performTextInput("225/75 R16")
        compose.onNode(hasSetTextAction() and hasText("Zweite Reifengröße")).assertDoesNotExist()

        compose.onAllNodes(hasText("Doppelbereifung") and hasClickAction())[0].performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Zweite Reifengröße")).performTextInput("225/70 R15")

        leaveFormAndKeep()

        compose.onNodeWithText("225/75 R16 (Doppelbereifung: 225/70 R15)").assertExists()
    }

    @Test
    fun nameRequired_forNewVehicle() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Fahrzeuge verwalten").performClick()
        compose.onNodeWithText("Fahrzeug hinzufügen").performClick()

        // Es gibt keinen Speichern-Button mehr; schon die erste Eingabe macht den noch fehlenden
        // Pflichtnamen inline sichtbar.
        val nameField = compose.onNode(hasSetTextAction() and hasText("Name"))
        nameField.performTextInput("X")
        nameField.performTextClearance()

        compose.onNodeWithText("Name erforderlich").assertExists()
    }
}
