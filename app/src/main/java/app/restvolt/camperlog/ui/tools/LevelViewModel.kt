package app.restvolt.camperlog.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.LevelSensor
import app.restvolt.camperlog.domain.LevelStatus
import app.restvolt.camperlog.domain.Tilt
import app.restvolt.camperlog.domain.calibrated
import app.restvolt.camperlog.domain.levelStatus
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
class LevelViewModel(sensor: LevelSensor) : ViewModel() {

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

    private sealed interface RawTilt {
        data object Loading : RawTilt
        data object Unavailable : RawTilt
        data class Available(val tilt: Tilt) : RawTilt
    }
}
