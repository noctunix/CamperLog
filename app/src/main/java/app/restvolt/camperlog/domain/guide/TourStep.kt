package app.restvolt.camperlog.domain.guide

import androidx.annotation.StringRes

/** Bildschirm, den die Navigation beim Betreten eines Schritts ansteuern soll. */
enum class GuideScreen {

    /** Der Schritt bleibt auf dem aktuellen Bildschirm. */
    UNCHANGED,

    /** Tourenliste mit Reiterleiste (Reiter und Daten-Button sichtbar). */
    TOURS_ROOT,

    /** Statistik, die nur die Demo-Tour zeigt. */
    DEMO_STATISTICS,

    /** Karte der Demo-Tour ohne Kachelabruf. */
    DEMO_MAP,
}

/**
 * Ein Schritt einer geführten Tour. [anchorId] referenziert einen über `Modifier.guideAnchor`
 * registrierten Ankerpunkt; `null` zeigt die Erklärkarte zentriert ohne Barriere mit Loch. [screen]
 * lässt die Navigation vor dem Schritt den passenden Bildschirm öffnen.
 */
data class TourStep(
    val id: String,
    val anchorId: String?,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val completion: StepCompletion,
    val screen: GuideScreen = GuideScreen.UNCHANGED,
)
