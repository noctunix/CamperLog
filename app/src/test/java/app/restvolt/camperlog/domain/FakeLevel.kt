package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [LevelSensor]-Fake für Tests ohne echte Neigungssensorik. */
class FakeLevelSensor(initialTilt: Tilt? = Tilt(0.0, 0.0)) : LevelSensor {
    private val state = MutableStateFlow(initialTilt)

    override fun observeTilt(): Flow<Tilt?> = state

    fun setTilt(value: Tilt?) {
        state.value = value
    }
}
