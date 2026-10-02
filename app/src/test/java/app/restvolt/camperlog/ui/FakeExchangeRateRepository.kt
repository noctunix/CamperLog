package app.restvolt.camperlog.ui

import android.database.sqlite.SQLiteException
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Currency

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomExchangeRateRepositoryTest. */
class FakeExchangeRateRepository(
    initial: List<ExchangeRate> = emptyList(),
    mainCurrency: Currency = EUR,
) : ExchangeRateRepository {

    private val state = MutableStateFlow(initial.associateBy { it.currency })
    private val main = MutableStateFlow(mainCurrency)

    val rates: List<ExchangeRate> get() = state.value.values.sortedBy { it.currency.currencyCode }
    val mainCurrency: Currency get() = main.value

    /** Simuliert eine volle oder defekte Datenbank: Schreibzugriffe werfen dann eine [SQLiteException]. */
    var failWrites = false

    private fun checkWritable() {
        if (failWrites) throw SQLiteException("simulierter Schreibfehler")
    }

    override fun observeRates(): Flow<List<ExchangeRate>> =
        state.map { map -> map.values.sortedBy { it.currency.currencyCode } }

    override suspend fun saveRate(rate: ExchangeRate) {
        checkWritable()
        require(rate.currency != EUR && rate.perEuro.signum() > 0)
        state.value += rate.currency to rate
    }

    override suspend fun deleteRate(currency: Currency) {
        checkWritable()
        state.value -= currency
    }

    override fun observeMainCurrency(): Flow<Currency> = main

    override suspend fun setMainCurrency(currency: Currency) {
        checkWritable()
        main.value = currency
    }
}
