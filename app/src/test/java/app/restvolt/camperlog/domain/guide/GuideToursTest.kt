package app.restvolt.camperlog.domain.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideToursTest {

    @Test
    fun introductionTour_pointsAtTheFourMainAnchorsInOrder() {
        val tour = introductionTour()

        assertEquals(INTRODUCTION_TOUR_ID, tour.id)
        assertEquals(listOf("nav.tab.tours", "nav.tab.logbook", "nav.tab.vehicle", "nav.data"), tour.steps.map { it.anchorId })
        assertTrue(tour.steps.all { it.completion is StepCompletion.Information })
    }
}
