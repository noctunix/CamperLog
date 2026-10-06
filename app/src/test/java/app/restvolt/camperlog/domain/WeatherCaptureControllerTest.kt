package app.restvolt.camperlog.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Zustandsautomat der einmaligen Wetterabfrage (6.8), mit einem Fake statt Netzwerk. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherCaptureControllerTest {

    private val snapshot = WeatherSnapshot(
        temperatureDeciC = 143,
        weatherCode = 1,
        windKmh = 18,
        gustKmh = 35,
        windDirectionDeg = 270,
        observedAt = Instant.EPOCH,
    )

    @Test
    fun fetch_success_reachesSuccessWithTheSnapshot() = runTest {
        val controller = WeatherCaptureController(FakeWeatherProvider(WeatherResult.Success(snapshot)), this)

        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        val state = controller.state.value as WeatherCaptureState.Success
        assertEquals(snapshot, state.snapshot)
    }

    @Test
    fun fetch_offline_showsOffline() = runTest {
        val controller = WeatherCaptureController(FakeWeatherProvider(WeatherResult.Offline), this)

        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        assertEquals(WeatherCaptureState.Offline, controller.state.value)
    }

    @Test
    fun fetch_rateLimited_showsServiceUnavailable() = runTest {
        val controller = WeatherCaptureController(FakeWeatherProvider(WeatherResult.RateLimited), this)

        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        assertEquals(WeatherCaptureState.ServiceUnavailable, controller.state.value)
    }

    @Test
    fun fetch_genericError_showsServiceUnavailable() = runTest {
        val controller = WeatherCaptureController(FakeWeatherProvider(WeatherResult.Error), this)

        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        assertEquals(WeatherCaptureState.ServiceUnavailable, controller.state.value)
    }

    @Test
    fun fetch_passesTheGivenCoordinatesToTheProvider() = runTest {
        val provider = FakeWeatherProvider(WeatherResult.Success(snapshot))
        val controller = WeatherCaptureController(provider, this)

        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        assertEquals(listOf(68.0912 to 13.1023), provider.requests)
    }

    @Test
    fun reset_returnsToReady() = runTest {
        val controller = WeatherCaptureController(FakeWeatherProvider(WeatherResult.Offline), this)
        controller.fetch(68.0912, 13.1023)
        advanceUntilIdle()

        controller.reset()

        assertEquals(WeatherCaptureState.Ready, controller.state.value)
    }
}
