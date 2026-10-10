package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.FakeLocationPermissionGate
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
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

/** Übernahme eines GPS-Fixes ins Reparaturformular: Erfolg und Entfernen, analog zu `EditStationViewModelLocationTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepairEditViewModelLocationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val vehicles = FakeVehicleRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(provider: FakeLocationProvider, gate: FakeLocationPermissionGate) = RepairEditViewModel(
        repository = vehicles,
        exchangeRates = FakeExchangeRateRepository(),
        vehicleId = vehicles.currentVehicleId,
        repairId = 0,
        savedStateHandle = SavedStateHandle(),
        locationProvider = provider,
        locationPermissionGate = gate,
    )

    @Test
    fun successfulFix_setsLatitudeAndLongitude() = runTest(dispatcher) {
        val fix = LocationFix(52.5219, 13.4132, accuracyM = 8)
        val viewModel = viewModel(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(granted = true))

        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals(52.5219, input.latitude)
        assertEquals(13.4132, input.longitude)
        // Der Controller übernimmt den Fix sofort ins Formular und setzt sich selbst zurück.
        assertEquals(LocationCaptureState.Ready, viewModel.locationCapture.state.value)
    }

    @Test
    fun onRemoveLocation_clearsLatitudeAndLongitude() = runTest(dispatcher) {
        val fix = LocationFix(52.5219, 13.4132, accuracyM = 8)
        val viewModel = viewModel(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(granted = true))
        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        viewModel.onRemoveLocation()

        val input = viewModel.uiState.value.input
        assertNull(input.latitude)
        assertNull(input.longitude)
    }

    @Test
    fun deniedPermission_doesNotTouchCoordinates() = runTest(dispatcher) {
        val viewModel = viewModel(FakeLocationProvider(), FakeLocationPermissionGate(granted = false, requestedBefore = true))

        viewModel.locationCapture.onButtonTapped { true }

        assertEquals(LocationCaptureState.PermissionRationale, viewModel.locationCapture.state.value)
        assertNull(viewModel.uiState.value.input.latitude)
    }
}
