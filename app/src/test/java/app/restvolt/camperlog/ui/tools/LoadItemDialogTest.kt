package app.restvolt.camperlog.ui.tools

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [LoadItemDialog] für sich allein, siehe CountryPickerTest zum Grund. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class LoadItemDialogTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun nameField() = compose.onNode(hasSetTextAction() and hasText("Name"))
    private fun massField() = compose.onNode(hasSetTextAction() and hasText("Masse pro Stück (kg)"))
    private fun positionField() = compose.onNode(hasSetTextAction() and hasText("Position (cm)"))

    @Test
    fun fillingInNameMassAndPosition_savesANewItemWithThem() {
        var saved: LoadItem? = null
        compose.setContent {
            CamperLogTheme {
                LoadItemDialog(vehicleId = 1, original = null, onDismiss = {}, onSave = { saved = it })
            }
        }

        nameField().performTextInput("Grill")
        massField().performTextInput("3,5")
        positionField().performTextInput("120")
        compose.onNodeWithText("Speichern").performClick()

        assertEquals("Grill", saved?.name)
        assertEquals(3_500L, saved?.massGrams)
        assertEquals(120, saved?.positionCm)
    }

    @Test
    fun savingWithoutAName_showsTheRequiredErrorAndDoesNotSave() {
        var saved: LoadItem? = null
        compose.setContent {
            CamperLogTheme {
                LoadItemDialog(vehicleId = 1, original = null, onDismiss = {}, onSave = { saved = it })
            }
        }

        massField().performTextInput("3,5")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Name erforderlich").assertExists()
        assertNull(saved)
    }
}
