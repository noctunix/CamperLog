package app.restvolt.camperlog.domain.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private fun step(id: String, completion: StepCompletion = StepCompletion.Information) =
    TourStep(id = id, anchorId = null, titleRes = 1, textRes = 2, completion = completion)

class TourDefinitionTest {

    @Test
    fun init_withEmptySteps_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            TourDefinition(id = "empty", version = 1, steps = emptyList())
        }
    }

    @Test
    fun init_withDuplicateStepIds_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            TourDefinition(id = "dup", version = 1, steps = listOf(step("a"), step("a")))
        }
    }

    @Test
    fun init_withUniqueSteps_succeeds() {
        val tour = TourDefinition(id = "ok", version = 1, steps = listOf(step("a"), step("b")))

        assertEquals(listOf("a", "b"), tour.steps.map { it.id })
    }
}
