package app.restvolt.camperlog.ui.vehicle

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.CamperLogNavHost
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeBackupImporter
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Abläufe rund um Fahrzeugdokumente: Anlegen mit Ablaufdatum, Erinnerungskarte, Detail und Löschen. */
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
                    FakeAttachmentRepository(),
                    FakeAttachmentFileStore(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                ) { }
            }
        }
        return documents
    }

    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performClick()
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun addDocumentWithExpiry_showsReminderCardAndOpensDetail() {
        val documents = start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Noch keine Dokumente.").assertExists()

        compose.onNodeWithText("Dokument hinzufügen").performClick()
        compose.onNodeWithText("Versicherung").performClick()
        compose.onNode(hasSetTextAction() and hasText("Titel")).performTextInput("KFZ-Police")
        pickDay("Ablaufdatum", LocalDate.now().dayOfMonth)
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

        // Nach dem ersten Speichern eines neuen Dokuments steht direkt seine Detailseite.
        compose.onNodeWithText("KFZ-Police").assertExists()
        assertEquals(1, documents.documents.size)

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNode(hasText("fällig in", substring = true) and hasClickAction()).assertExists()

        // Die Erinnerungskarte des ablaufenden Dokuments öffnet dessen Detailseite statt der Fahrzeugbearbeitung.
        compose.onNode(hasText("fällig in", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("KFZ-Police").assertExists()
        compose.onNodeWithText("Versicherung").assertExists()
    }

    @Test
    fun deleteDocument_removesItAfterConfirmation() {
        val documents = start()
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithText("Dokument hinzufügen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Titel")).performTextInput("Fahrzeugschein")
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

        compose.onNodeWithContentDescription("Dokument löschen").performClick()
        compose.onNodeWithText("Dokument löschen?").assertExists()
        compose.onNode(hasText("Dokument löschen") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Noch keine Dokumente.").assertExists()
        assertEquals(0, documents.documents.size)
    }
}
