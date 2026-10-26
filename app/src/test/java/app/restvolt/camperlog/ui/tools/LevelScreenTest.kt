package app.restvolt.camperlog.ui.tools

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.FakeLevelSensor
import app.restvolt.camperlog.domain.Tilt
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wasserwaage: Anzeige der Neigung, Kalibrierung und die feste Hinweiskarte zu den Messpunkten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class LevelScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(sensor: FakeLevelSensor) {
        val viewModel = LevelViewModel(sensor)
        compose.setContent {
            CamperLogTheme {
                LevelScreen(viewModel = viewModel, onBack = {})
            }
        }
    }

    @Test
    fun showsMeasuringPointsHintCard() {
        start(FakeLevelSensor(Tilt(0.0, 0.0)))

        compose.onNodeWithText("Wichtige Messpunkte").assertExists()
        compose.onNodeWithText("Abfluss & Kassette: ohne Gefälle zum Auslauf läuft Grau- oder Schwarzwasser nicht richtig ab.").assertExists()
        compose.onNodeWithText(
            "Kühlschrank: Absorberkühlschränke brauchen oft eine enge Neigungstoleranz. Die genaue Gradzahl findest du in der " +
                "Bedienungsanleitung deines Kühlschranks; sonst leiden Kühlleistung und Lebensdauer.",
        ).assertExists()
    }

    @Test
    fun showsStatusAndDegreesAsPlainTextAndCalibrateResetsThem() {
        start(FakeLevelSensor(Tilt(5.0, 3.0)))

        compose.onNodeWithText("Geneigt").assertExists()
        compose.onNodeWithText("Neigung vorne/hinten: 5,0°").assertExists()
        compose.onNodeWithText("Neigung links/rechts: 3,0°").assertExists()

        compose.onNodeWithText("Auf aktueller Fläche nullen").performClick()

        compose.onNodeWithText("Eben").assertExists()
        compose.onNodeWithText("Neigung vorne/hinten: 0,0°").assertExists()
        compose.onNodeWithText("Neigung links/rechts: 0,0°").assertExists()
    }

    @Test
    fun missingSensorShowsHintInsteadOfControls() {
        start(FakeLevelSensor(initialTilt = null))

        compose.onNodeWithText("Dieses Gerät hat keinen Bewegungssensor für die Wasserwaage.").assertExists()
        compose.onNodeWithText("Auf aktueller Fläche nullen").assertDoesNotExist()
    }
}
