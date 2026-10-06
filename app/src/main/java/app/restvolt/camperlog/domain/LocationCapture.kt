package app.restvolt.camperlog.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Zustand der einmaligen Standortbestimmung auf Tastendruck. */
sealed interface LocationCaptureState {

    /** Bereit für einen Tastendruck; zeigt keinen eigenen Inhalt. */
    data object Ready : LocationCaptureState

    /** Einmal abgelehnt, erneutes Fragen ist noch möglich: Begründung mit Weiter/Nicht jetzt. */
    data object PermissionRationale : LocationCaptureState

    /** Dauerhaft abgelehnt: Hinweis mit App-Einstellungen/Koordinaten eingeben/Abbrechen. */
    data object PermissionPermanentlyDenied : LocationCaptureState

    /** Standort ist am Gerät ausgeschaltet. */
    data object ServicesOff : LocationCaptureState

    /** Ein frischer Fix wird gesucht (bis zu [LOCATION_FRESH_FIX_TIMEOUT_MS]). */
    data object Searching : LocationCaptureState

    /** Ein Fix liegt vor; [isLastKnown] unterscheidet einen frischen von einem zuletzt bekannten Standort. */
    data class Found(val fix: LocationFix, val isLastKnown: Boolean) : LocationCaptureState

    /** Kein frischer Fix gefunden; [lastKnownOffer] ist gesetzt, wenn ein Rückgriff darauf möglich ist. */
    data class NotFound(val lastKnownOffer: LocationFix?) : LocationCaptureState
}

/**
 * Steuert eine einmalige Standortbestimmung; eingebettet in ein ViewModel über dessen
 * `viewModelScope`, damit eine Suche einen Konfigurationswechsel überlebt. Die Entscheidung, ob ein
 * abgelehnter Tastendruck eine Begründung oder den dauerhaft-abgelehnt-Hinweis zeigt, braucht
 * `shouldShowRequestPermissionRationale` einer Activity; das bleibt bewusst außerhalb dieser Klasse
 * und wird ihr über [onButtonTapped]/[onPermissionResult] als Parameter übergeben, statt eine
 * Activity-Referenz zu halten.
 */
class LocationCaptureController(
    private val provider: LocationProvider,
    private val permissionGate: LocationPermissionGate,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<LocationCaptureState>(LocationCaptureState.Ready)
    val state: StateFlow<LocationCaptureState> = _state.asStateFlow()

    /** Erhöht sich, wenn die Oberfläche den Systemdialog für die Standortberechtigung starten soll. */
    private val _permissionRequests = MutableStateFlow(0)
    val permissionRequests: StateFlow<Int> = _permissionRequests.asStateFlow()

    private var job: Job? = null

    /** Tastendruck auf "Aktuellen Standort verwenden" bzw. "Wo bin ich?"; [shouldShowRationale] kommt von der Oberfläche. */
    fun onButtonTapped(shouldShowRationale: () -> Boolean) {
        when {
            permissionGate.hasPermission() -> fetch()
            !permissionGate.hasRequestedBefore() -> _permissionRequests.update { it + 1 }
            shouldShowRationale() -> _state.value = LocationCaptureState.PermissionRationale
            else -> _state.value = LocationCaptureState.PermissionPermanentlyDenied
        }
    }

    /** "Weiter" in der Begründung: startet den Systemdialog erneut. */
    fun onContinueRationale() {
        _permissionRequests.update { it + 1 }
    }

    /** Ergebnis des Systemdialogs; [shouldShowRationale] kommt wieder von der Oberfläche. */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        permissionGate.markRequested()
        if (granted) {
            fetch()
        } else {
            _state.value = if (shouldShowRationale) LocationCaptureState.PermissionRationale else LocationCaptureState.PermissionPermanentlyDenied
        }
    }

    /** "Nicht jetzt"/"Abbrechen": zurück zum Ausgangszustand, ohne etwas zu suchen. */
    fun dismiss() {
        job?.cancel()
        _state.value = LocationCaptureState.Ready
    }

    /** "Erneut" nach einem Fehlschlag; die Berechtigung ist an dieser Stelle schon erteilt. */
    fun retry() = fetch()

    /** "Letzte Position verwenden": übernimmt den in [LocationCaptureState.NotFound] angebotenen Fix. */
    fun useLastKnown(fix: LocationFix) {
        _state.value = LocationCaptureState.Found(fix, isLastKnown = true)
    }

    /** Setzt den Zustand zurück, z. B. nachdem ein gefundener Fix in ein Formularfeld übernommen wurde. */
    fun clear() {
        job?.cancel()
        _state.value = LocationCaptureState.Ready
    }

    private fun fetch() {
        job?.cancel()
        if (!provider.isLocationEnabled()) {
            _state.value = LocationCaptureState.ServicesOff
            return
        }
        job = scope.launch {
            _state.value = LocationCaptureState.Searching
            val fresh = withTimeoutOrNull(LOCATION_FRESH_FIX_TIMEOUT_MS) { provider.requestFreshFix() }
            _state.value = if (fresh != null) {
                LocationCaptureState.Found(fresh, isLastKnown = false)
            } else {
                LocationCaptureState.NotFound(provider.lastKnownFix())
            }
        }
    }
}
