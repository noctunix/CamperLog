package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.CostInput
import app.restvolt.camperlog.domain.QUICK_CURRENCIES
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourInput
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.costErrors
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toTour
import app.restvolt.camperlog.domain.travelDaysBetween
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Zustand des Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class EditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: TourInput = TourInput(),
    val errors: Map<TourField, TourError> = emptyMap(),
    /** Fehler je Kostenzeile, Schlüssel ist der Index in [TourInput.costs]. */
    val costErrors: Map<Int, TourError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Der letzte Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
)

/**
 * Lädt, validiert und speichert eine Tour. [tourId] 0 legt eine neue Tour an.
 *
 * @param locale liefert die aktuelle Sprache für Beträge; wird bei jedem Zugriff neu gelesen,
 *   damit ein Sprachwechsel bei laufendem ViewModel greift
 */
class EditTourViewModel(
    private val repository: TourRepository,
    tourId: Long,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState(isNew = tourId == 0L, isLoading = tourId != 0L))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var original: Tour? = null
    private var showErrors = false
    private var autoTravelDays: String? = null

    init {
        if (tourId == 0L) {
            viewModelScope.launch {
                val currency = repository.lastUsedCurrency() ?: return@launch
                // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                _uiState.update {
                    if (it.isDirty) it else it.copy(input = it.input.copy(costs = listOf(CostInput(currency = currency))))
                }
            }
        } else {
            viewModelScope.launch {
                val tour = repository.observeTour(tourId).first()
                original = tour
                _uiState.update {
                    it.copy(isLoading = false, notFound = tour == null, input = tour?.toInput(locale()) ?: it.input)
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (TourInput) -> TourInput) {
        _uiState.update { state ->
            val input = transform(state.input)
            state.copy(
                input = input,
                isDirty = true,
                errors = if (showErrors) input.validate(locale()) else emptyMap(),
                costErrors = if (showErrors) input.costErrors(locale()) else emptyMap(),
            )
        }
    }

    fun onCostAmountChange(index: Int, amount: String) = onCostChange(index) { it.copy(amount = amount) }

    fun onCostCurrencyChange(index: Int, currency: Currency) = onCostChange(index) { it.copy(currency = currency) }

    /** Hängt eine Kostenzeile mit der ersten noch freien Währung an, bevorzugt aus [QUICK_CURRENCIES]. */
    fun onAddCost() = onInputChange { input ->
        val used = input.costs.map(CostInput::currency).toSet()
        val next = (QUICK_CURRENCIES + ALL_CURRENCIES).firstOrNull { it !in used } ?: return@onInputChange input
        input.copy(costs = input.costs + CostInput(currency = next))
    }

    /** Entfernt eine Kostenzeile; die letzte bleibt stehen und wird nur geleert. */
    fun onRemoveCost(index: Int) = onInputChange { input ->
        val costs = input.costs.filterIndexed { i, _ -> i != index }
        input.copy(costs = costs.ifEmpty { listOf(CostInput(currency = input.costs[index].currency)) })
    }

    private fun onCostChange(index: Int, transform: (CostInput) -> CostInput) = onInputChange { input ->
        input.copy(costs = input.costs.mapIndexed { i, cost -> if (i == index) transform(cost) else cost })
    }

    fun onStartDateChange(date: LocalDate) = onInputChange { prefillTravelDays(it.copy(startDate = date)) }

    fun onEndDateChange(date: LocalDate) = onInputChange { prefillTravelDays(it.copy(endDate = date)) }

    /** Validiert und speichert; bei Erfolg wird [EditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val locale = locale()
        val errors = state.input.validate(locale)
        if (errors.isNotEmpty()) {
            showErrors = true
            _uiState.update {
                it.copy(errors = errors, costErrors = state.input.costErrors(locale), rejectedSaves = it.rejectedSaves + 1)
            }
            return
        }
        val tour = state.input.toTour(original, locale)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), costErrors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(tour)
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
