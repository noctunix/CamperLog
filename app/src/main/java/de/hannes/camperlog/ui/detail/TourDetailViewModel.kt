package de.hannes.camperlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Zustand der Detailansicht. */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val tour: Tour) : DetailUiState
}

/** Beobachtet eine einzelne Tour, damit Änderungen aus dem Formular sofort sichtbar sind. */
class TourDetailViewModel(repository: TourRepository, tourId: Long) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = repository.observeTour(tourId)
        .map { tour -> tour?.let(DetailUiState::Loaded) ?: DetailUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)
}
