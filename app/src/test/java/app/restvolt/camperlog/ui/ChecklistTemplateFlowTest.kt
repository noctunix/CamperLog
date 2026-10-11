package app.restvolt.camperlog.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Abläufe rund um Checklisten-Vorlagen: anlegen, Punkte hinzufügen und per Pfeil-Buttons umsortieren. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class ChecklistTemplateFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start() {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(),
                    FakeVehicleRepository(),
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    AccentColor.AZURE,
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(),
                    onAccentColorChange = { },
                ) { }
            }
        }
    }

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

    private fun fieldValue(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun openTemplateEditor() {
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Checklisten").performClick()
        compose.onNodeWithText("Vorlagen").performClick()
        compose.onNodeWithText("Vorlage hinzufügen").performClick()
    }

    @Test
    fun newTemplate_addItemsAndMoveOneUp_savesInTheNewOrder() {
        start()
        openTemplateEditor()

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Testliste")
        compose.onNodeWithText("Punkt hinzufügen").performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Eins")
        compose.onNodeWithText("Punkt hinzufügen").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("Zwei")

        // Den zweiten Punkt ("Zwei") nach oben vor den ersten ("Eins") schieben.
        compose.onAllNodesWithContentDescription("Punkt nach oben verschieben")[1].performClick()
        val fieldsAfterMove = compose.onAllNodes(hasSetTextAction())
        assertEquals("Zwei", fieldValue(fieldsAfterMove[1]))
        assertEquals("Eins", fieldValue(fieldsAfterMove[2]))

        clickSave()

        compose.onNodeWithText("Testliste").performClick()
        val fieldsAfterReopen = compose.onAllNodes(hasSetTextAction())
        assertEquals("Testliste", fieldValue(fieldsAfterReopen[0]))
        assertEquals("Zwei", fieldValue(fieldsAfterReopen[1]))
        assertEquals("Eins", fieldValue(fieldsAfterReopen[2]))
    }

    @Test
    fun templateLifecycle_editReopenThenDeleteFromListAndUndo_persistsChangesAndRestoresTemplate() {
        start()
        openTemplateEditor()

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Packliste")
        compose.onNodeWithText("Punkt hinzufügen").performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Zelt")
        compose.onNodeWithText("Punkt hinzufügen").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("Campingstuhl")

        // Ersten Punkt umbenennen, zweiten entfernen.
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("Sonnenschirm")
        compose.onAllNodesWithContentDescription("Punkt entfernen")[1].performClick()

        val fieldsBeforeSave = compose.onAllNodes(hasSetTextAction())
        assertEquals(2, fieldsBeforeSave.fetchSemanticsNodes().size)
        assertEquals("Sonnenschirm", fieldValue(fieldsBeforeSave[1]))

        clickSave()

        // Zurück auf der Liste: erneut öffnen und die gespeicherten Änderungen bestätigen.
        compose.onNodeWithText("Packliste").performClick()
        val fieldsAfterReopen = compose.onAllNodes(hasSetTextAction())
        assertEquals(2, fieldsAfterReopen.fetchSemanticsNodes().size)
        assertEquals("Packliste", fieldValue(fieldsAfterReopen[0]))
        assertEquals("Sonnenschirm", fieldValue(fieldsAfterReopen[1]))

        // Über die Liste löschen und per Rückgängig wiederherstellen.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Packliste").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Vorlage löschen").performClick()

        compose.onNodeWithText("Packliste").assertDoesNotExist()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Packliste").assertExists()
    }
}
