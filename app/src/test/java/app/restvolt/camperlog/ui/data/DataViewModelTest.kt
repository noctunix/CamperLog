package app.restvolt.camperlog.ui.data

import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.decodeBackup
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class DataViewModelTest {

    private val nok = Currency.getInstance("NOK")

    private val tour = Tour(
        uuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60",
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 3),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 800,
        costs = listOf(Money(320000, nok)),
        pitchAssigned = false,
        electricityFlatRate = ElectricityFlatRate.NOT_USED,
        lteQuality = LteQuality.OK,
        pitchSlope = PitchSlope.SLOPED,
        levelingBlocksUsed = false,
        notes = "",
        mapLink = null,
        createdAt = Instant.parse("2026-07-04T08:00:00Z"),
        updatedAt = Instant.parse("2026-07-04T08:00:00Z"),
    )

    @Test
    fun backupJson_containsToursRatesAndMainCurrency() = runBlocking {
        val rate = ExchangeRate(nok, BigDecimal("11.5"), LocalDate.of(2026, 10, 1), "EZB")
        val exportedAt = Instant.parse("2026-10-04T12:00:00Z")
        val viewModel = DataViewModel(
            FakeTourRepository(listOf(tour)),
            FakeExchangeRateRepository(listOf(rate), mainCurrency = nok),
            clock = { exportedAt },
        )

        val backup = (decodeBackup(viewModel.backupJson()) as BackupReadResult.Success).backup

        assertEquals(exportedAt, backup.exportedAt)
        assertEquals(nok, backup.mainCurrency)
        assertEquals(listOf(rate.currency), backup.rates.map { it.currency })
        assertEquals(0, rate.perEuro.compareTo(backup.rates.single().perEuro))
        assertEquals(listOf(tour), backup.tours)
    }
}
