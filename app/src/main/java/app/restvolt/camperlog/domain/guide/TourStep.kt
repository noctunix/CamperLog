package app.restvolt.camperlog.domain.guide

import androidx.annotation.StringRes

/**
 * Ein Schritt einer geführten Tour. [anchorId] referenziert einen über `Modifier.guideAnchor`
 * registrierten Ankerpunkt; `null` zeigt die Erklärkarte zentriert ohne Barriere mit Loch.
 */
data class TourStep(
    val id: String,
    val anchorId: String?,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val completion: StepCompletion,
)
