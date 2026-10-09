package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.FakeWeatherProvider
import app.restvolt.camperlog.domain.WeatherCaptureState
import app.restvolt.camperlog.domain.WeatherResult
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Übernahme einer Wetterabfrage ins Stationsformular: Erfolg, Fehlschläge und Entfernen.
 * Robolectric, weil jede Eingabeänderung den Entwurf über [androidx.lifecycle.SavedStateHandle] sichert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditStationViewModelWeatherTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val snapshot = WeatherSnapshot(
        temperatureDeciC = 143,
        weatherCode = 1,
        windKmh = 18,
        gustKmh = 35,
        windDirectionDeg = 270,
        observedAt = Instant.EPOCH,
    )

    private fun viewModel(provider: FakeWeatherProvider, date: LocalDate = LocalDate.now()) = EditStationViewModel(
        repository = FakeStationRepository(),
        tours = FakeTourRepository(),
        vehicles = FakeVehicleRepository(),
        attachments = FakeAttachmentRepository(),
        fileStore = FakeAttachmentFileStore(),
        stationId = 0,
        weatherProvider = provider,
        savedStateHandle = SavedStateHandle(),
    ).also { vm ->
        vm.onInputChange { it.copy(date = date, latitude = 68.0912, longitude = 13.1023, coordinateSource = CoordinateSource.ENTERED) }
    }

    @Test
    fun onFetchWeather_success_setsTheSnapshotOnTheInputAndResetsTheController() = runTest(dispatcher) {
        val viewModel = viewModel(FakeWeatherProvider(WeatherResult.Success(snapshot)))

        viewModel.onFetchWeather()
        advanceUntilIdle()

        assertEquals(snapshot, viewModel.uiState.value.input.weather)
        assertEquals(WeatherCaptureState.Ready, viewModel.weatherCapture.state.value)
    }

    @Test
    fun onFetchWeather_passesTheInputCoordinates() = runTest(dispatcher) {
        val provider = FakeWeatherProvider(WeatherResult.Success(snapshot))
        val viewModel = viewModel(provider)

        viewModel.onFetchWeather()
        advanceUntilIdle()

        assertEquals(listOf(68.0912 to 13.1023), provider.requests)
    }

    @Test
    fun onFetchWeather_withoutCoordinates_doesNothing() = runTest(dispatcher) {
        val provider = FakeWeatherProvider(WeatherResult.Success(snapshot))
        val viewModel = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = FakeVehicleRepository(),
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            weatherProvider = provider,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.onFetchWeather()
        advanceUntilIdle()

        assertEquals(0, provider.requests.size)
        assertNull(viewModel.uiState.value.input.weather)
    }

    @Test
    fun onFetchWeather_offline_surfacesInCaptureStateWithoutTouchingTheInput() = runTest(dispatcher) {
        val viewModel = viewModel(FakeWeatherProvider(WeatherResult.Offline))

        viewModel.onFetchWeather()
        advanceUntilIdle()

        assertEquals(WeatherCaptureState.Offline, viewModel.weatherCapture.state.value)
        assertNull(viewModel.uiState.value.input.weather)
    }

    @Test
    fun onFetchWeather_rateLimited_showsServiceUnavailable() = runTest(dispatcher) {
        val viewModel = viewModel(FakeWeatherProvider(WeatherResult.RateLimited))

        viewModel.onFetchWeather()
        advanceUntilIdle()

        assertEquals(WeatherCaptureState.ServiceUnavailable, viewModel.weatherCapture.state.value)
    }

    @Test
    fun onRemoveWeather_clearsTheSnapshot() = runTest(dispatcher) {
        val viewModel = viewModel(FakeWeatherProvider(WeatherResult.Success(snapshot)))
        viewModel.onFetchWeather()
        advanceUntilIdle()

        viewModel.onRemoveWeather()

        assertNull(viewModel.uiState.value.input.weather)
    }

    @Test
    fun onFetchTemperature_success_fillsOnlyTheTemperatureFieldAsWholeDegrees() = runTest(dispatcher) {
        val viewModel = viewModel(FakeWeatherProvider(WeatherResult.Success(snapshot)))

        viewModel.onFetchTemperature()
        advanceUntilIdle()

        assertEquals("14", viewModel.uiState.value.input.manualTemperatureC)
        assertNull(viewModel.uiState.value.input.weather)
        assertEquals(WeatherCaptureState.Ready, viewModel.temperatureCapture.state.value)
    }

    @Test
    fun onFetchTemperature_passesTheInputCoordinates() = runTest(dispatcher) {
        val provider = FakeWeatherProvider(WeatherResult.Success(snapshot))
        val viewModel = viewModel(provider)

        viewModel.onFetchTemperature()
        advanceUntilIdle()

        assertEquals(listOf(68.0912 to 13.1023), provider.requests)
    }

    @Test
    fun onFetchTemperature_withoutCoordinates_doesNothing() = runTest(dispatcher) {
        val provider = FakeWeatherProvider(WeatherResult.Success(snapshot))
        val viewModel = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = FakeVehicleRepository(),
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            weatherProvider = provider,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.onFetchTemperature()
        advanceUntilIdle()

        assertEquals(0, provider.requests.size)
        assertEquals("", viewModel.uiState.value.input.manualTemperatureC)
    }
}
