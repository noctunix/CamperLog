package app.restvolt.camperlog.domain.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun defaultTour() = createFirstTourTour(
        offerVehicleCreation = false,
        trackSwitchGloballyEnabled = true,
        hasHomeLocation = true,
    )

    @Test
    fun createFirstTourTour_hasAllStepsAnchoredInOrder_withARealVehicleAndBothSwitchesAvailable() {
        val tour = defaultTour()

        assertEquals(CREATE_FIRST_TOUR_ID, tour.id)
        assertEquals(
            listOf(
                "tours.fab.add",
                "edit.tour.name",
                "edit.tour.tourtype",
                "edit.tour.vehicle",
                "edit.tour.period",
                "edit.tour.track.checkbox",
                "edit.tour.home.checkbox",
                "edit.tour.other.costs",
                "edit.tour.advanced",
                "edit.tour.save",
                "detail.station.fab",
                "station.type",
                "station.edit.save",
                "detail.track.section",
            ),
            tour.steps.map { it.anchorId },
        )
    }

    @Test
    fun createFirstTourTour_everyStepIdIsUnique() {
        val tour = defaultTour()

        assertEquals(tour.steps.size, tour.steps.map { it.id }.toSet().size)
    }

    @Test
    fun createFirstTourTour_actionStepsWaitForTheirRealOutcome() {
        val tour = defaultTour()
        val byId = tour.steps.associateBy { it.id }

        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_FAB_ACTION), byId.getValue("fab").completion)
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_SAVE_ACTION), byId.getValue("save").completion)
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_STATION_FAB_ACTION), byId.getValue("add-station").completion)
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_STATION_SAVE_ACTION), byId.getValue("station-save").completion)
        // Mit vorhandenem echtem Fahrzeug erklärt der Schritt die Auswahl nur, statt eine Aktion abzuwarten.
        assertTrue(byId.getValue("vehicle").completion is StepCompletion.Information)
    }

    @Test
    fun createFirstTourTour_offersInlineVehicleCreation_whenNoRealVehicleExistsYet() {
        val tour = createFirstTourTour(offerVehicleCreation = true, trackSwitchGloballyEnabled = true, hasHomeLocation = true)
        val vehicleStep = tour.steps.single { it.id == "vehicle" }

        assertEquals("edit.tour.vehicle.create", vehicleStep.anchorId)
        assertEquals(StepCompletion.Action(CREATE_FIRST_TOUR_VEHICLE_ACTION), vehicleStep.completion)
    }

    @Test
    fun createFirstTourTour_explainsRatherThanHidesTheTrackSwitchStep_whenTheGlobalSwitchIsOff() {
        val tour = createFirstTourTour(offerVehicleCreation = false, trackSwitchGloballyEnabled = false, hasHomeLocation = true)
        val trackStep = tour.steps.single { it.id == "track-switch" }

        assertNull(trackStep.anchorId)
        assertTrue(trackStep.completion is StepCompletion.Information)
    }

    @Test
    fun createFirstTourTour_explainsRatherThanHidesTheHomeSwitchStep_whenNoHomeLocationIsSet() {
        val tour = createFirstTourTour(offerVehicleCreation = false, trackSwitchGloballyEnabled = true, hasHomeLocation = false)
        val homeStep = tour.steps.single { it.id == "home-switch" }

        assertNull(homeStep.anchorId)
        assertTrue(homeStep.completion is StepCompletion.Information)
    }

    @Test
    fun createFirstTourTour_endsOnAnInformationStepAboutTheGpsTrack() {
        val tour = defaultTour()

        assertTrue(tour.steps.last().completion is StepCompletion.Information)
        assertEquals("track", tour.steps.last().id)
    }
}
