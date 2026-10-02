package app.restvolt.camperlog.ui.rates

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.parseRate
import app.restvolt.camperlog.domain.rateToInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Fehlerhafte Eingaben im Kursformular. */
enum class RateError { CURRENCY_REQUIRED, RATE_INVALID }

/** Zustand des Kursformulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class RateEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val currency: Currency? = null,
    val rate: String = "",
    val date: LocalDate = LocalDate.MIN,
    val source: String = "",
    /** Währungen mit bereits erfasstem Kurs sowie EUR; stehen bei neuen Kursen nicht zur Wahl. */
    val unavailable: Set<Currency> = setOf(EUR),
    val errors: Set<RateError> = emptySet(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
)

/**
 * Bearbeitet den Kurs für [currencyCode]. Ohne Code oder ohne erfassten Kurs wird ein neuer angelegt,
 * mit der Währung aus [currencyCode] vorbelegt und dem Stand [today].
 */
class RateEditViewModel(
    private val repository: ExchangeRateRepository,
    currencyCode: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateEditUiState())
    val uiState: StateFlow<RateEditUiState> = _uiState.asStateFlow()

    private var showErrors = false

    init {
        val preset = currencyCode?.let(Currency::getInstance)?.takeIf { it != EUR }
        viewModelScope.launch {
            val rates = repository.observeRates().first()
            val existing = rates.firstOrNull { it.currency == preset }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNew = existing == null,
                    currency = preset,
                    rate = existing?.let { rate -> rateToInput(rate.perEuro, locale()) }.orEmpty(),
                    date = existing?.date ?: today(),
                    source = existing?.source.orEmpty(),
                    unavailable = rates.map(ExchangeRate::currency).toSet() + EUR,
                )
            }
        }
    }

    fun onCurrencyChange(currency: Currency) = edit { if (it.isNew) it.copy(currency = currency) else it }

    fun onRateChange(rate: String) = edit { it.copy(rate = rate) }

    fun onDateChange(date: LocalDate) = edit { it.copy(date = date) }

    fun onSourceChange(source: String) = edit { it.copy(source = source) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSaved) return
        showErrors = true
        val errors = validate(state)
        val perEuro = parseRate(state.rate, locale())
        val currency = state.currency
        if (errors.isNotEmpty() || perEuro == null || currency == null) {
            _uiState.update { it.copy(errors = errors, rejectedSaves = it.rejectedSaves + 1) }
            return
        }
        _uiState.update { it.copy(errors = emptySet(), isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveRate(ExchangeRate(currency, perEuro, state.date, state.source.trim()))
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun onSaveFailureShown() = _uiState.update { it.copy(saveFailed = false) }

    private fun edit(change: (RateEditUiState) -> RateEditUiState) = _uiState.update {
        val next = change(it)
        if (next == it) it else next.copy(isDirty = true, errors = if (showErrors) validate(next) else emptySet())
    }

    private fun validate(state: RateEditUiState): Set<RateError> = buildSet {
        if (state.currency == null) add(RateError.CURRENCY_REQUIRED)
        if (parseRate(state.rate, locale()) == null) add(RateError.RATE_INVALID)
    }
}
