package app.restvolt.camperlog.domain.guide

/** Bedingung, unter der ein [TourStep] weitergeschaltet werden darf. */
sealed interface StepCompletion {

    /** Der Weiter-Button allein reicht. */
    data object Information : StepCompletion

    /** Wartet auf eine extern über [GuideController.completeAction] gemeldete erfolgreiche Aktion. */
    data class Action(val actionId: String) : StepCompletion

    /** Wartet auf einen extern über [GuideController.setCondition] gesetzten Wahrheitswert. */
    data class Condition(val conditionId: String) : StepCompletion
}
