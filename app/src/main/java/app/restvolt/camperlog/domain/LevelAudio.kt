package app.restvolt.camperlog.domain

import kotlin.math.abs
import kotlin.math.hypot

/** Beep-Intervall, wenn die Neigung nur knapp außerhalb der Toleranz liegt. */
const val LEVEL_AUDIO_SLOW_INTERVAL_MILLIS: Long = 700L

/** Beep-Intervall ab der doppelten Toleranz, siehe [levelAudioCue]. */
const val LEVEL_AUDIO_FAST_INTERVAL_MILLIS: Long = 250L

/** Tonhöhen je Richtung in Hertz, bewusst nicht-harmonisch für gute Unterscheidbarkeit. */
const val LEVEL_AUDIO_FREQUENCY_FRONT_HZ = 587
const val LEVEL_AUDIO_FREQUENCY_BACK_HZ = 784
const val LEVEL_AUDIO_FREQUENCY_LEFT_HZ = 988
const val LEVEL_AUDIO_FREQUENCY_RIGHT_HZ = 1319

/** Frequenz des Dauertons bei „eben", siehe [LevelAudioCue.Level]. */
const val LEVEL_AUDIO_TONE_FREQUENCY_HZ = 441

/** Richtung der stärker geneigten Achse für die akustische Rückmeldung der Wasserwaage. */
enum class LevelAudioDirection { FRONT, BACK, LEFT, RIGHT }

/** Richtungslogik für [levelAudioCue]: entweder die dominante Einzelachse oder beide Achsen gleichzeitig. */
enum class LevelAudioScheme { DOMINANT_AXIS, QUADRANT }

/** Akustische Rückmeldung der Wasserwaage: entweder ein gerichteter Beep oder der Dauerton bei „eben". */
sealed interface LevelAudioCue {

    /** Beep in Richtung [direction] der stärker geneigten Achse, alle [beepIntervalMillis]. */
    data class Beep(val direction: LevelAudioDirection, val beepIntervalMillis: Long) : LevelAudioCue

    /** Dauerton, solange beide Achsen innerhalb der Toleranz liegen. */
    data object Level : LevelAudioCue
}

/**
 * Berechnet die akustische Rückmeldung für [tilt]. Liegen beide Achsen innerhalb [toleranceDegrees],
 * ist das Ergebnis [LevelAudioCue.Level] (Dauerton). Die Vorzeichen folgen der Anzeige in
 * [app.restvolt.camperlog.ui.tools.LevelScreen]: positiver Pitch verschiebt den Punkt nach unten
 * ([LevelAudioDirection.BACK]), positiver Roll nach rechts ([LevelAudioDirection.RIGHT]).
 *
 * Bei [LevelAudioScheme.DOMINANT_AXIS] gewinnt, sind beide Achsen außerhalb der Toleranz, die mit dem
 * größeren Betrag; das Beep-Intervall richtet sich nach dieser Achse. Bei [LevelAudioScheme.QUADRANT]
 * bestimmen die Vorzeichen beider Achsen gemeinsam einen festen Uhrzeigersinn-Umlauf
 * FRONT → RIGHT → BACK → LEFT, und das Intervall richtet sich nach dem euklidischen Abstand beider
 * Achsen. In beiden Fällen wird das Beep-Intervall schneller, je näher die Neigung an [toleranceDegrees]
 * liegt, und wechselt auf [LEVEL_AUDIO_FAST_INTERVAL_MILLIS] ab der doppelten Toleranz.
 */
fun levelAudioCue(
    tilt: Tilt,
    toleranceDegrees: Double = LEVEL_TOLERANCE_DEGREES,
    scheme: LevelAudioScheme = LevelAudioScheme.DOMINANT_AXIS,
): LevelAudioCue {
    val absPitch = abs(tilt.pitchDegrees)
    val absRoll = abs(tilt.rollDegrees)
    if (absPitch <= toleranceDegrees && absRoll <= toleranceDegrees) return LevelAudioCue.Level

    return when (scheme) {
        LevelAudioScheme.DOMINANT_AXIS -> dominantAxisCue(tilt, absPitch, absRoll, toleranceDegrees)
        LevelAudioScheme.QUADRANT -> quadrantCue(tilt, toleranceDegrees)
    }
}

private fun dominantAxisCue(tilt: Tilt, absPitch: Double, absRoll: Double, toleranceDegrees: Double): LevelAudioCue {
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
    return LevelAudioCue.Beep(direction, beepIntervalMillis(dominantAbs, toleranceDegrees))
}

private fun quadrantCue(tilt: Tilt, toleranceDegrees: Double): LevelAudioCue {
    val direction = when {
        tilt.pitchDegrees < 0 && tilt.rollDegrees < 0 -> LevelAudioDirection.FRONT
        tilt.pitchDegrees < 0 && tilt.rollDegrees >= 0 -> LevelAudioDirection.RIGHT
        tilt.pitchDegrees >= 0 && tilt.rollDegrees >= 0 -> LevelAudioDirection.BACK
        else -> LevelAudioDirection.LEFT
    }
    val distance = hypot(tilt.pitchDegrees, tilt.rollDegrees)
    return LevelAudioCue.Beep(direction, beepIntervalMillis(distance, toleranceDegrees))
}

private fun beepIntervalMillis(magnitudeDegrees: Double, toleranceDegrees: Double): Long {
    return if (magnitudeDegrees >= toleranceDegrees * 2) LEVEL_AUDIO_FAST_INTERVAL_MILLIS else LEVEL_AUDIO_SLOW_INTERVAL_MILLIS
}
