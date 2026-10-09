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

    @Test
    fun createFirstTourTour_startsOnTheFabActionAndEndsOnTheSaveActionAnchoredAtTheSaveButton() {
        val tour = createFirstTourTour()

        assertEquals(CREATE_FIRST_TOUR_ID, tour.id)
        assertEquals(listOf("tours.fab.add", "edit.tour.name", "edit.tour.save"), tour.steps.map { it.anchorId })
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_FAB_ACTION), tour.steps.first().completion)
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_SAVE_ACTION), tour.steps.last().completion)
        assertTrue(tour.steps[1].completion is StepCompletion.Information)
    }
}
