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
