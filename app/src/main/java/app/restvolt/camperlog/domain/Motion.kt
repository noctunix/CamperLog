package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow

/** Stillstandsdauer ohne neues Bewegungssignal, nach der [MotionDetector.observeIsMoving] auf Stillstand wechselt. */
const val MOTION_STILLNESS_TIMEOUT_MILLIS: Long = 5 * 60_000L

/**
 * Erkennung von Gerätebewegung, damit der Mindestdistanz-Filter der Trackaufzeichnung
 * ([TrackSamplingConfig], [withMotionAwareness]) nur im tatsächlichen Stillstand wirkt. Die
 * Android-Implementierung nutzt den Trigger-Sensor `TYPE_SIGNIFICANT_MOTION`, keine Play-Services;
 * siehe [app.restvolt.camperlog.data.AndroidMotionDetector].
 */
interface MotionDetector {

    /**
     * Beobachtet den Bewegungszustand: `true` bei Bewegung oder solange der Sensor noch keine
     * Entscheidung getroffen hat, `false` nach [MOTION_STILLNESS_TIMEOUT_MILLIS] ohne neues
     * Bewegungssignal. Startet mit `true`.
     */
    fun observeIsMoving(): Flow<Boolean>
}
