package app.restvolt.camperlog.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.LevelAudioCue
import app.restvolt.camperlog.domain.LevelAudioScheme
import app.restvolt.camperlog.domain.LevelSensor
import app.restvolt.camperlog.domain.LevelStatus
import app.restvolt.camperlog.domain.Tilt
import app.restvolt.camperlog.domain.calibrated
import app.restvolt.camperlog.domain.levelAudioCue
import app.restvolt.camperlog.domain.levelStatus
import app.restvolt.camperlog.ui.settings.LevelAudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Zustand der Wasserwaage-Ansicht. [tilt] ist bereits gegen die Session-Kalibrierung ausgeglichen;
 * `null`, solange noch kein Sensorwert vorliegt oder das Gerät keinen Sensor hat ([sensorAvailable]
 * unterscheidet beide Fälle).
 */
data class LevelUiState(
    val isLoading: Boolean = true,
    val sensorAvailable: Boolean = true,
    val tilt: Tilt? = null,
    val status: LevelStatus? = null,
)

/** Hält die Session-Kalibrierung der Wasserwaage; der Nullpunkt wird nicht über die Sitzung hinaus gespeichert. */
class LevelViewModel(sensor: LevelSensor, private val audioSettings: LevelAudioSettings) : ViewModel() {

    private val rawTilt: StateFlow<RawTilt> = sensor.observeTilt()
        .map { tilt -> tilt?.let { RawTilt.Available(it) } ?: RawTilt.Unavailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RawTilt.Loading)

    private val zero = MutableStateFlow<Tilt?>(null)

    val uiState: StateFlow<LevelUiState> = combine(rawTilt, zero) { raw, zero ->
        when (raw) {
            RawTilt.Loading -> LevelUiState(isLoading = true)
            RawTilt.Unavailable -> LevelUiState(isLoading = false, sensorAvailable = false)
            is RawTilt.Available -> {
                val calibrated = zero?.let { raw.tilt.calibrated(it) } ?: raw.tilt
                LevelUiState(isLoading = false, sensorAvailable = true, tilt = calibrated, status = levelStatus(calibrated))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelUiState())

    /** Setzt den aktuellen Rohwert als neuen Nullpunkt der laufenden Sitzung. */
    fun calibrate() {
        val raw = rawTilt.value
        if (raw is RawTilt.Available) zero.value = raw.tilt
    }

    /** Ob die akustische Rückmeldung aktiv ist; persistiert über [audioSettings]. */
    val audioEnabled: StateFlow<Boolean> = audioSettings.values

    /** Schaltet die akustische Rückmeldung um und persistiert die Wahl. */
    fun setAudioEnabled(value: Boolean) {
        audioSettings.enabled = value
    }

    /** Richtungslogik der akustischen Rückmeldung; persistiert über [audioSettings]. */
    val audioScheme: StateFlow<LevelAudioScheme> = audioSettings.schemeValues

    /** Wechselt die Richtungslogik der akustischen Rückmeldung und persistiert die Wahl. */
    fun setAudioScheme(value: LevelAudioScheme) {
        audioSettings.scheme = value
    }

    /**
     * Aktuelle akustische Rückmeldung, abgeleitet aus [uiState], [audioEnabled] und [audioScheme];
     * `null` nur, wenn die Rückmeldung ausgeschaltet ist oder noch kein Sensorwert vorliegt.
     */
    val audioCue: StateFlow<LevelAudioCue?> = combine(uiState, audioEnabled, audioScheme) { state, enabled, scheme ->
        val tilt = state.tilt
        if (enabled && tilt != null) levelAudioCue(tilt, scheme = scheme) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private sealed interface RawTilt {
        data object Loading : RawTilt
        data object Unavailable : RawTilt
        data class Available(val tilt: Tilt) : RawTilt
    }
}
