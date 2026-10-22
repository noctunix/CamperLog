package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.data.DiagnosticsLogSettings
import app.restvolt.camperlog.domain.DiagnosticsLogEntry
import app.restvolt.camperlog.domain.DiagnosticsLogSource
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Schalter "Diagnose-Protokoll" und die Fehlerprotokoll-Seite in den Einstellungen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class DiagnosticsLogSettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.getSharedPreferences("diagnostics_log", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openSettings(diagnosticsLog: FakeDiagnosticsLogRepository = FakeDiagnosticsLogRepository()) {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(emptyList()), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(),
                    FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(),
                    FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(), homeLocations = FakeHomeLocationRepository(),
                    diagnosticsLog = diagnosticsLog, diagnosticsLogSettings = DiagnosticsLogSettings(context),
                    onAccentColorChange = { },
                    formDraftStore = FakeFormDraftStore(),
                ) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Diagnose-Protokoll"))
    }

    @Test
    fun switchIsOffByDefault() {
        openSettings()

        assertFalse(DiagnosticsLogSettings(context).enabled.value)
    }

    @Test
    fun togglingTheSwitch_persistsIt() {
        openSettings()

        compose.onNodeWithText("Diagnose-Protokoll").performClick()

        assertTrue(DiagnosticsLogSettings(context).enabled.value)
    }

    @Test
    fun openingTheLog_showsThePrefilledEntry() {
        val entry = DiagnosticsLogEntry.capture(
            timestamp = Instant.parse("2026-01-02T03:04:05Z"),
            source = DiagnosticsLogSource.TRACK_RECORDING,
            fatal = false,
            tag = "Test",
            rawMessage = "etwas ging schief",
        )
        openSettings(FakeDiagnosticsLogRepository(listOf(entry)))

        compose.onNodeWithText("Fehlerprotokoll anzeigen").performClick()

        compose.onNodeWithText("Test: etwas ging schief").assertExists()
    }

    @Test
    fun clearingTheLog_removesAllEntries() {
        val entry = DiagnosticsLogEntry.capture(
            timestamp = Instant.now(),
            source = DiagnosticsLogSource.CRASH,
            fatal = true,
            tag = "Crash",
            rawMessage = "Absturz",
        )
        openSettings(FakeDiagnosticsLogRepository(listOf(entry)))
        compose.onNodeWithText("Fehlerprotokoll anzeigen").performClick()

        compose.onNodeWithContentDescription("Protokoll löschen").performClick()
        compose.onNodeWithText("Protokoll löschen").performClick()

        compose.onNodeWithText("Noch keine Einträge.").assertExists()
    }
}
