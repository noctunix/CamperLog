package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.FakeLocationPermissionGate
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationFix
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

/**
 * Übernahme eines GPS-Fixes ins Stationsformular: Erfolg, Abbruch und Entfernen. Robolectric,
 * weil jede Eingabeänderung den Entwurf über [androidx.lifecycle.SavedStateHandle] sichert (siehe
 * `DraftRestorationTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditStationViewModelLocationTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(provider: FakeLocationProvider, gate: FakeLocationPermissionGate) = EditStationViewModel(
        repository = FakeStationRepository(),
        tours = FakeTourRepository(),
        vehicles = FakeVehicleRepository(),
        attachments = FakeAttachmentRepository(),
        fileStore = FakeAttachmentFileStore(),
        stationId = 0,
        locationProvider = provider,
        locationPermissionGate = gate,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun successfulFix_setsCoordinatesSourceGpsAndAccuracy() = runTest(dispatcher) {
        val fix = LocationFix(68.0912, 13.1023, accuracyM = 8)
        val viewModel = viewModel(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(granted = true))

        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals(68.0912, input.latitude)
        assertEquals(13.1023, input.longitude)
        assertEquals(CoordinateSource.GPS, input.coordinateSource)
        assertEquals(8, input.accuracyM)
        // Der Controller übernimmt den Fix sofort ins Formular und setzt sich selbst zurück.
        assertEquals(LocationCaptureState.Ready, viewModel.locationCapture.state.value)
    }

    @Test
    fun servicesOff_surfacesInCaptureState() = runTest(dispatcher) {
        val viewModel = viewModel(FakeLocationProvider(locationEnabled = false), FakeLocationPermissionGate(granted = true))

        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        assertEquals(LocationCaptureState.ServicesOff, viewModel.locationCapture.state.value)
        assertNull(viewModel.uiState.value.input.latitude)
    }

    @Test
    fun timeoutWithoutLastKnown_leavesCoordinatesUnset() = runTest(dispatcher) {
        val viewModel = viewModel(FakeLocationProvider(freshFix = null, lastKnown = null), FakeLocationPermissionGate(granted = true))

        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        val state = viewModel.locationCapture.state.value as LocationCaptureState.NotFound
        assertNull(state.lastKnownOffer)
        assertNull(viewModel.uiState.value.input.latitude)
    }

    @Test
    fun onRemoveGpsCoordinates_clearsLatitudeLongitudeSourceAndAccuracy() = runTest(dispatcher) {
        val fix = LocationFix(68.0912, 13.1023, accuracyM = 8)
        val viewModel = viewModel(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(granted = true))
        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        viewModel.onRemoveGpsCoordinates()

        val input = viewModel.uiState.value.input
        assertNull(input.latitude)
        assertNull(input.longitude)
        assertNull(input.coordinateSource)
        assertNull(input.accuracyM)
    }

    @Test
    fun deniedPermission_doesNotTouchCoordinates() = runTest(dispatcher) {
        val viewModel = viewModel(FakeLocationProvider(), FakeLocationPermissionGate(granted = false, requestedBefore = true))

        viewModel.locationCapture.onButtonTapped { true }

        assertEquals(LocationCaptureState.PermissionRationale, viewModel.locationCapture.state.value)
        assertNull(viewModel.uiState.value.input.latitude)
    }
}
