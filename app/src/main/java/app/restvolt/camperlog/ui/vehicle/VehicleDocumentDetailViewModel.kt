package app.restvolt.camperlog.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Dokumentdetailseite. */
sealed interface VehicleDocumentDetailUiState {
    data object Loading : VehicleDocumentDetailUiState
    data object NotFound : VehicleDocumentDetailUiState
    data class Loaded(val document: VehicleDocument) : VehicleDocumentDetailUiState
}

/** Zeigt ein einzelnes Fahrzeugdokument und löscht es bei Bedarf. */
class VehicleDocumentDetailViewModel(
    private val repository: VehicleDocumentRepository,
    vehicleId: Long,
    documentId: Long,
) : ViewModel() {

    val uiState: StateFlow<VehicleDocumentDetailUiState> = repository.observeForVehicle(vehicleId)
        .map { list -> list.firstOrNull { it.id == documentId } }
        .map { document -> if (document == null) VehicleDocumentDetailUiState.NotFound else VehicleDocumentDetailUiState.Loaded(document) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDocumentDetailUiState.Loading)

    fun delete(document: VehicleDocument) {
        viewModelScope.launch { repository.delete(document.id) }
    }
}
