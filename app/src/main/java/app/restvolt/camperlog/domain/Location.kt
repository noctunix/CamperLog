package app.restvolt.camperlog.domain

/**
 * Einmaliger GPS-Fix. [accuracyM] fehlt, wenn das Gerät dazu keine Angabe liefert.
 * [ageMillis] ist 0 für einen frischen Fix, sonst das Alter eines zuletzt bekannten Standorts.
 */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Int?, val ageMillis: Long = 0)

/**
 * Ein Kandidat für den zuletzt bekannten Standort, auf Alter und Genauigkeit reduziert, aus denen
 * [selectBestLastKnown] auswählt. Android-frei gehalten, damit die Auswahl pur testbar bleibt.
 */
data class LocationCandidate(val ageMillis: Long, val accuracyMeters: Float)

/** Zeitbudget für einen frischen Fix, bevor auf den zuletzt bekannten Standort zurückgefallen wird. */
const val LOCATION_FRESH_FIX_TIMEOUT_MS: Long = 30_000L

/** Ein zuletzt bekannter Standort wird nur angeboten, wenn er jünger als dies ist. */
const val LOCATION_LAST_KNOWN_MAX_AGE_MS: Long = 30 * 60 * 1000L

/**
 * Ab dieser Genauigkeit gilt ein Fix als nur ungefähr (Android-12-Fall "Nur ungefährer Standort").
 * Android liefert bei nur grober Berechtigung üblicherweise ein- bis mehrstellige Kilometerwerte;
 * 500 m trennt das zuverlässig von einer echten GPS-Genauigkeit.
 */
const val LOCATION_APPROXIMATE_THRESHOLD_M: Int = 500

/** Ob [accuracyM] auf einen nur ungefähren Standort hindeutet. */
fun isApproximateFix(accuracyM: Int?): Boolean = accuracyM != null && accuracyM >= LOCATION_APPROXIMATE_THRESHOLD_M

/**
 * Wählt aus [candidates] den besten zuletzt bekannten Standort für einen expliziten Tastendruck:
 * nur Kandidaten bis [maxAgeMillis] zählen, unter ihnen gewinnt die kleinste Genauigkeitsangabe
 * (am genauesten).
 *
 * @return der Index in [candidates], oder -1, wenn keiner jung genug ist
 */
fun selectBestLastKnown(candidates: List<LocationCandidate>, maxAgeMillis: Long = LOCATION_LAST_KNOWN_MAX_AGE_MS): Int {
    var bestIndex = -1
    var bestAccuracy = Float.MAX_VALUE
    candidates.forEachIndexed { index, candidate ->
        if (candidate.ageMillis <= maxAgeMillis && candidate.accuracyMeters < bestAccuracy) {
            bestIndex = index
            bestAccuracy = candidate.accuracyMeters
        }
    }
    return bestIndex
}

/**
 * Zugriff auf die Standorthardware des Geräts. Die Android-Implementierung nutzt
 * `LocationManagerCompat`, keine Play-Services; siehe [app.restvolt.camperlog.data.AndroidLocationProvider].
 */
interface LocationProvider {

    /** Fragt GPS, dann Netzwerk-Provider nach einem frischen Fix; `null`, wenn keiner antwortet. */
    suspend fun requestFreshFix(): LocationFix?

    /** Liefert den besten zuletzt bekannten Standort nach [selectBestLastKnown], oder `null`. */
    suspend fun lastKnownFix(maxAgeMillis: Long = LOCATION_LAST_KNOWN_MAX_AGE_MS): LocationFix?

    /** Ob am Gerät überhaupt ein Standortanbieter aktiv ist (`LocationManagerCompat.isLocationEnabled`). */
    fun isLocationEnabled(): Boolean
}

/**
 * Testbarer Zugriff auf die Standortberechtigung. Bewusst ohne `shouldShowRequestPermissionRationale`,
 * das eine Activity braucht und daher in der Oberfläche bleibt statt in einem ViewModel zu landen.
 */
interface LocationPermissionGate {

    /** Ob mindestens eine der beiden Standortberechtigungen (fein oder grob) erteilt ist. */
    fun hasPermission(): Boolean

    /** Ob die App die Berechtigung schon einmal angefragt hat (unterscheidet "noch nie gefragt" von "dauerhaft abgelehnt"). */
    fun hasRequestedBefore(): Boolean

    /** Vermerkt, dass die Berechtigung angefragt wurde. */
    fun markRequested()
}
