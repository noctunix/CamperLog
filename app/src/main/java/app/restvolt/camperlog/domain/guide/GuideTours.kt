package app.restvolt.camperlog.domain.guide

import app.restvolt.camperlog.R

/** Id und Version des Rundgangs durch die Hauptbereiche; startet einmalig nach der Ersteinrichtung. */
const val INTRODUCTION_TOUR_ID = "tour.introduction"
const val INTRODUCTION_TOUR_VERSION = 1

/** Rundgang über die vier Hauptreiter (Touren, Bordbuch, Fahrzeug) und den Daten-Button. */
fun introductionTour() = TourDefinition(
    id = INTRODUCTION_TOUR_ID,
    version = INTRODUCTION_TOUR_VERSION,
    steps = listOf(
        TourStep("welcome", "nav.tab.tours", R.string.title_intro_welcome, R.string.body_intro_welcome, StepCompletion.Information),
        TourStep("logbook", "nav.tab.logbook", R.string.title_intro_logbook, R.string.body_intro_logbook, StepCompletion.Information),
        TourStep("vehicle", "nav.tab.vehicle", R.string.title_intro_vehicle, R.string.body_intro_vehicle, StepCompletion.Information),
        TourStep("data", "nav.data", R.string.title_intro_data, R.string.body_intro_data, StepCompletion.Information),
    ),
)

/** Id und Version der Pilot-Tour "Erste Tour anlegen". */
const val CREATE_FIRST_TOUR_ID = "tour.create-first"
const val CREATE_FIRST_TOUR_VERSION = 1

/** Aktion, die den letzten Schritt von [CREATE_FIRST_TOUR_ID] beim echten Speichern der Tour freischaltet. */
const val CREATE_FIRST_TOUR_SAVE_ACTION = "tour.saved"

/** Pilot-Tour: FAB, Namensfeld und Speichern-Button beim Anlegen der ersten Tour. */
fun createFirstTourTour() = TourDefinition(
    id = CREATE_FIRST_TOUR_ID,
    version = CREATE_FIRST_TOUR_VERSION,
    steps = listOf(
        TourStep("fab", "tours.fab.add", R.string.tours_new, R.string.guide_step_fab_body, StepCompletion.Information),
        TourStep("name", "edit.tour.name", R.string.field_name, R.string.guide_step_name_body, StepCompletion.Information),
        TourStep(
            "save",
            "edit.tour.save",
            R.string.action_save,
            R.string.guide_step_save_body,
            StepCompletion.Action(CREATE_FIRST_TOUR_SAVE_ACTION),
        ),
    ),
)
