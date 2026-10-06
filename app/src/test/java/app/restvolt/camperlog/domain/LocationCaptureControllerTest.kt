package app.restvolt.camperlog.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zustandsautomat der einmaligen Standortbestimmung (6.7), mit Fakes statt echter Standorthardware. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationCaptureControllerTest {

    private val fix = LocationFix(68.0912, 13.1023, accuracyM = 8)

    @Test
    fun onButtonTapped_withPermission_fetchesDirectlyAndSucceeds() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(granted = true), this)

        controller.onButtonTapped { false }
        advanceUntilIdle()

        val state = controller.state.value as LocationCaptureState.Found
        assertEquals(fix, state.fix)
        assertEquals(false, state.isLastKnown)
    }

    @Test
    fun onButtonTapped_firstEverTap_requestsTheSystemDialogInsteadOfShowingADialog() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = false)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)

        controller.onButtonTapped { true }

        assertEquals(1, controller.permissionRequests.value)
        assertEquals(LocationCaptureState.Ready, controller.state.value)
    }

    @Test
    fun onButtonTapped_deniedBeforeWithRationaleAllowed_showsRationale() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = true)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)

        controller.onButtonTapped { true }

        assertEquals(LocationCaptureState.PermissionRationale, controller.state.value)
    }

    @Test
    fun onButtonTapped_permanentlyDenied_showsThePermanentlyDeniedState() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = true)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)

        controller.onButtonTapped { false }

        assertEquals(LocationCaptureState.PermissionPermanentlyDenied, controller.state.value)
    }

    @Test
    fun onContinueRationale_requestsTheSystemDialogAgain() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = true)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)
        controller.onButtonTapped { true }

        controller.onContinueRationale()

        assertEquals(1, controller.permissionRequests.value)
    }

    @Test
    fun onPermissionResult_granted_fetchesAndMarksRequested() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = false)
        val provider = FakeLocationProvider(freshFix = fix)
        val controller = LocationCaptureController(provider, gate, this)

        gate.grant()
        controller.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        assertEquals(1, gate.markRequestedCalls)
        assertTrue(controller.state.value is LocationCaptureState.Found)
    }

    @Test
    fun onPermissionResult_deniedWithRationale_showsRationale() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = false)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)

        controller.onPermissionResult(granted = false, shouldShowRationale = true)

        assertEquals(LocationCaptureState.PermissionRationale, controller.state.value)
        assertEquals(1, gate.markRequestedCalls)
    }

    @Test
    fun onPermissionResult_deniedWithoutRationale_showsPermanentlyDenied() = runTest {
        val gate = FakeLocationPermissionGate(granted = false, requestedBefore = false)
        val controller = LocationCaptureController(FakeLocationProvider(), gate, this)

        controller.onPermissionResult(granted = false, shouldShowRationale = false)

        assertEquals(LocationCaptureState.PermissionPermanentlyDenied, controller.state.value)
    }

    @Test
    fun fetch_withLocationServicesOff_showsServicesOffWithoutSearching() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(locationEnabled = false), FakeLocationPermissionGate(), this)

        controller.onButtonTapped { false }

        assertEquals(LocationCaptureState.ServicesOff, controller.state.value)
    }

    @Test
    fun fetch_noFreshFixAndNoLastKnown_showsNotFoundWithoutOffer() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(freshFix = null, lastKnown = null), FakeLocationPermissionGate(), this)

        controller.onButtonTapped { false }
        advanceUntilIdle()

        val state = controller.state.value as LocationCaptureState.NotFound
        assertNull(state.lastKnownOffer)
    }

    @Test
    fun fetch_timesOutAndOffersALastKnownFix() = runTest {
        val lastKnown = fix.copy(ageMillis = 12 * 60 * 1000L)
        val provider = FakeLocationProvider(freshFix = fix, lastKnown = lastKnown, freshFixDelayMillis = LOCATION_FRESH_FIX_TIMEOUT_MS + 5_000)
        val controller = LocationCaptureController(provider, FakeLocationPermissionGate(), this)

        controller.onButtonTapped { false }
        advanceUntilIdle()

        val state = controller.state.value as LocationCaptureState.NotFound
        assertEquals(lastKnown, state.lastKnownOffer)
    }

    @Test
    fun useLastKnown_appliesTheOfferedFixAsFound() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(), FakeLocationPermissionGate(), this)

        controller.useLastKnown(fix)

        val state = controller.state.value as LocationCaptureState.Found
        assertEquals(fix, state.fix)
        assertTrue(state.isLastKnown)
    }

    @Test
    fun retry_afterNotFound_fetchesAgain() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(), this)
        controller.useLastKnown(fix.copy(ageMillis = 1))

        controller.retry()
        advanceUntilIdle()

        assertTrue(controller.state.value is LocationCaptureState.Found)
    }

    @Test
    fun dismiss_returnsToReady() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(), FakeLocationPermissionGate(granted = false, requestedBefore = true), this)
        controller.onButtonTapped { true }

        controller.dismiss()

        assertEquals(LocationCaptureState.Ready, controller.state.value)
    }

    @Test
    fun clear_afterFound_returnsToReady() = runTest {
        val controller = LocationCaptureController(FakeLocationProvider(freshFix = fix), FakeLocationPermissionGate(), this)
        controller.onButtonTapped { false }
        advanceUntilIdle()

        controller.clear()

        assertEquals(LocationCaptureState.Ready, controller.state.value)
    }
}
