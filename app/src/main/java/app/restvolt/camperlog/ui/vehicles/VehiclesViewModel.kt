package app.restvolt.camperlog.ui.vehicles

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDeleteResult
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Fahrzeugverwaltung. */
data class VehiclesUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
)

/** Rückmeldungen, die die Fahrzeugverwaltung als Snackbar oder Dialog anzeigt. */
sealed interface VehiclesMessage {
    data class Deleted(val vehicle: Vehicle) : VehiclesMessage
    data class DeleteRefused(val vehicle: Vehicle, val reason: VehicleDeleteResult) : VehiclesMessage
    data object Failed : VehiclesMessage
}

/** Liefert die Fahrzeugliste, setzt das aktuelle Fahrzeug und löscht Fahrzeuge. */
class VehiclesViewModel(private val repository: VehicleRepository) : ViewModel() {

    val uiState: StateFlow<VehiclesUiState> =
        combine(repository.observeVehicles(), repository.observeCurrentVehicle()) { list, current ->
            VehiclesUiState(list, current.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehiclesUiState())

    private val _message = MutableStateFlow<VehiclesMessage?>(null)

    /** Einmalige Rückmeldung für Snackbar oder Dialog; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<VehiclesMessage?> = _message.asStateFlow()

    fun setCurrent(id: Long) {
        viewModelScope.launch { repository.setCurrentVehicle(id) }
    }

    /** Löscht [vehicle]; bei Ablehnung (noch Touren oder letztes Fahrzeug) bleibt es erhalten. */
    fun delete(vehicle: Vehicle) {
        viewModelScope.launch {
            _message.value = try {
                when (val result = repository.delete(vehicle.id)) {
                    VehicleDeleteResult.DELETED -> VehiclesMessage.Deleted(vehicle)
                    VehicleDeleteResult.HAS_TOURS_OR_STATIONS, VehicleDeleteResult.LAST_VEHICLE -> VehiclesMessage.DeleteRefused(vehicle, result)
                }
            } catch (_: SQLException) {
                VehiclesMessage.Failed
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: VehiclesMessage) {
        _message.compareAndSet(shown, null)
    }
}
