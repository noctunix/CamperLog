package app.restvolt.camperlog.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.guide.GuideController
import app.restvolt.camperlog.domain.guide.StepCompletion
import app.restvolt.camperlog.domain.guide.TourDefinition
import app.restvolt.camperlog.domain.guide.TourStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun testTour() = TourDefinition(
    id = "test-tour",
    version = 1,
    steps = listOf(
        TourStep("anchored", "target", R.string.app_name, R.string.more_options, StepCompletion.Information),
        TourStep("centered", null, R.string.app_name, R.string.more_options, StepCompletion.Information),
    ),
)

@Composable
private fun TestScene(controller: GuideController, buttonAlignment: Alignment = Alignment.Center, onButtonClick: () -> Unit) {
    val registry = remember { GuideAnchorRegistry() }
    CompositionLocalProvider(LocalGuideAnchors provides registry) {
        Box(Modifier.fillMaxSize()) {
            Button(
                onClick = onButtonClick,
                modifier = Modifier.align(buttonAlignment).guideAnchor("target"),
            ) {
                Text("App button")
            }
            GuideHost(controller)
        }
    }
}

/** `GuideHost` mit einer kleinen Test-Tour: Ankerfindung, Kartentext und Klick-Ausschluss durch die Barriere. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideHostTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newController() = GuideController(CoroutineScope(Dispatchers.Unconfined))

    private fun buttonCenter() = compose.onNodeWithText("App button").fetchSemanticsNode().boundsInRoot.let {
        Offset(it.left + it.width / 2, it.top + it.height / 2)
    }

    @Test
    fun showsTheCardWithTheCurrentStepTextAndCounter() {
        val controller = newController()
        compose.setContent { TestScene(controller, onButtonClick = {}) }
        compose.runOnIdle { controller.start(testTour()) }
        compose.waitForIdle()

        compose.onNodeWithText("CamperLog").assertExists()
        compose.onNodeWithText("More options").assertExists()
        compose.onNodeWithText("Step 1 of 2").assertExists()
    }

    @Test
    fun tappingOutsideTheHoleDoesNotReachTheRealButtonUnderneath() {
        var clicks = 0
        val controller = newController()
        compose.setContent { TestScene(controller, onButtonClick = { clicks++ }) }
        compose.runOnIdle { controller.start(testTour()) }
        compose.waitForIdle()

        compose.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()

        assertEquals(0, clicks)
    }

    @Test
    fun tappingInsideTheHoleReachesTheRealButtonUnderneath() {
        var clicks = 0
        val controller = newController()
        compose.setContent { TestScene(controller, onButtonClick = { clicks++ }) }
        compose.runOnIdle { controller.start(testTour()) }
        compose.waitForIdle()

        compose.onRoot().performTouchInput { click(buttonCenter()) }
        compose.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun withoutAnAnchor_showsTheCardCenteredAndDoesNotBlockTheRealButton() {
        // Die Karte liegt zentriert; der Button sitzt bewusst in einer Ecke, damit der Klick nicht
        // die Karte selbst trifft, sondern wirklich prüft, dass ohne Anker nichts blockiert wird.
        var clicks = 0
        val controller = newController()
        compose.setContent { TestScene(controller, buttonAlignment = Alignment.TopStart, onButtonClick = { clicks++ }) }
        compose.runOnIdle { controller.start(testTour()) }
        compose.runOnIdle { controller.next() }
        compose.waitForIdle()

        compose.onRoot().performTouchInput { click(buttonCenter()) }
        compose.waitForIdle()

        assertEquals(1, clicks)
    }
}
