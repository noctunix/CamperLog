package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [MotionDetector]-Fake für Tests ohne echte Bewegungssensorik. */
class FakeMotionDetector(initiallyMoving: Boolean = true) : MotionDetector {
    private val state = MutableStateFlow(initiallyMoving)

    override fun observeIsMoving(): Flow<Boolean> = state

    fun setMoving(value: Boolean) {
        state.value = value
    }
}
