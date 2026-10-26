package app.restvolt.camperlog.ui.tools

import app.restvolt.camperlog.domain.FakeLevelSensor
import app.restvolt.camperlog.domain.LevelStatus
import app.restvolt.camperlog.domain.Tilt
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LevelViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Startet die Sammlung des `WhileSubscribed`-Zustands, damit [StateFlow.value] in Tests aktuell ist. */
    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    @Test
    fun uiState_reflectsTheSensorTiltAndStatus() = runTest(dispatcher) {
        val sensor = FakeLevelSensor(Tilt(1.0, 1.0))
        val viewModel = LevelViewModel(sensor)
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
        val viewModel = LevelViewModel(sensor)
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
        val viewModel = LevelViewModel(sensor)
        collect(viewModel.uiState)

        assertEquals(false, viewModel.uiState.value.sensorAvailable)
        assertNull(viewModel.uiState.value.tilt)
    }
}
