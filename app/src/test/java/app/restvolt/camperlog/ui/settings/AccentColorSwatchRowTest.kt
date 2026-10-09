package app.restvolt.camperlog.ui.settings

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.ui.theme.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Auswahl der Akzentfarbe über die Kreis-Swatches: Antippen meldet die Farbe, die aktuelle ist markiert. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class AccentColorSwatchRowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingASwatch_reportsItsAccentColor() {
        var selected: AccentColor? = null
        compose.setContent {
            AccentColorSwatchRow(accentColor = AccentColor.AZURE) { selected = it }
        }
        compose.onNodeWithContentDescription("Waldgrün").performClick()
        assertEquals(AccentColor.FOREST, selected)
    }

    @Test
    fun currentAccentColor_isMarkedSelected() {
        compose.setContent {
            AccentColorSwatchRow(accentColor = AccentColor.PLUM) { }
        }
        compose.onNodeWithContentDescription("Violett").assertIsSelected()
        compose.onNodeWithContentDescription("Azurblau").assertIsNotSelected()
    }
}
