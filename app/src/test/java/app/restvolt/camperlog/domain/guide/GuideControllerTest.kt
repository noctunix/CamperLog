package app.restvolt.camperlog.domain.guide

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun step(id: String, completion: StepCompletion = StepCompletion.Information, anchorId: String? = null) =
    TourStep(id = id, anchorId = anchorId, titleRes = 1, textRes = 2, completion = completion)

private fun threeStepTour(
    step1Completion: StepCompletion = StepCompletion.Information,
    step2Completion: StepCompletion = StepCompletion.Action("confirm"),
    step3Completion: StepCompletion = StepCompletion.Information,
) = TourDefinition(
    id = "fake-tour",
    version = 1,
    steps = listOf(step("s1", step1Completion), step("s2", step2Completion), step("s3", step3Completion)),
)

/** Zustandsautomat der geführten Tour, mit einer kleinen Fake-Tour aus drei Schritten. */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideControllerTest {

    @Test
    fun start_setsTheFirstStepAndItsPhase() = runTest {
        val controller = GuideController(this)

        controller.start(threeStepTour())
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("s1", state.currentStep?.id)
        assertEquals(0, state.stepIndex)
        assertEquals(3, state.stepCount)
        assertEquals(GuidePhase.ACTIVE, state.phase)
    }

    @Test
    fun start_onAnActionStep_beginsBlocked() = runTest {
        val controller = GuideController(this)

        controller.start(threeStepTour(step1Completion = StepCompletion.Action("a")))
        advanceUntilIdle()

        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun next_onAnInformationStep_advances() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()

        controller.next()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("s2", state.currentStep?.id)
        assertEquals(1, state.stepIndex)
        assertEquals(GuidePhase.BLOCKED, state.phase)
    }

    @Test
    fun next_whileBlocked_isIgnored() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        // Jetzt auf s2 (Action), BLOCKED.

        controller.next()
        advanceUntilIdle()

        assertEquals("s2", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun completeAction_withMatchingId_unblocksTheStep() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()

        controller.completeAction("confirm")
        advanceUntilIdle()

        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)
    }

    @Test
    fun completeAction_withWrongId_staysBlocked() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()

        controller.completeAction("something-else")
        advanceUntilIdle()

        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun completeAction_afterLeavingTheStep_isIgnored() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        controller.completeAction("confirm")
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        // Jetzt auf s3 (Information), ACTIVE.

        controller.completeAction("confirm")
        advanceUntilIdle()

        assertEquals("s3", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)
    }

    @Test
    fun completeAction_queuedBeforeAStepChangeIsProcessed_doesNotLeakIntoTheNewStep() = runTest {
        // Zwei Schritte warten absichtlich auf dieselbe actionId, um zu zeigen, dass die
        // Generation und nicht nur der Id-Abgleich die veraltete Meldung verwirft.
        val controller = GuideController(this)
        controller.start(
            TourDefinition(
                id = "reused-action-id",
                version = 1,
                steps = listOf(step("s1"), step("s2", StepCompletion.Action("confirm"))),
            ),
        )
        advanceUntilIdle()

        // next() wird noch nicht verarbeitet (keine advanceUntilIdle dazwischen); completeAction()
        // merkt sich dabei die Generation von s1, bevor next() sie durch den Wechsel zu s2 erhöht.
        controller.next()
        controller.completeAction("confirm")
        advanceUntilIdle()

        assertEquals("s2", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun setCondition_satisfied_unblocksAndUnsatisfied_blocksAgain() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour(step1Completion = StepCompletion.Condition("valid")))
        advanceUntilIdle()
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)

        controller.setCondition("valid", satisfied = true)
        advanceUntilIdle()
        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)

        controller.setCondition("valid", satisfied = false)
        advanceUntilIdle()
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun setCondition_afterLeavingTheStep_isIgnored() = runTest {
        val controller = GuideController(this)
        controller.start(
            TourDefinition(
                id = "t",
                version = 1,
                steps = listOf(step("s1", StepCompletion.Condition("valid")), step("s2")),
            ),
        )
        advanceUntilIdle()
        controller.setCondition("valid", satisfied = true)
        controller.next()
        advanceUntilIdle()
        // Jetzt auf s2 (Information), ACTIVE.

        controller.setCondition("valid", satisfied = false)
        advanceUntilIdle()

        assertEquals("s2", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)
    }

    @Test
    fun back_returnsToThePreviousStepAndResetsItsPhase() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        controller.completeAction("confirm")
        advanceUntilIdle()
        // s2, ACTIVE (erfüllt).

        controller.back()
        advanceUntilIdle()

        assertEquals("s1", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)
    }

    @Test
    fun back_onAnActionStepReenteredFromForward_isBlockedAgainEvenIfPreviouslySatisfied() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        controller.completeAction("confirm")
        controller.next()
        advanceUntilIdle()
        // s3, ACTIVE.

        controller.back()
        advanceUntilIdle()

        assertEquals("s2", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun back_atTheFirstStep_isIgnored() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()

        controller.back()
        advanceUntilIdle()

        assertEquals("s1", controller.state.value.currentStep?.id)
        assertEquals(0, controller.state.value.stepIndex)
    }

    @Test
    fun next_onTheLastStep_endsTheTour() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next() // s2
        advanceUntilIdle()
        controller.completeAction("confirm")
        controller.next() // s3
        advanceUntilIdle()

        controller.next()
        advanceUntilIdle()

        assertEquals(GuidePhase.ENDED, controller.state.value.phase)
    }

    @Test
    fun pause_hidesProgressAndResumeRestoresTheExactPhase() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        // s2, BLOCKED.

        controller.pause()
        advanceUntilIdle()
        assertEquals(GuidePhase.PAUSED, controller.state.value.phase)

        controller.resume()
        advanceUntilIdle()
        assertEquals("s2", controller.state.value.currentStep?.id)
        assertEquals(GuidePhase.BLOCKED, controller.state.value.phase)
    }

    @Test
    fun pause_thenSatisfyingTheAction_thenResume_restoresActiveNotBlocked() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()
        controller.pause()
        advanceUntilIdle()

        // Eine Pause darf nicht verhindern, dass eine zwischenzeitlich gemeldete Aktion zählt.
        controller.completeAction("confirm")
        controller.resume()
        advanceUntilIdle()

        assertEquals(GuidePhase.ACTIVE, controller.state.value.phase)
    }

    @Test
    fun end_stopsTheTourFromAnyPhase() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()

        controller.end()
        advanceUntilIdle()

        assertEquals(GuidePhase.ENDED, controller.state.value.phase)
    }

    @Test
    fun next_afterEnd_hasNoEffect() = runTest {
        val controller = GuideController(this)
        controller.start(threeStepTour())
        advanceUntilIdle()
        controller.end()
        advanceUntilIdle()

        controller.next()
        advanceUntilIdle()

        assertEquals(GuidePhase.ENDED, controller.state.value.phase)
    }

    @Test
    fun initialState_isIdleWithNoTour() = runTest {
        val controller = GuideController(this)

        assertNull(controller.state.value.tour)
        assertEquals(GuidePhase.IDLE, controller.state.value.phase)
    }
}
