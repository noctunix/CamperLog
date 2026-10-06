package app.restvolt.camperlog.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
 * [CountryPicker] für sich allein, nicht über den vollen Navigationsbaum: Dort mit den rund 250
 * ISO-Ländern bestückt kommt Robolectric in [StationFlowTest] nicht verlässlich zur Ruhe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class CountryPickerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingACountry_callsOnSelectWithItsCode() {
        var selected: String? = null
        compose.setContent {
            CamperLogTheme { CountryPicker(selected = null, locale = Locale.GERMANY, onSelect = { selected = it }, onDismiss = {}) }
        }

        compose.onNodeWithText("Land wählen").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Land suchen")).performTextInput("Österreich")
        compose.onNodeWithText("AT · Österreich").performClick()

        assertEquals("AT", selected)
    }

    @Test
    fun noCountrySelected_callsOnSelectWithNull() {
        var selected: String? = "AT"
        compose.setContent {
            CamperLogTheme { CountryPicker(selected = "AT", locale = Locale.GERMANY, onSelect = { selected = it }, onDismiss = {}) }
        }

        compose.onNodeWithText("Kein Land ausgewählt").performClick()

        assertEquals(null, selected)
    }

    @Test
    fun search_filtersTheListByNameOrCode() {
        compose.setContent {
            CamperLogTheme { CountryPicker(selected = null, locale = Locale.GERMANY, onSelect = {}, onDismiss = {}) }
        }

        compose.onNode(hasSetTextAction() and hasText("Land suchen")).performTextInput("Norwegen")

        compose.onNodeWithText("NO · Norwegen").assertExists()
        compose.onNodeWithText("AT · Österreich").assertDoesNotExist()
    }
}
