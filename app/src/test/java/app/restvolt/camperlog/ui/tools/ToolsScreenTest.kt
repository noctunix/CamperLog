package app.restvolt.camperlog.ui.tools

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tools-Reiter: gemeinsame Kopfzeile mit Suche und Dreipunktmenü. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class ToolsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overflowMenuOpensDataAndSettings() {
        var openedData = false
        var openedSettings = false
        compose.setContent {
            CamperLogTheme {
                ToolsScreen(
                    onOpenLogbook = {},
                    onOpenLevel = {},
                    onOpenSearch = {},
                    onOpenData = { openedData = true },
                    onOpenSettings = { openedSettings = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Daten").performClick()
        assertTrue(openedData)

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun searchActionIsAvailable() {
        var openedSearch = false
        compose.setContent {
            CamperLogTheme {
                ToolsScreen(
                    onOpenLogbook = {},
                    onOpenLevel = {},
                    onOpenSearch = { openedSearch = true },
                    onOpenData = {},
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Suchen").performClick()
        assertTrue(openedSearch)
    }
}
