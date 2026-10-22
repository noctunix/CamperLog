package app.restvolt.camperlog.domain.guide

import androidx.annotation.StringRes

/** Screen the navigation should open when a step is entered. */
enum class GuideScreen {

    /** The step stays on the current screen. */
    UNCHANGED,

    /** Tour list with tab bar. */
    TOURS_ROOT,

    /** Statistics of the demo tour. */
    DEMO_STATISTICS,

    /** Offline map of the demo tour. */
    DEMO_MAP,
}

/**
 * One step of a guided tour. [anchorId] refers to an anchor registered via `Modifier.guideAnchor`;
 * `null` centres the card. [screen] is opened before the step.
 */
data class TourStep(
    val id: String,
    val anchorId: String?,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val completion: StepCompletion,
    val screen: GuideScreen = GuideScreen.UNCHANGED,
)
