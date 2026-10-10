package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.guide.GuideController
import app.restvolt.camperlog.domain.guide.StepCompletion
import app.restvolt.camperlog.domain.guide.TourDefinition
import app.restvolt.camperlog.domain.guide.TourStep
import app.restvolt.camperlog.ui.guide.GuideAnchorRegistry
import app.restvolt.camperlog.ui.guide.GuideHost
import app.restvolt.camperlog.ui.guide.LocalGuideAnchors
import app.restvolt.camperlog.ui.guide.guideAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun dateFieldTour() = TourDefinition(
    id = "date-field-test-tour",
    version = 1,
    steps = listOf(TourStep("pick-date", "date.anchor", R.string.app_name, R.string.more_options, StepCompletion.Information)),
)

@Composable
private fun DateFieldTestScene(controller: GuideController, showGuide: Boolean) {
    val registry = remember { GuideAnchorRegistry() }
    CompositionLocalProvider(LocalGuideAnchors provides registry) {
        Box(Modifier.fillMaxSize()) {
            DateField(
                label = "Start",
                date = null,
                error = null,
                onDateSelected = {},
                modifier = Modifier.guideAnchor("date.anchor"),
            )
            if (showGuide) GuideHost(controller)
        }
    }
}

/**
 * Ein `DatePickerDialog`-Trigger, dessen Feld exakt den Anker eines Tourschritts ausfüllt, öffnet
 * sich über einen echten per `performTouchInput` injizierten Zeigerklick durch das Loch der
 * Barriere - mit und ohne laufende Tour. Ein per Klick-Position geöffneter Dialog bestätigt, dass
 * die Barriere ein Loch nicht nur zeichnet, sondern auch tatsächlich klickbar lässt; `onRoot()`
 * dient dafür als Ziel, weil `performTouchInput` auf einem konkreten Knoten dessen `Offset` relativ
 * zum Knoten selbst statt zum Root interpretiert - bei absoluten Root-Koordinaten (wie hier aus
 * `boundsInRoot()`) träfe der Klick sonst daneben und würde einen Barriere-Fehler nur vortäuschen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideHostDateFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newController() = GuideController(CoroutineScope(Dispatchers.Unconfined))

    private fun calendarIconCenter() =
        compose.onNodeWithContentDescription("Choose Start").fetchSemanticsNode().boundsInRoot.let {
            Offset(it.left + it.width / 2, it.top + it.height / 2)
        }

    @Test
    fun withoutAGuideTour_realTouchOnTheCalendarIconOpensTheDialog() {
        val controller = newController()
        compose.setContent { DateFieldTestScene(controller, showGuide = false) }
        compose.waitForIdle()

        compose.onRoot().performTouchInput { click(calendarIconCenter()) }
        compose.waitForIdle()

        compose.onAllNodesWithText("OK").assertAny()
    }

    @Test
    fun duringAGuideTour_realTouchInsideTheAnchorHoleOpensTheDialog() {
        val controller = newController()
        compose.setContent { DateFieldTestScene(controller, showGuide = true) }
        compose.runOnIdle { controller.start(dateFieldTour()) }
        compose.waitForIdle()

        val dialogsBefore = compose.onAllNodes(isDialog()).fetchSemanticsNodes().size

        compose.onRoot().performTouchInput { click(calendarIconCenter()) }
        compose.waitForIdle()

        val dialogsAfter = compose.onAllNodes(isDialog()).fetchSemanticsNodes().size
        assertTrue("expected a new dialog window, had $dialogsBefore before and $dialogsAfter after", dialogsAfter > dialogsBefore)
        compose.onAllNodesWithText("OK").assertAny()
    }

    @Test
    fun duringAGuideTour_semanticClickAlsoOpensTheDialog() {
        val controller = newController()
        compose.setContent { DateFieldTestScene(controller, showGuide = true) }
        compose.runOnIdle { controller.start(dateFieldTour()) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Choose Start").performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText("OK").assertAny()
    }
}

private fun SemanticsNodeInteractionCollection.assertAny() {
    assertTrue(fetchSemanticsNodes().isNotEmpty())
}
