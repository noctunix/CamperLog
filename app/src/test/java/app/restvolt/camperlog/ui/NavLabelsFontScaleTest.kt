package app.restvolt.camperlog.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die Beschriftungen der unteren Navigation bei 200 % Schriftgröße auf 360 dp Breite:
 * mit vier Reitern bleiben 84 dp pro Eintrag, "Stationen" und "Bordbuch" sind dort am längsten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
class NavLabelsFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navLabels_atDoubleFontScale_wrapWithoutClipping() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
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
                        formDraftStore = FakeFormDraftStore(),
                    ) { }
                }
            }
        }

        listOf("Touren", "Stationen", "Bordbuch", "Fahrzeug").forEach { label ->
            val result = compose.onNodeWithText(label).textLayoutResult()
            assertTrue("Label muss bei 2.0x lesbar bleiben", result.lineCount in 1..2)
            assertTrue("Label darf nicht auf Kleinstschrift schrumpfen", result.layoutInput.style.fontSize.value >= 12f)
            assertFalse("Label \"$label\" wird bei 2.0x abgeschnitten: size=${result.size}, lines=${result.lineCount}, height=${result.multiParagraph.height}", result.hasVisualOverflow)
        }
    }

    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = checkNotNull(fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action) {
            "Kein GetTextLayoutResult auf diesem Knoten"
        }
        action.invoke(results)
        return results.single()
    }
}
