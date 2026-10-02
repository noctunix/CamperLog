package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomExchangeRateRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomExchangeRateRepository

    private val nok = Currency.getInstance("NOK")
    private val dkk = Currency.getInstance("DKK")
    private val day = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomExchangeRateRepository(db.exchangeRateDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun keepsExactPrecisionAndSortsByCurrency() = runTest {
        val nokRate = ExchangeRate(nok, BigDecimal("11.4850"), day, "EZB")
        val dkkRate = ExchangeRate(dkk, BigDecimal("7.46"), day.minusDays(3), "")
        repository.saveRate(nokRate)
        repository.saveRate(dkkRate)

        val rates = repository.observeRates().first()

        assertEquals(listOf(dkkRate, nokRate), rates)
        assertEquals(4, rates[1].perEuro.scale())
    }

    @Test
    fun savingSameCurrencyReplacesRateAndDeleteRemovesIt() = runTest {
        repository.saveRate(ExchangeRate(nok, BigDecimal("11.2"), day.minusDays(30), "Bank"))
        val update = ExchangeRate(nok, BigDecimal("11.485"), day, "EZB")
        repository.saveRate(update)

        assertEquals(listOf(update), repository.observeRates().first())

        repository.deleteRate(nok)
        assertEquals(emptyList<ExchangeRate>(), repository.observeRates().first())
    }

    @Test
    fun rejectsEuroAndNonPositiveRates() = runTest {
        listOf(ExchangeRate(EUR, BigDecimal.ONE, day, ""), ExchangeRate(nok, BigDecimal.ZERO, day, "")).forEach {
            val error = runCatching { repository.saveRate(it) }.exceptionOrNull()
            assertTrue(it.toString(), error is IllegalArgumentException)
        }
        assertEquals(emptyList<ExchangeRate>(), repository.observeRates().first())
    }

    @Test
    fun mainCurrencyDefaultsToEuroAndCanBeChanged() = runTest {
        assertEquals(EUR, repository.observeMainCurrency().first())

        repository.setMainCurrency(nok)
        assertEquals(nok, repository.observeMainCurrency().first())

        repository.setMainCurrency(dkk)
        assertEquals(dkk, repository.observeMainCurrency().first())
    }
}
