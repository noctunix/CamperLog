package app.restvolt.camperlog.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import app.restvolt.camperlog.domain.LocationCandidate
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.selectBestLastKnown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * [LocationProvider] über `LocationManagerCompat`, ohne Play-Services: fragt GPS, dann den
 * Netzwerk-Provider, und fällt bei einem zuletzt bekannten Standort auf [selectBestLastKnown] zurück.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val locationManager get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(locationManager)

    @SuppressLint("MissingPermission")
    override suspend fun requestFreshFix(): LocationFix? = withContext(Dispatchers.IO) {
        val manager = locationManager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!manager.allProviders.contains(provider) || !manager.isProviderEnabled(provider)) continue
            try {
                requestFromProvider(manager, provider)?.let { return@withContext it.toLocationFix() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nächster Provider versuchen.
            }
        }
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFromProvider(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, { it.run() }) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownFix(maxAgeMillis: Long): LocationFix? = withContext(Dispatchers.IO) {
        val manager = locationManager
        val now = SystemClock.elapsedRealtimeNanos()
        val locations = manager.allProviders.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        if (locations.isEmpty()) return@withContext null
        fun ageMillisOf(location: Location) = (now - location.elapsedRealtimeNanos) / 1_000_000L
        val candidates = locations.map { location ->
            LocationCandidate(ageMillisOf(location), if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE)
        }
        val bestIndex = selectBestLastKnown(candidates, maxAgeMillis)
        if (bestIndex < 0) null else locations[bestIndex].toLocationFix(ageMillis = ageMillisOf(locations[bestIndex]))
    }

    private fun Location.toLocationFix(ageMillis: Long = 0): LocationFix =
        LocationFix(latitude, longitude, if (hasAccuracy()) accuracy.roundToInt() else null, ageMillis)
}
