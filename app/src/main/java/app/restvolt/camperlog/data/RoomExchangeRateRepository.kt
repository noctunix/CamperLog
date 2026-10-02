package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Currency

/** [ExchangeRateRepository] auf Basis von Room. */
class RoomExchangeRateRepository(private val dao: ExchangeRateDao) : ExchangeRateRepository {

    override fun observeRates(): Flow<List<ExchangeRate>> =
        dao.observeRates().map { rows -> rows.map(ExchangeRateEntity::toDomain) }

    override suspend fun saveRate(rate: ExchangeRate) {
        require(rate.currency != EUR) { "EUR hat immer den Kurs 1" }
        require(rate.perEuro.signum() > 0) { "Kurs muss positiv sein" }
        dao.upsertRate(rate.toEntity())
    }

    override suspend fun deleteRate(currency: Currency) = dao.deleteRate(currency.currencyCode)

    override fun observeMainCurrency(): Flow<Currency> =
        dao.observeMainCurrency().map { code -> code?.let(Currency::getInstance) ?: EUR }.distinctUntilChanged()

    override suspend fun setMainCurrency(currency: Currency) =
        dao.upsertSettings(SettingsEntity(mainCurrency = currency.currencyCode))
}
