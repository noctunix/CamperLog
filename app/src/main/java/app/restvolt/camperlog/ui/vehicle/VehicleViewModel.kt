package app.restvolt.camperlog.ui.vehicle

import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zustand des Fahrzeug-Reiters: Fahrzeugliste und -wechsler, das aktuelle Fahrzeug, seine Reparaturen
 * und seine Dokumente (für ablaufende Dokumente als zusätzliche Erinnerungskarten, siehe [VehicleScreen]).
 */
data class VehicleUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehicleId: Long = 0,
    val currentVehicle: Vehicle? = null,
    val repairs: List<Repair> = emptyList(),
    val documents: List<VehicleDocument> = emptyList(),
    /** Checklisten des Fahrzeugs ohne Tourbezug (z. B. Einwintern), für die Checklisten-Karte. */
    val checklistsWithoutTour: List<Checklist> = emptyList(),
)

/** Rückmeldungen, die der Fahrzeug-Reiter als Snackbar anzeigt. */
sealed interface VehicleMessage {
    data class RepairDeleted(val repair: Repair) : VehicleMessage
    data class Failed(@StringRes val text: Int) : VehicleMessage
}

/** Hält das Datenblatt des Fahrzeug-Reiters und seinen Fahrzeugwechsler aktuell; löscht Reparaturen. */
class VehicleViewModel(
    private val vehicles: VehicleRepository,
    private val documents: VehicleDocumentRepository,
    private val checklists: ChecklistRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VehicleUiState> =
        combine(vehicles.observeVehicles(), vehicles.observeCurrentVehicle()) { list, current -> list to current }
            .flatMapLatest { (list, current) ->
                combine(
                    vehicles.observeRepairs(current.id),
                    documents.observeForVehicle(current.id),
                    checklists.observeForVehicleWithoutTour(current.id),
                ) { repairs, documents, checklistList ->
                    VehicleUiState(list, current.id, current, repairs, documents, checklistList)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())

    fun onSelectVehicle(id: Long) {
        viewModelScope.launch { vehicles.setCurrentVehicle(id) }
    }

    private val _message = MutableStateFlow<VehicleMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar des Reiters; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<VehicleMessage?> = _message.asStateFlow()

    /** Löscht [repair] und bietet über [VehicleMessage.RepairDeleted] das Rückgängigmachen an. */
    fun deleteRepair(repair: Repair) {
        viewModelScope.launch {
            _message.value = try {
                vehicles.deleteRepair(repair.id)
                VehicleMessage.RepairDeleted(repair)
            } catch (_: SQLException) {
                VehicleMessage.Failed(R.string.vehicle_repair_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteRepair] entfernte Reparatur unverändert wieder her. */
    fun undoDeleteRepair(repair: Repair) {
        viewModelScope.launch {
            try {
                vehicles.restoreRepair(repair)
            } catch (_: SQLException) {
                _message.value = VehicleMessage.Failed(R.string.vehicle_repair_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: VehicleMessage) {
        _message.compareAndSet(shown, null)
    }
}
