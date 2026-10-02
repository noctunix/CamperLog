package app.restvolt.camperlog.ui.rates

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.TourRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency

/** Hauptwährung, erfasste Kurse und Währungen aus Touren oder Hauptwährung, für die noch ein Kurs fehlt. */
data class RatesUiState(
    val isLoading: Boolean = true,
    val mainCurrency: Currency = EUR,
    val rates: List<ExchangeRate> = emptyList(),
    val missing: List<Currency> = emptyList(),
    /** Zuletzt gelöschter Kurs; die Oberfläche bietet dafür Rückgängig an. */
    val deleted: ExchangeRate? = null,
    /** Ein Schreibzugriff ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val writeFailed: Boolean = false,
)

/** Verwaltet die Kursliste und die Hauptwährung. */
class RatesViewModel(private val rates: ExchangeRateRepository, tours: TourRepository) : ViewModel() {

    private val events = MutableStateFlow(Events())

    val uiState: StateFlow<RatesUiState> = combine(
        rates.observeMainCurrency(),
        rates.observeRates(),
        tours.observeTotals(),
        events,
    ) { main, list, totals, events ->
        val rated = list.map { it.currency }.toSet()
        val missing = (totals.costs.map { it.currency } + main)
            .filter { it != EUR && it !in rated }
            .distinct()
            .sortedBy { it.currencyCode }
        RatesUiState(
            isLoading = false,
            mainCurrency = main,
            rates = list,
            missing = missing,
            deleted = events.deleted,
            writeFailed = events.writeFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatesUiState())

    fun setMainCurrency(currency: Currency) = write { rates.setMainCurrency(currency) }

    fun delete(rate: ExchangeRate) = write {
        rates.deleteRate(rate.currency)
        events.update { it.copy(deleted = rate) }
    }

    fun undoDelete() {
        val rate = events.value.deleted ?: return
        events.update { it.copy(deleted = null) }
        write { rates.saveRate(rate) }
    }

    fun onDeletedShown() = events.update { it.copy(deleted = null) }

    fun onWriteFailureShown() = events.update { it.copy(writeFailed = false) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: SQLException) {
                events.update { it.copy(writeFailed = true) }
            }
        }
    }

    private data class Events(val deleted: ExchangeRate? = null, val writeFailed: Boolean = false)
}
