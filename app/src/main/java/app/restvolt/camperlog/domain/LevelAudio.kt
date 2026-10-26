package app.restvolt.camperlog.domain

import kotlin.math.abs

/** Beep-Intervall, wenn die Neigung nur knapp außerhalb der Toleranz liegt. */
const val LEVEL_AUDIO_SLOW_INTERVAL_MILLIS: Long = 700L

/** Beep-Intervall ab der doppelten Toleranz, siehe [levelAudioCue]. */
const val LEVEL_AUDIO_FAST_INTERVAL_MILLIS: Long = 250L

/** Tonhöhen je Richtung in Hertz, bewusst nicht-harmonisch für gute Unterscheidbarkeit. */
const val LEVEL_AUDIO_FREQUENCY_FRONT_HZ = 587
const val LEVEL_AUDIO_FREQUENCY_BACK_HZ = 784
const val LEVEL_AUDIO_FREQUENCY_LEFT_HZ = 988
const val LEVEL_AUDIO_FREQUENCY_RIGHT_HZ = 1319

/** Richtung der stärker geneigten Achse für die akustische Rückmeldung der Wasserwaage. */
enum class LevelAudioDirection { FRONT, BACK, LEFT, RIGHT }

/** Akustische Rückmeldung: [direction] der stärker geneigten Achse plus Beep-Intervall. */
data class LevelAudioCue(val direction: LevelAudioDirection, val beepIntervalMillis: Long)

/**
 * Berechnet die akustische Rückmeldung für [tilt]; `null`, solange beide Achsen innerhalb
 * [toleranceDegrees] liegen (kein Ton). Sind beide Achsen außerhalb der Toleranz, gewinnt die mit
 * dem größeren Betrag. Die Vorzeichen folgen der Anzeige in [app.restvolt.camperlog.ui.tools.LevelScreen]:
 * positiver Pitch verschiebt den Punkt nach unten ([LevelAudioDirection.BACK]), positiver Roll nach
 * rechts ([LevelAudioDirection.RIGHT]). Das Beep-Intervall wird schneller, je näher die Neigung an
 * [toleranceDegrees] liegt, und wechselt auf [LEVEL_AUDIO_FAST_INTERVAL_MILLIS] ab der doppelten Toleranz.
 */
fun levelAudioCue(tilt: Tilt, toleranceDegrees: Double = LEVEL_TOLERANCE_DEGREES): LevelAudioCue? {
    val absPitch = abs(tilt.pitchDegrees)
    val absRoll = abs(tilt.rollDegrees)
    if (absPitch <= toleranceDegrees && absRoll <= toleranceDegrees) return null

    val pitchDominates = if (absPitch > toleranceDegrees && absRoll > toleranceDegrees) {
        absPitch >= absRoll
    } else {
        absPitch > toleranceDegrees
    }
    val direction = if (pitchDominates) {
        if (tilt.pitchDegrees > 0) LevelAudioDirection.BACK else LevelAudioDirection.FRONT
    } else {
        if (tilt.rollDegrees > 0) LevelAudioDirection.RIGHT else LevelAudioDirection.LEFT
    }
    val dominantAbs = if (pitchDominates) absPitch else absRoll
    val intervalMillis = if (dominantAbs >= toleranceDegrees * 2) {
        LEVEL_AUDIO_FAST_INTERVAL_MILLIS
    } else {
        LEVEL_AUDIO_SLOW_INTERVAL_MILLIS
    }
    return LevelAudioCue(direction, intervalMillis)
}
