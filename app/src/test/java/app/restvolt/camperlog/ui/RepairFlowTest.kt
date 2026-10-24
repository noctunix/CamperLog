package app.restvolt.camperlog.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Abläufe rund um Reparaturen: implizites Anlegen und Speichern, Verwerfen/Speichern beim
 * Verlassen, Löschen mit Rückgängig über die Kopfzeile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class RepairFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository = FakeVehicleRepository()): FakeVehicleRepository {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }, formDraftStore = FakeFormDraftStore()) { }
            }
        }
        return vehicles
    }

    private fun descriptionField() = compose.onNode(hasSetTextAction() and hasText("Beschreibung"))

    private fun openNewRepairForm() {
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Reparatur hinzufügen").performClick()
    }

    /** Verlässt das (implizit schon automatisch gespeicherte) Formular ohne jede Rückfrage. */
    private fun backWithoutDialog() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForIdle()
    }

    /** Verlässt über die Verwerfen/Speichern-Abfrage und behält die Änderungen. */
    private fun backKeepingChanges() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("So lassen").performClick()
        compose.waitForIdle()
    }

    /** Verlässt über die Verwerfen/Speichern-Abfrage und verwirft die Änderungen. */
    private fun backDiscardingChanges() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitForIdle()
    }

    /**
     * Der Haken in der Kopfzeile verlässt sofort, ohne die Verwerfen/Speichern-Abfrage zu zeigen -
     * Autosave hat die Eingabe schon gespeichert.
     */
    @Test
    fun saveCheckmark_leavesImmediatelyWithoutLeaveDialog() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Reifen gewechselt")

        compose.onNodeWithContentDescription("Speichern").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Reifen gewechselt").assertExists()
    }

    /** Bestätigt im Zeitauswahl-Dialog speichert eine Uhrzeit und ersetzt das Uhr-Symbol durch "Uhrzeit entfernen". */
    @Test
    fun timeField_confirmingThePickerSavesTheChosenTime() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Glühbirne gewechselt")

        compose.onNodeWithContentDescription("Uhrzeit wählen").performClick()
        compose.onNodeWithText("OK").performClick()

        compose.onNodeWithContentDescription("Uhrzeit entfernen").assertExists()

        backKeepingChanges()
        compose.onNodeWithText("Glühbirne gewechselt").performClick()

        compose.onNodeWithContentDescription("Uhrzeit entfernen").assertExists()
    }

    @Test
    fun repairsSectionIsAlwaysVisibleEvenWhenSheetIsEmpty() {
        start()
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNodeWithText("Noch keine Angaben erfasst.").assertExists()
        compose.onNodeWithText("Noch keine Reparaturen.").assertExists()
        compose.onNodeWithText("Reparatur hinzufügen").assertExists()
    }

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Eine neue Reparatur hat von der
     * allerersten Anzeige an eine echte id - Anhänge lassen sich sofort hinzufügen, ohne auf einen
     * expliziten Speichern-Erfolg zu warten.
     */
    @Test
    fun openingNewRepair_createsARealRecordImmediately_enablingAttachmentsAndDelete() {
        start()
        openNewRepairForm()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto hinzufügen").assertExists()
        compose.onNodeWithContentDescription("Reparatur löschen").assertExists()
    }

    /** Eine neue Reparatur speichert sich schon laufend selbst - ohne jede Eingabe bleibt also keine Karteileiche zurück. */
    @Test
    fun leavingNewRepairWithoutAnyInput_discardsItSilentlyWithoutAsking() {
        start()
        openNewRepairForm()
        compose.waitForIdle()

        backWithoutDialog()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Noch keine Reparaturen.").assertExists()
    }

    /** Schon die erste Eingabe in ein beliebiges Feld macht den Pflichtfeld-Fehler der Beschreibung sichtbar. */
    @Test
    fun descriptionRequired_isShownAsSoonAsAnyOtherFieldIsTouched() {
        start()
        openNewRepairForm()

        compose.onNode(hasSetTextAction() and hasText("Werkstatt")).performTextInput("Werkstatt Meier")

        compose.onNodeWithText("Beschreibung erforderlich").assertExists()
    }

    /** Mit einer sinnvollen Eingabe fragt das Verlassen jetzt nach; "So lassen" behält die automatisch angelegte Reparatur. */
    @Test
    fun leavingNewRepairAfterTyping_asksAndKeepingKeepsTheRepair() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Reifen gewechselt")

        backKeepingChanges()

        compose.onNodeWithText("Reifen gewechselt").assertExists()
        compose.onNodeWithText("Noch keine Reparaturen.").assertDoesNotExist()
    }

    /** "Verwerfen" löscht die automatisch angelegte neue Reparatur trotz sinnvoller Eingabe wieder komplett. */
    @Test
    fun leavingNewRepairAfterTyping_discardingDeletesIt() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Reifen gewechselt")

        backDiscardingChanges()

        compose.onNodeWithText("Noch keine Reparaturen.").assertExists()
    }

    /** Ein unverändertes Formular einer bestehenden Reparatur verlässt sich ohne jede Rückfrage. */
    @Test
    fun leavingUnmodifiedExistingRepairEdit_leavesWithoutAsking() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Ölwechsel")
        backKeepingChanges()

        compose.onNodeWithText("Ölwechsel").performClick()
        backWithoutDialog()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Ölwechsel").assertExists()
    }

    /**
     * Das Bearbeiten einer bestehenden Reparatur fragt beim Verlassen mit echten Änderungen jetzt
     * über die Verwerfen/Speichern-Abfrage nach; Verwerfen setzt sie auf den beim Öffnen geladenen
     * Stand zurück, statt sie (wie bei einer neuen Reparatur) zu löschen.
     */
    @Test
    fun leavingDirtyExistingRepairEdit_discardingRevertsToTheOpenedState() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Reifen gewechselt")
        backKeepingChanges()

        compose.onNodeWithText("Reifen gewechselt").performClick()
        descriptionField().performTextReplacement("Bremsen gewechselt")

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        descriptionField().assertExists()

        backDiscardingChanges()

        compose.onNodeWithText("Reifen gewechselt").assertExists()
        compose.onNodeWithText("Bremsen gewechselt").assertDoesNotExist()
    }

    @Test
    fun editingExistingRepair_autosavesWithoutAnySaveButton() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Reifen gewechselt")
        backKeepingChanges()

        compose.onNodeWithText("Reifen gewechselt").performClick()
        descriptionField().performTextReplacement("Bremsen gewechselt")
        backKeepingChanges()

        compose.onNodeWithText("Bremsen gewechselt").assertExists()
        compose.onNodeWithText("Reifen gewechselt").assertDoesNotExist()
    }

    @Test
    fun addRepair_keepsWorkshopNameAfterReopening() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Ölwechsel")
        compose.onNode(hasSetTextAction() and hasText("Werkstatt")).performTextInput("Werkstatt Meier")
        backKeepingChanges()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNode(hasSetTextAction() and hasText("Werkstatt")).assertTextContains("Werkstatt Meier")
    }

    @Test
    fun addRepair_keepsPaymentMethodAfterReopening() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Ölwechsel")
        compose.onNode(hasSetTextAction() and hasText("Zahlungsart")).performTextInput("Bar")
        backKeepingChanges()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNode(hasSetTextAction() and hasText("Zahlungsart")).assertTextContains("Bar")
    }

    /** Löschen über die Kopfzeile wirkt unabhängig von der Verwerfen/Speichern-Abfrage, auch ohne jede Eingabe. */
    @Test
    fun deleteRepair_fromFormWithoutAnyChanges_doesNotAskToDiscardAndRemovesIt() {
        start()
        openNewRepairForm()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Reparatur löschen").performClick()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Reparatur gelöscht").assertExists()
        compose.onNodeWithText("Noch keine Reparaturen.").assertExists()
    }

    @Test
    fun deleteRepair_removesItAndOffersUndo() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Ölwechsel")
        backKeepingChanges()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNodeWithContentDescription("Reparatur löschen").performClick()

        compose.onNodeWithText("Ölwechsel").assertDoesNotExist()
        compose.onNodeWithText("Reparatur gelöscht").assertExists()
    }

    @Test
    fun deleteRepair_undo_restoresIt() {
        start()
        openNewRepairForm()
        descriptionField().performTextInput("Ölwechsel")
        backKeepingChanges()

        compose.onNodeWithText("Ölwechsel").performClick()
        compose.onNodeWithContentDescription("Reparatur löschen").performClick()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Ölwechsel").assertExists()
    }
}
