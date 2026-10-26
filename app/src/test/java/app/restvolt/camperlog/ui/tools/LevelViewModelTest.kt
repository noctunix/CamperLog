package app.restvolt.camperlog.ui.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.FakeLevelSensor
import app.restvolt.camperlog.domain.LevelAudioCue
import app.restvolt.camperlog.domain.LevelAudioDirection
import app.restvolt.camperlog.domain.LevelStatus
import app.restvolt.camperlog.domain.Tilt
import app.restvolt.camperlog.ui.settings.LevelAudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LevelViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("level_audio", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.getSharedPreferences("level_audio", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] in Tests aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun viewModel(sensor: FakeLevelSensor, audioSettings: LevelAudioSettings = LevelAudioSettings(context)) =
        LevelViewModel(sensor, audioSettings)

    @Test
    fun uiState_reflectsTheSensorTiltAndStatus() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(1.0, 1.0))
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)

        assertEquals(Tilt(1.0, 1.0), viewModel.uiState.value.tilt)
        assertEquals(LevelStatus.LEVEL, viewModel.uiState.value.status)

        sensor.setTilt(Tilt(10.0, 0.0))

        assertEquals(Tilt(10.0, 0.0), viewModel.uiState.value.tilt)
        assertEquals(LevelStatus.TILTED, viewModel.uiState.value.status)
    }

    @Test
    fun calibrate_resetsTheDisplayedTiltToZeroForTheCurrentReading() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(5.0, 3.0))
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)

        viewModel.calibrate()

        assertEquals(Tilt(0.0, 0.0), viewModel.uiState.value.tilt)
        assertEquals(LevelStatus.LEVEL, viewModel.uiState.value.status)

        sensor.setTilt(Tilt(5.0, 3.0))
        assertEquals(Tilt(0.0, 0.0), viewModel.uiState.value.tilt)

        sensor.setTilt(Tilt(8.0, 3.0))
        assertEquals(Tilt(3.0, 0.0), viewModel.uiState.value.tilt)
    }

    @Test
    fun uiState_showsSensorUnavailableWhenTheDeviceHasNoSensor() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(initialTilt = null)
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)

        assertEquals(false, viewModel.uiState.value.sensorAvailable)
        assertNull(viewModel.uiState.value.tilt)
    }

    @Test
    fun audioEnabled_defaultsToOffAndIsPersistedViaSettings() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(0.0, 0.0))
        val audioSettings = LevelAudioSettings(context)
        val viewModel = viewModel(sensor, audioSettings)
        collect(viewModel.audioEnabled)

        assertFalse(viewModel.audioEnabled.value)

        viewModel.setAudioEnabled(true)

        assertEquals(true, viewModel.audioEnabled.value)
        assertEquals(true, audioSettings.enabled)
    }

    @Test
    fun audioCue_isNullWhenAudioIsDisabledEvenWhileTilted() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(10.0, 0.0))
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)
        collect(viewModel.audioCue)

        assertNull(viewModel.audioCue.value)
    }

    @Test
    fun audioCue_reflectsTiltOnceAudioIsEnabled() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(10.0, 0.0))
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)
        collect(viewModel.audioCue)

        viewModel.setAudioEnabled(true)

        val cue = viewModel.audioCue.value
        assertEquals(LevelAudioDirection.BACK, (cue as? LevelAudioCue.Beep)?.direction)

        viewModel.setAudioEnabled(false)

        assertNull(viewModel.audioCue.value)
    }

    @Test
    fun audioCue_isLevelWhenEnabledAndWithinTolerance() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(0.0, 0.0))
        val viewModel = viewModel(sensor)
        collect(viewModel.uiState)
        collect(viewModel.audioCue)

        viewModel.setAudioEnabled(true)

        assertEquals(LevelAudioCue.Level, viewModel.audioCue.value)
    }
}
