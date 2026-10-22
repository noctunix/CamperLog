package app.restvolt.camperlog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class OptionalFeatureActionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun inactiveActionOnlyActivatesAfterExplicitConfirmation() {
        var activated = 0
        compose.setContent {
            CamperLogTheme {
                InactiveFeatureButton("Standort übernehmen", OptionalFeature.LOCATION, { activated++ })
            }
        }
        compose.onNodeWithText("Standort übernehmen · Nicht aktiviert").performClick()
        assertEquals(0, activated)
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(0, activated)
        compose.onNodeWithText("Standort übernehmen · Nicht aktiviert").performClick()
        compose.onNodeWithText("Aktivieren").performClick()
        assertEquals(1, activated)
    }

    @Test
    fun mapConfirmationActivatesBeforeOpeningAndDoesNotRepeatTheDialog() {
        val calls = mutableListOf<String>()
        compose.setContent {
            var enabled by remember { mutableStateOf(false) }
            CamperLogTheme {
                OptionalFeatureIconButton(
                    enabled, true, "Ort fehlt", OptionalFeature.INTERNET,
                    onActivate = { calls += "activate"; enabled = true },
                    onClick = { calls += "open" }, label = "Karte", icon = R.drawable.ic_map,
                )
            }
        }
        compose.onNodeWithContentDescription("Karte · Nicht aktiviert").performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("Aktivieren").performClick()
        assertEquals(listOf("activate", "open"), calls)
        compose.onNodeWithContentDescription("Karte").performClick()
        assertEquals(listOf("activate", "open", "open"), calls)
    }

    @Test
    fun missingCoordinatesExplainThePrerequisiteWithoutActivatingInternet() {
        var activated = false
        var opened = false
        compose.setContent {
            CamperLogTheme {
                OptionalFeatureIconButton(
                    false, false, "Ergänze zuerst einen Ort.", OptionalFeature.INTERNET,
                    onActivate = { activated = true }, onClick = { opened = true },
                    label = "Karte", icon = R.drawable.ic_map,
                )
            }
        }
        compose.onNodeWithContentDescription("Karte · Nicht aktiviert").performClick()
        compose.onNodeWithText("Ergänze zuerst einen Ort.").assertExists()
        compose.onNodeWithText("Aktivieren").assertDoesNotExist()
        assertEquals(false, activated)
        assertEquals(false, opened)
    }
}
