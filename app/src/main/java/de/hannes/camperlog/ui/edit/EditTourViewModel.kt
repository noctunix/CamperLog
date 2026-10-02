package de.hannes.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourField
import de.hannes.camperlog.domain.TourInput
import de.hannes.camperlog.domain.TourRepository
import de.hannes.camperlog.domain.toInput
import de.hannes.camperlog.domain.toTour
import de.hannes.camperlog.domain.travelDaysBetween
import de.hannes.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Zustand des Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class EditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: TourInput = TourInput(),
    val errors: Map<TourField, String> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Der letzte Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
)

/** Lädt, validiert und speichert eine Tour. [tourId] 0 legt eine neue Tour an. */
class EditTourViewModel(private val repository: TourRepository, tourId: Long) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState(isNew = tourId == 0L, isLoading = tourId != 0L))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var original: Tour? = null
    private var showErrors = false
    private var autoTravelDays: String? = null

    init {
        if (tourId != 0L) {
            viewModelScope.launch {
                val tour = repository.observeTour(tourId).first()
                original = tour
                _uiState.update {
                    it.copy(isLoading = false, notFound = tour == null, input = tour?.toInput() ?: it.input)
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (TourInput) -> TourInput) {
        _uiState.update { state ->
            val input = transform(state.input)
            state.copy(input = input, isDirty = true, errors = if (showErrors) input.validate() else emptyMap())
        }
    }

    fun onStartDateChange(date: LocalDate) = onInputChange { prefillTravelDays(it.copy(startDate = date)) }

    fun onEndDateChange(date: LocalDate) = onInputChange { prefillTravelDays(it.copy(endDate = date)) }

    /** Validiert und speichert; bei Erfolg wird [EditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val errors = state.input.validate()
        if (errors.isNotEmpty()) {
            showErrors = true
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(state.input.toTour(original))
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Die Fehlermeldung zu [EditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    /**
     * Füllt die Reisetage aus dem Zeitraum vor, solange der Nutzer sie nicht selbst geändert hat.
     */
    private fun prefillTravelDays(input: TourInput): TourInput {
        val start = input.startDate ?: return input
        val end = input.endDate ?: return input
        if (end < start) return input
        val untouched = input.travelDays.isBlank() || input.travelDays == autoTravelDays
        if (!untouched) return input
        val days = travelDaysBetween(start, end).toString()
        autoTravelDays = days
        return input.copy(travelDays = days)
    }
}
