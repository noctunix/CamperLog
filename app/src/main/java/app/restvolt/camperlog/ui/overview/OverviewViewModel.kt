package app.restvolt.camperlog.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Conversion
import app.restvolt.camperlog.domain.CountryLookupRepository
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.convert
import app.restvolt.camperlog.domain.totalCountries
import app.restvolt.camperlog.domain.tourCountriesByYear
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Currency

/**
 * Kennzahlen mit Umrechnung der Kosten in die Hauptwährung. [conversion] ist `null`, wenn alle Kosten
 * bereits in der Hauptwährung vorliegen und eine Umrechnung nichts Neues zeigen würde.
 */
data class TotalsRow(val totals: TourTotals, val conversion: Conversion?, val countries: Set<String> = emptySet())

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
    stations: StationRepository,
    countryLookup: CountryLookupRepository,
    vehicleId: Long? = null,
) : ViewModel() {

    /** Länder je Jahr der Startdaten aller Touren von [vehicleId] (alle Fahrzeuge, wenn `null`); siehe [tourCountriesByYear]. */
    private val countriesByYear = combine(repository.observeTours(), stations.observeForVehicle(vehicleId)) { tours, allStations ->
        val stationsByTourId = allStations.mapNotNull { station -> station.tourId?.let { it to station } }.groupBy({ it.first }, { it.second })
        tours.filter { vehicleId == null || it.vehicleId == vehicleId } to stationsByTourId
    }.map { (tours, stationsByTourId) -> tourCountriesByYear(tours, stationsByTourId, countryLookup) }

    val uiState: StateFlow<OverviewUiState> = combine(
        repository.observeTotals(vehicleId),
        repository.observeYearTotals(vehicleId),
        exchangeRates.observeMainCurrency(),
        exchangeRates.observeRates(),
        countriesByYear,
    ) { total, years, main, rates, countriesByYear ->
        OverviewUiState(
            isLoading = false,
            total = row(total, main, rates, countriesByYear.totalCountries()),
            years = years.map { it.year to row(it.totals, main, rates, countriesByYear[it.year].orEmpty()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun row(totals: TourTotals, main: Currency, rates: List<ExchangeRate>, countries: Set<String>) = TotalsRow(
        totals = totals,
        conversion = if (totals.costs.all { it.currency == main }) null else convert(totals.costs, main, rates),
        countries = countries,
    )
}
