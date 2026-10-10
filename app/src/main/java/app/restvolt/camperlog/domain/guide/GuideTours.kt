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
const val CREATE_FIRST_TOUR_VERSION = 2

/** Aktion, die den FAB-Schritt von [CREATE_FIRST_TOUR_ID] beim echten Klick auf den FAB freischaltet. */
const val CREATE_FIRST_TOUR_FAB_ACTION = "tour.fab.clicked"

/** Aktion, die den Fahrzeug-Schritt freischaltet, sobald ein inline angelegtes Fahrzeug gespeichert ist. */
const val CREATE_FIRST_TOUR_VEHICLE_ACTION = "tour.vehicle.created"

/** Aktion, die den Speichern-Schritt beim echten Speichern der Tour freischaltet. */
const val CREATE_FIRST_TOUR_SAVE_ACTION = "tour.saved"

/** Aktion, die den "Station hinzufügen"-Schritt beim echten Auswählen eines Stationstyps freischaltet. */
const val CREATE_FIRST_TOUR_STATION_FAB_ACTION = "tour.station.fab.clicked"

/** Aktion, die den Stationsformular-Schritt beim echten Speichern der Station freischaltet. */
const val CREATE_FIRST_TOUR_STATION_SAVE_ACTION = "tour.station.saved"

/**
 * Pilot-Tour: ein vollständiger Durchlauf beim Anlegen der ersten eigenen Tour - FAB, jedes
 * Formularfeld einzeln, Speichern, eine erste Station und abschließend der GPS-Track-Abschnitt der
 * jetzt echten Tour, mit einem Hinweis auf die weiterhin erkundbare, simulierte Demo-Tour
 * ([DemoTourSession]) - die zeigt ihren Track in der Tourenliste und im Tourdetail immer, auch wenn
 * die globale Trackaufzeichnung oder "Wetter & Karte" ausgeschaltet sind.
 *
 * Bedingte Abschnitte werden einmalig bei Tourstart entschieden statt während der Tour zu springen:
 * [offerVehicleCreation] bestimmt Anker und Abschlussbedingung des Fahrzeug-Schritts (echtes
 * Fahrzeug vorhanden vs. inline anlegen), [trackSwitchGloballyEnabled] und [hasHomeLocation], ob die
 * beiden "Beim Speichern"-Schritte auf ihre jeweilige Checkbox zeigen oder - weil die Checkbox ohne
 * die zugehörige Einstellung gar nicht erst im Formular steht - ohne Anker nur erklären, wo man die
 * Voraussetzung dafür einstellt.
 *
 * @param offerVehicleCreation `true`, wenn beim Start der Tour noch kein echtes (nicht als Demo
 *   markiertes) Fahrzeug existiert; der Fahrzeug-Schritt bietet dann das Anlegen eines neuen
 *   Fahrzeugs an, statt die Fahrzeugauswahl zu erklären
 * @param trackSwitchGloballyEnabled ob der globale Schalter für die Trackaufzeichnung an ist; nur
 *   dann zeigt das Tourformular die Checkbox "GPS-Track aufzeichnen" überhaupt
 * @param hasHomeLocation ob in den Einstellungen bereits eine Zuhause-Koordinate gesetzt ist; nur
 *   dann zeigt das Tourformular die Checkbox "Beginnt zu Hause" überhaupt
 */
fun createFirstTourTour(
    offerVehicleCreation: Boolean,
    trackSwitchGloballyEnabled: Boolean,
    hasHomeLocation: Boolean,
) = TourDefinition(
    id = CREATE_FIRST_TOUR_ID,
    version = CREATE_FIRST_TOUR_VERSION,
    steps = listOf(
        TourStep(
            "fab",
            "tours.fab.add",
            R.string.tours_new,
            R.string.guide_step_fab_body,
            StepCompletion.Action(CREATE_FIRST_TOUR_FAB_ACTION),
        ),
        TourStep("name", "edit.tour.name", R.string.field_name, R.string.guide_step_name_body, StepCompletion.Information),
        TourStep("tourtype", "edit.tour.tourtype", R.string.field_tour_type, R.string.guide_step_tourtype_body, StepCompletion.Information),
        if (offerVehicleCreation) {
            TourStep(
                "vehicle",
                "edit.tour.vehicle.create",
                R.string.vehicles_add,
                R.string.guide_step_vehicle_create_body,
                StepCompletion.Action(CREATE_FIRST_TOUR_VEHICLE_ACTION),
            )
        } else {
            TourStep("vehicle", "edit.tour.vehicle", R.string.field_vehicle, R.string.guide_step_vehicle_body, StepCompletion.Information)
        },
        TourStep("period", "edit.tour.period", R.string.edit_section_period, R.string.guide_step_period_body, StepCompletion.Information),
        TourStep(
            "track-switch",
            if (trackSwitchGloballyEnabled) "edit.tour.track.checkbox" else null,
            R.string.edit_track_switch_title,
            if (trackSwitchGloballyEnabled) R.string.guide_step_track_switch_body_enabled else R.string.guide_step_track_switch_body_disabled,
            StepCompletion.Information,
        ),
        TourStep(
            "home-switch",
            if (hasHomeLocation) "edit.tour.home.checkbox" else null,
            R.string.guide_step_home_switch_title,
            if (hasHomeLocation) R.string.guide_step_home_switch_body_set else R.string.guide_step_home_switch_body_unset,
            StepCompletion.Information,
        ),
        TourStep(
            "other-costs",
            "edit.tour.other.costs",
            R.string.tour_section_other_costs,
            R.string.guide_step_other_costs_body,
            StepCompletion.Information,
        ),
        TourStep("advanced", "edit.tour.advanced", R.string.edit_section_advanced, R.string.guide_step_advanced_body, StepCompletion.Information),
        TourStep(
            "save",
            "edit.tour.save",
            R.string.action_save,
            R.string.guide_step_save_body,
            StepCompletion.Action(CREATE_FIRST_TOUR_SAVE_ACTION),
        ),
        TourStep(
            "add-station",
            "detail.station.fab",
            R.string.station_fab_add,
            R.string.guide_step_station_fab_body,
            StepCompletion.Action(CREATE_FIRST_TOUR_STATION_FAB_ACTION),
        ),
        TourStep(
            "station-fields",
            "station.type",
            R.string.guide_step_station_fields_title,
            R.string.guide_step_station_fields_body,
            StepCompletion.Information,
        ),
        TourStep(
            "station-save",
            "station.edit.save",
            R.string.action_save,
            R.string.guide_step_station_save_body,
            StepCompletion.Action(CREATE_FIRST_TOUR_STATION_SAVE_ACTION),
        ),
        TourStep(
            "track",
            "detail.track.section",
            R.string.guide_step_track_title,
            R.string.guide_step_track_body,
            StepCompletion.Information,
        ),
    ),
)
