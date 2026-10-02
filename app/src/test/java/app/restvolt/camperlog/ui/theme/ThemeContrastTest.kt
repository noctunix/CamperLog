package app.restvolt.camperlog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft die WCAG-Kontraste der Farben, die Auswahl- und Rahmenzustände tragen. */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertContrast(name: String, a: Color, b: Color, min: Double) {
        val ratio = contrast(a, b)
        assertTrue("$name: %.2f < %.1f".format(ratio, min), ratio >= min)
    }

    private fun assertSchemeContrasts(scheme: ColorScheme) {
        // Karten (SectionCard) und Seitenhintergrund, auf denen Felder und Segmente liegen.
        val backgrounds = mapOf("Karte" to scheme.surfaceContainerLowest, "Hintergrund" to scheme.background)
        for ((name, background) in backgrounds) {
            assertContrast("Rahmen auf $name", scheme.outline, background, 3.0)
            assertContrast("Aktives Segment auf $name", scheme.primary, background, 3.0)
        }
        assertContrast("Text im aktiven Segment", scheme.onPrimary, scheme.primary, 4.5)
        assertContrast("Hinweistext auf Karte", scheme.onSurfaceVariant, scheme.surfaceContainerLowest, 4.5)
        assertContrast("Fehlertext auf Karte", scheme.error, scheme.surfaceContainerLowest, 4.5)
        assertContrast("Text auf Hintergrund", scheme.onBackground, scheme.background, 4.5)
    }

    @Test
    fun lightScheme_meetsContrastMinimums() = assertSchemeContrasts(LightColors)

    @Test
    fun darkScheme_meetsContrastMinimums() = assertSchemeContrasts(DarkColors)
}
