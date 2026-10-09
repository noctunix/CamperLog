package app.restvolt.camperlog.domain.guide

import kotlinx.serialization.Serializable

/**
 * Persistenter Fortschritt einer Tour. [currentStepId] ist für ein vollständiges Wiederaufnehmen
 * mitten im Schritt vorgesehen, wird aber noch nicht ausgewertet; [completed] allein entscheidet,
 * ob [tourId] in [version] erneut gezeigt wird.
 */
@Serializable
data class GuideCheckpoint(
    val schema: Int = 1,
    val tourId: String,
    val version: Int,
    val currentStepId: String? = null,
    val completed: Boolean = false,
)
