package app.restvolt.camperlog.domain

import kotlinx.coroutines.delay

/** [LocationProvider]-Fake für Tests ohne echte Standorthardware. */
class FakeLocationProvider(
    val freshFix: LocationFix? = null,
    val lastKnown: LocationFix? = null,
    val locationEnabled: Boolean = true,
    val freshFixDelayMillis: Long = 0,
) : LocationProvider {
    var lastKnownRequests = 0

    override suspend fun requestFreshFix(): LocationFix? {
        if (freshFixDelayMillis > 0) delay(freshFixDelayMillis)
        return freshFix
    }

    override suspend fun lastKnownFix(maxAgeMillis: Long): LocationFix? {
        lastKnownRequests++
        return lastKnown
    }

    override fun isLocationEnabled(): Boolean = locationEnabled
}

/** [LocationPermissionGate]-Fake für Tests; [grant] erteilt die Berechtigung nachträglich. */
class FakeLocationPermissionGate(private var granted: Boolean = true, private var requestedBefore: Boolean = false) : LocationPermissionGate {
    var markRequestedCalls = 0

    override fun hasPermission(): Boolean = granted
    override fun hasRequestedBefore(): Boolean = requestedBefore
    override fun markRequested() {
        requestedBefore = true
        markRequestedCalls++
    }

    fun grant() {
        granted = true
    }
}
