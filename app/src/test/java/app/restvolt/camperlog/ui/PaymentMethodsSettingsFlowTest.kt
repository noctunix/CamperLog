package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Abschnitt "Zahlungsarten" in den Einstellungen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class PaymentMethodsSettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openSettings(paymentMethods: FakePaymentMethodRepository = FakePaymentMethodRepository()): FakePaymentMethodRepository {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(emptyList()), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(),
                    FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(),
                    FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(), homeLocations = FakeHomeLocationRepository(), paymentMethods = paymentMethods,
                    diagnosticsLog = FakeDiagnosticsLogRepository(), onAccentColorChange = { },
                ) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Zahlungsarten"))
        return paymentMethods
    }

    @Test
    fun showsTheDefaultsAndRemovesAnEntry() {
        val repository = openSettings()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Kreditkarte"))

        compose.onNodeWithContentDescription("Kreditkarte entfernen").performClick()

        assertEquals(listOf("Bar", "EC-Karte", "PayPal"), repository.methods.value)
        compose.onNodeWithText("Kreditkarte").assertDoesNotExist()
    }

    @Test
    fun addsACustomEntry() {
        val repository = openSettings()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasSetTextAction() and hasText("Neue Zahlungsart"))

        compose.onNode(hasSetTextAction() and hasText("Neue Zahlungsart")).performTextInput("Apple Pay")
        compose.onNodeWithText("Hinzufügen").performClick()

        assertEquals(listOf("Kreditkarte", "Bar", "EC-Karte", "PayPal", "Apple Pay"), repository.methods.value)
        compose.onNodeWithText("Apple Pay").assertExists()
    }

    @Test
    fun restoresTheDefaults() {
        val repository = openSettings()
        repository.remove("Bar")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Vorgaben wiederherstellen"))

        compose.onNodeWithText("Vorgaben wiederherstellen").performClick()

        assertEquals(listOf("Kreditkarte", "Bar", "EC-Karte", "PayPal"), repository.methods.value)
    }
}
