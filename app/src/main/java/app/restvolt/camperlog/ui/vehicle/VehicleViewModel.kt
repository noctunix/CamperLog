package app.restvolt.camperlog.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand des Fahrzeug-Reiters: Fahrzeugliste und -wechsler sowie das vollständige aktuelle Fahrzeug. */
data class VehicleUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
    val currentVehicle: Vehicle? = null,
)

/** Hält das Datenblatt des Fahrzeug-Reiters und seinen Fahrzeugwechsler aktuell. */
class VehicleViewModel(private val vehicles: VehicleRepository) : ViewModel() {

    val uiState: StateFlow<VehicleUiState> =
        combine(vehicles.observeVehicles(), vehicles.observeCurrentVehicle()) { list, current ->
            VehicleUiState(list, current.id, current)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())

    fun onSelectVehicle(id: Long) {
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }
}
