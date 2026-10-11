package app.restvolt.camperlog.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import app.restvolt.camperlog.domain.MOTION_STILLNESS_TIMEOUT_MILLIS
import app.restvolt.camperlog.domain.MotionDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * [MotionDetector] über den Trigger-Sensor `TYPE_SIGNIFICANT_MOTION`, keine Play-Services. Der
 * Sensor löst nur einmalig aus und wird nach jedem Auslösen neu angemeldet, um weitere Bewegung zu
 * erkennen; bleibt er [stillnessTimeoutMillis] lang stumm, gilt das Gerät als stillstehend. Fehlt
 * der Sensor (z. B. mancher Emulator), bleibt der Zustand dauerhaft `false`, damit ohne
 * Bewegungserkennung das bisherige, intervallabhängige Filterverhalten unverändert gilt.
 */
class AndroidMotionDetector(
    private val context: Context,
    private val stillnessTimeoutMillis: Long = MOTION_STILLNESS_TIMEOUT_MILLIS,
) : MotionDetector {

    private val sensorManager get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun observeIsMoving(): Flow<Boolean> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        if (sensor == null) {
            send(false)
            awaitClose { }
            return@callbackFlow
        }
        var stillnessJob: Job? = null
        fun armStillnessTimeout() {
            stillnessJob?.cancel()
            stillnessJob = launch {
                delay(stillnessTimeoutMillis)
                trySend(false)
            }
        }
        lateinit var listener: TriggerEventListener
        listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                trySend(true)
                armStillnessTimeout()
                sensorManager.requestTriggerSensor(listener, sensor)
            }
        }
        send(true)
        sensorManager.requestTriggerSensor(listener, sensor)
        armStillnessTimeout()
        awaitClose {
            stillnessJob?.cancel()
            sensorManager.cancelTriggerSensor(listener, sensor)
        }
    }
}
