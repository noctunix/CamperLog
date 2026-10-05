package app.restvolt.camperlog.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Conversion
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.convert
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Currency

/**
 * Kennzahlen mit Umrechnung der Kosten in die Hauptwährung. [conversion] ist `null`, wenn alle Kosten
 * bereits in der Hauptwährung vorliegen und eine Umrechnung nichts Neues zeigen würde.
 */
data class TotalsRow(val totals: TourTotals, val conversion: Conversion?)

/** Gesamt- und Jahreskennzahlen; [years] ist absteigend nach Jahr sortiert. */
data class OverviewUiState(
    val isLoading: Boolean = true,
    val total: TotalsRow? = null,
    val years: List<Pair<Int, TotalsRow>> = emptyList(),
)

/**
 * Stellt die per SQL aggregierten Kennzahlen samt Umrechnung in die Hauptwährung bereit.
 * [vehicleId] beschränkt sie auf ein Fahrzeug; `null` zeigt die Kennzahlen aller Fahrzeuge.
 */
class OverviewViewModel(
    repository: TourRepository,
    exchangeRates: ExchangeRateRepository,
    vehicleId: Long? = null,
) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = combine(
        repository.observeTotals(vehicleId),
        repository.observeYearTotals(vehicleId),
        exchangeRates.observeMainCurrency(),
        exchangeRates.observeRates(),
    ) { total, years, main, rates ->
        OverviewUiState(
            isLoading = false,
            total = row(total, main, rates),
            years = years.map { it.year to row(it.totals, main, rates) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun row(totals: TourTotals, main: Currency, rates: List<ExchangeRate>) = TotalsRow(
        totals = totals,
        conversion = if (totals.costs.all { it.currency == main }) null else convert(totals.costs, main, rates),
    )
}
