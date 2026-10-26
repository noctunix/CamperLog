package app.restvolt.camperlog.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.restvolt.camperlog.domain.LevelSensor
import app.restvolt.camperlog.domain.Tilt
import kotlin.math.atan2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** Glättungsfaktor des exponentiellen gleitenden Mittels im Beschleunigungssensor-Fallback. */
private const val ACCELEROMETER_SMOOTHING = 0.15f

/**
 * [LevelSensor] über Sensorfusion: primär `TYPE_ROTATION_VECTOR` (kombiniert Beschleunigungssensor,
 * Gyroskop und Magnetometer), ersatzweise `TYPE_ACCELEROMETER` mit einer einfachen Glättung gegen
 * Zittern, falls das Gerät den Rotationssensor nicht hat. Fehlen beide, liefert [observeTilt]
 * dauerhaft `null`.
 */
class AndroidLevelSensor(private val context: Context) : LevelSensor {

    private val sensorManager get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun observeTilt(): Flow<Tilt?> {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) return observeRotationVector(rotationVector)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) return observeAccelerometer(accelerometer)
        return flowOf(null)
    }

    private fun observeRotationVector(sensor: Sensor): Flow<Tilt?> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val pitch = Math.toDegrees(orientation[1].toDouble())
                val roll = Math.toDegrees(orientation[2].toDouble())
                trySend(Tilt(pitchDegrees = pitch, rollDegrees = roll))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun observeAccelerometer(sensor: Sensor): Flow<Tilt?> = callbackFlow {
        var smoothedX = 0f
        var smoothedY = 0f
        var smoothedZ = 0f
        var initialized = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!initialized) {
                    smoothedX = event.values[0]
                    smoothedY = event.values[1]
                    smoothedZ = event.values[2]
                    initialized = true
                } else {
                    smoothedX += ACCELEROMETER_SMOOTHING * (event.values[0] - smoothedX)
                    smoothedY += ACCELEROMETER_SMOOTHING * (event.values[1] - smoothedY)
                    smoothedZ += ACCELEROMETER_SMOOTHING * (event.values[2] - smoothedZ)
                }
                val pitch = Math.toDegrees(atan2(-smoothedY.toDouble(), smoothedZ.toDouble()))
                val roll = Math.toDegrees(atan2(smoothedX.toDouble(), smoothedZ.toDouble()))
                trySend(Tilt(pitchDegrees = pitch, rollDegrees = roll))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
