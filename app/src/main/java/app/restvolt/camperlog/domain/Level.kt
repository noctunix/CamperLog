package app.restvolt.camperlog.domain

import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

/** Toleranz in Grad, innerhalb der eine Fläche noch als eben gilt. */
const val LEVEL_TOLERANCE_DEGREES: Double = 2.0

/** Neigung des Geräts in Grad: [pitchDegrees] vorne/hinten, [rollDegrees] links/rechts. */
data class Tilt(val pitchDegrees: Double, val rollDegrees: Double)

/** Ampel-Einstufung einer Neigung gegenüber einer Toleranz. */
enum class LevelStatus { LEVEL, TILTED }

/**
 * Erkennung der Geräteneigung für die Wasserwaage-Ansicht. Android-Implementierung über
 * Sensorfusion, siehe [app.restvolt.camperlog.data.AndroidLevelSensor].
 */
interface LevelSensor {

    /** Beobachtet die aktuelle Neigung; `null`, solange das Gerät keinen geeigneten Sensor hat. */
    fun observeTilt(): Flow<Tilt?>
}

/** Rechnet [this] relativ zum als eben definierten Nullpunkt [zero] um. */
fun Tilt.calibrated(zero: Tilt): Tilt = Tilt(pitchDegrees - zero.pitchDegrees, rollDegrees - zero.rollDegrees)

/** Stuft [tilt] als [LevelStatus.LEVEL] ein, solange beide Achsen innerhalb [toleranceDegrees] liegen. */
fun levelStatus(tilt: Tilt, toleranceDegrees: Double = LEVEL_TOLERANCE_DEGREES): LevelStatus =
    if (abs(tilt.pitchDegrees) <= toleranceDegrees && abs(tilt.rollDegrees) <= toleranceDegrees) {
        LevelStatus.LEVEL
    } else {
        LevelStatus.TILTED
    }
