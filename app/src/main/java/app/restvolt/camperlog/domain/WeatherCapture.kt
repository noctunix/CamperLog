package app.restvolt.camperlog.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Zustand der einmaligen Wetterabfrage auf Tastendruck (6.8). */
sealed interface WeatherCaptureState {

    /** Bereit für einen Tastendruck: "Wetter abrufen". */
    data object Ready : WeatherCaptureState

    /** Die Abfrage läuft. */
    data object Loading : WeatherCaptureState

    /** Ein Schnappschuss liegt vor. */
    data class Success(val snapshot: WeatherSnapshot) : WeatherCaptureState

    /** Keine Internetverbindung (6.8: `UnknownHostException`, ohne `ACCESS_NETWORK_STATE`). */
    data object Offline : WeatherCaptureState

    /** Dienst nicht erreichbar, auch bei HTTP 429 ([WeatherResult.RateLimited]); 6.8 zeigt denselben Text. */
    data object ServiceUnavailable : WeatherCaptureState
}

/**
 * Steuert eine einmalige Wetterabfrage (6.8); eingebettet in ein ViewModel über dessen
 * `viewModelScope`, damit eine laufende Abfrage einen Konfigurationswechsel überlebt.
 */
class WeatherCaptureController(private val provider: WeatherProvider, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<WeatherCaptureState>(WeatherCaptureState.Ready)
    val state: StateFlow<WeatherCaptureState> = _state.asStateFlow()

    private var job: Job? = null

    /** Tastendruck auf "Wetter abrufen" bzw. "Erneut" nach einem Fehlschlag. */
    fun fetch(latitude: Double, longitude: Double) {
        job?.cancel()
        job = scope.launch {
            _state.value = WeatherCaptureState.Loading
            _state.value = when (val result = provider.fetchCurrent(latitude, longitude)) {
                is WeatherResult.Success -> WeatherCaptureState.Success(result.snapshot)
                WeatherResult.Offline -> WeatherCaptureState.Offline
                WeatherResult.RateLimited, WeatherResult.Error -> WeatherCaptureState.ServiceUnavailable
            }
        }
    }

    /** Setzt den Zustand zurück, z. B. wenn die Station neu geladen wird oder die Koordinaten wegfallen. */
    fun reset() {
        job?.cancel()
        _state.value = WeatherCaptureState.Ready
    }
}
