package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun remembersExplicitChoiceButNotAnAutomaticErrorExpansion() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("form_sections", Context.MODE_PRIVATE).edit().clear().commit()
        val instance = mutableIntStateOf(0)
        val error = mutableStateOf(false)
        compose.setContent {
            CamperLogTheme {
                key(instance.intValue) {
                    FormSection("test", "More details", "Stored value", error.value) { Text("Input field") }
                }
            }
        }
        compose.onNodeWithText("Stored value").assertExists()
        compose.onNodeWithText("Input field").assertDoesNotExist()
        compose.runOnIdle { error.value = true }
        compose.onNodeWithText("Input field").assertExists()
        compose.runOnIdle { error.value = false; instance.intValue++ }
        compose.onNodeWithText("Input field").assertDoesNotExist()
        compose.onNodeWithText("More details").performClick()
        compose.runOnIdle { instance.intValue++ }
        compose.onNodeWithText("Input field").assertExists()
    }
}
