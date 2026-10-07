package app.restvolt.camperlog.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * [TourCountriesCard] für sich allein, nicht über die volle Navigation (siehe KDoc von
 * `app.restvolt.camperlog.ui.CountryPickerTest` zur Begründung): Entfernen eines automatisch
 * erkannten Landes und Hinzufügen eines weiteren über `CountryPicker`, dann Speichern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class TourCountriesCardFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editCountries_removesAutoDetectedAndAddsAnotherViaPicker() {
        var savedAdded: Set<String>? = null
        var savedRemoved: Set<String>? = null

        compose.setContent {
            CamperLogTheme {
                var added by remember { mutableStateOf(emptySet<String>()) }
                var removed by remember { mutableStateOf(emptySet<String>()) }
                TourCountriesCard(
                    autoDetected = setOf("IT", "CH"),
                    manuallyAdded = added,
                    manuallyRemoved = removed,
                    locale = Locale.GERMANY,
                    onSave = { newAdded, newRemoved ->
                        added = newAdded
                        removed = newRemoved
                        savedAdded = newAdded
                        savedRemoved = newRemoved
                    },
                )
            }
        }

        compose.onNodeWithText("Italien", substring = true).assertExists()
        compose.onNodeWithText("Schweiz", substring = true).assertExists()

        compose.onNodeWithText("Länder bearbeiten").performClick()
        // Die Karte zeigt "Italien" weiterhin hinter dem Dialog; auf den entfernbaren Chip im Dialog beschränken.
        compose.onNode(hasText("Italien", substring = true) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Land hinzufügen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Land suchen")).performTextInput("Norwegen")
        compose.onNodeWithText("NO · Norwegen").performClick()
        compose.onNodeWithText("Speichern").performClick()

        assertEquals(setOf("NO"), savedAdded)
        assertEquals(setOf("IT"), savedRemoved)
        compose.onNodeWithText("Italien", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Schweiz", substring = true).assertExists()
        compose.onNodeWithText("Norwegen", substring = true).assertExists()
    }

    @Test
    fun editCountries_restoringARemovedAutoDetectedCountryClearsItsRemoval() {
        compose.setContent {
            CamperLogTheme {
                var removed by remember { mutableStateOf(setOf("IT")) }
                TourCountriesCard(
                    autoDetected = setOf("IT", "CH"),
                    manuallyAdded = emptySet(),
                    manuallyRemoved = removed,
                    locale = Locale.GERMANY,
                    onSave = { _, newRemoved -> removed = newRemoved },
                )
            }
        }

        compose.onNodeWithText("Italien", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Länder bearbeiten").performClick()
        compose.onNodeWithText("Land hinzufügen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Land suchen")).performTextInput("Italien")
        compose.onNodeWithText("IT · Italien").performClick()
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Italien", substring = true).assertExists()
    }
}
