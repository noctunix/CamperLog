package app.restvolt.camperlog.ui.vehicle

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.CamperLogNavHost
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeBackupImporter
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeChecklistTemplateRepository
import app.restvolt.camperlog.ui.FakeCountryLookupRepository
import app.restvolt.camperlog.ui.FakeDiaryEntryRepository
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * Abläufe rund um Fahrzeugdokumente: implizites Anlegen und Speichern, Verwerfen/Speichern beim
 * Verlassen, Erinnerungskarte, Detailseite und Löschen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class VehicleDocumentFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(): FakeVehicleDocumentRepository {
        val documents = FakeVehicleDocumentRepository()
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Einziges Fahrzeug")))
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(),
                    vehicles,
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    documents,
                    FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(),
                    FakeAttachmentRepository(),
                    FakeAttachmentFileStore(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    AccentColor.AZURE,
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(),
                    onAccentColorChange = { },
                ) { }
            }
        }
        return documents
    }

    private fun titleField() = compose.onNode(hasSetTextAction() and hasText("Titel"))

    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performClick()
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    private fun openNewDocumentForm() {
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Dokument hinzufügen").performClick()
    }

    /** Öffnet ein bestehendes Dokument über das Datenblatt und dessen Detailseite zum Bearbeiten. */
    private fun openExistingDocumentForEdit(title: String) {
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithContentDescription("Dokument bearbeiten").performClick()
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
        compose.onNodeWithText("Speichern").performClick()
        compose.waitForIdle()
    }

    /** Verlässt über die Verwerfen/Speichern-Abfrage und verwirft die Änderungen. */
    private fun backDiscardingChanges() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitForIdle()
    }

    @Test
    fun addDocumentWithExpiry_showsReminderCardAndOpensItsDetail() {
        val documents = start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Noch keine Dokumente.").assertExists()

        openNewDocumentForm()
        compose.onNodeWithText("Versicherung").performClick()
        titleField().performTextInput("KFZ-Police")
        pickDay("Ablaufdatum", LocalDate.now().dayOfMonth)
        backKeepingChanges()

        assertEquals(1, documents.documents.size)
        compose.onNode(hasText("fällig in", substring = true) and hasClickAction()).assertExists()

        // Die Erinnerungskarte des ablaufenden Dokuments öffnet dessen Detailseite statt der Fahrzeugbearbeitung.
        compose.onNode(hasText("fällig in", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("KFZ-Police").assertExists()
        compose.onNodeWithText("Versicherung").assertExists()
    }

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Ein neues Dokument hat von der
     * allerersten Anzeige an eine echte id - Anhänge lassen sich sofort hinzufügen, ohne auf einen
     * expliziten Speichern-Erfolg zu warten.
     */
    @Test
    fun openingNewDocument_createsARealRecordImmediately_enablingAttachments() {
        start()
        openNewDocumentForm()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto hinzufügen").assertExists()
    }

    /** Ein neues Dokument speichert sich schon laufend selbst - ohne jede Eingabe bleibt also keine Karteileiche zurück. */
    @Test
    fun leavingNewDocumentWithoutAnyInput_discardsItSilentlyWithoutAsking() {
        val documents = start()
        openNewDocumentForm()
        compose.waitForIdle()

        backWithoutDialog()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Noch keine Dokumente.").assertExists()
        assertEquals(0, documents.documents.size)
    }

    /** Schon die erste Eingabe in ein beliebiges Feld macht den Pflichtfeld-Fehler des Titels sichtbar. */
    @Test
    fun titleRequired_isShownAsSoonAsAnyOtherFieldIsTouched() {
        start()
        openNewDocumentForm()

        compose.onNodeWithText("Versicherung").performClick()

        compose.onNodeWithText("Pflichtfeld").assertExists()
    }

    /** Mit einer sinnvollen Eingabe fragt das Verlassen jetzt nach; "Speichern" behält das automatisch angelegte Dokument. */
    @Test
    fun leavingNewDocumentAfterTyping_asksAndKeepingKeepsTheDocument() {
        val documents = start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")

        backKeepingChanges()

        compose.onNodeWithText("Fahrzeugschein").assertExists()
        compose.onNodeWithText("Noch keine Dokumente.").assertDoesNotExist()
        assertEquals(1, documents.documents.size)
    }

    /** "Verwerfen" löscht das automatisch angelegte neue Dokument trotz sinnvoller Eingabe wieder komplett. */
    @Test
    fun leavingNewDocumentAfterTyping_discardingDeletesIt() {
        val documents = start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")

        backDiscardingChanges()

        compose.onNodeWithText("Noch keine Dokumente.").assertExists()
        assertEquals(0, documents.documents.size)
    }

    /** Ein unverändertes Formular eines bestehenden Dokuments verlässt sich ohne jede Rückfrage. */
    @Test
    fun leavingUnmodifiedExistingDocumentEdit_leavesWithoutAsking() {
        start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")
        backKeepingChanges()

        openExistingDocumentForEdit("Fahrzeugschein")
        backWithoutDialog()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Fahrzeugschein").assertExists()
    }

    /**
     * Das Bearbeiten eines bestehenden Dokuments fragt beim Verlassen mit echten Änderungen jetzt
     * über die Verwerfen/Speichern-Abfrage nach; Verwerfen setzt es auf den beim Öffnen geladenen
     * Stand zurück, statt es (wie bei einem neuen Dokument) zu löschen.
     */
    @Test
    fun leavingDirtyExistingDocumentEdit_discardingRevertsToTheOpenedState() {
        start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")
        backKeepingChanges()

        openExistingDocumentForEdit("Fahrzeugschein")
        titleField().performTextReplacement("KFZ-Schein")

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        titleField().assertExists()

        backDiscardingChanges()

        compose.onNodeWithText("Fahrzeugschein").assertExists()
        compose.onNodeWithText("KFZ-Schein").assertDoesNotExist()
    }

    @Test
    fun editingExistingDocument_autosavesWithoutAnySaveButton() {
        start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")
        backKeepingChanges()

        openExistingDocumentForEdit("Fahrzeugschein")
        titleField().performTextReplacement("KFZ-Schein")
        backKeepingChanges()

        compose.onNodeWithText("KFZ-Schein").assertExists()
        compose.onNodeWithText("Fahrzeugschein").assertDoesNotExist()
    }

    @Test
    fun addDocument_keepsExpiryDateAfterReopening() {
        start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")
        pickDay("Ablaufdatum", LocalDate.now().dayOfMonth)
        backKeepingChanges()

        openExistingDocumentForEdit("Fahrzeugschein")
        compose.onNodeWithText(formatDate(LocalDate.now(), Locale.GERMANY)).assertExists()
    }

    @Test
    fun deleteDocument_removesItAfterConfirmation() {
        val documents = start()
        openNewDocumentForm()
        titleField().performTextInput("Fahrzeugschein")
        backKeepingChanges()

        compose.onNodeWithText("Fahrzeugschein").performClick()
        compose.onNodeWithContentDescription("Dokument löschen").performClick()
        compose.onNodeWithText("Dokument löschen?").assertExists()
        compose.onNode(hasText("Dokument löschen") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Noch keine Dokumente.").assertExists()
        assertEquals(0, documents.documents.size)
    }
}
