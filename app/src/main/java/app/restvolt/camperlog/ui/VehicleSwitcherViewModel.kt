package app.restvolt.camperlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fahrzeugliste und aktuelles Fahrzeug für den Wechsler in der oberen Leiste von Bordbuch und Fahrzeug. */
data class VehicleSwitcherUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
)

/** Hält den Fahrzeugwechsler der Reiter Bordbuch und Fahrzeug aktuell. */
class VehicleSwitcherViewModel(private val vehicles: VehicleRepository) : ViewModel() {

    val uiState: StateFlow<VehicleSwitcherUiState> =
        combine(vehicles.observeVehicles(), vehicles.observeCurrentVehicle()) { list, current ->
            VehicleSwitcherUiState(list, current.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSwitcherUiState())

    fun onSelectVehicle(id: Long) {
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }
}
