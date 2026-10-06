package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class ElectricityBillingTest {

    private val jpy = Currency.getInstance("JPY")

    private val base = Station(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        nights = 3,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun noneAndIncludedNeverProduceACost() {
        assertNull(electricityCost(base.copy(electricityBilling = ElectricityBilling.NONE, electricityCurrency = EUR)))
        assertNull(electricityCost(base.copy(electricityBilling = ElectricityBilling.INCLUDED, electricityCurrency = EUR)))
    }

    @Test
    fun missingBillingOrCurrencyIsNull() {
        assertNull(electricityCost(base))
        assertNull(
            electricityCost(
                base.copy(electricityBilling = ElectricityBilling.FLAT_PER_STAY, electricityFlatAmount = Money(500, EUR)),
            ),
        )
    }

    @Test
    fun flatPerNightMultipliesByNights() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.FLAT_PER_NIGHT,
            electricityCurrency = EUR,
            electricityFlatAmount = Money(500, EUR),
            nights = 3,
        )
        assertEquals(Money(1500, EUR), electricityCost(station))
    }

    @Test
    fun flatPerNightWithoutNightsIsNull() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.FLAT_PER_NIGHT,
            electricityCurrency = EUR,
            electricityFlatAmount = Money(500, EUR),
            nights = null,
        )
        assertNull(electricityCost(station))
    }

    @Test
    fun flatPerStayIgnoresNights() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.FLAT_PER_STAY,
            electricityCurrency = EUR,
            electricityFlatAmount = Money(1200, EUR),
            nights = 7,
        )
        assertEquals(Money(1200, EUR), electricityCost(station))
    }

    @Test
    fun flatPerStayWithoutAmountIsNull() {
        val station = base.copy(electricityBilling = ElectricityBilling.FLAT_PER_STAY, electricityCurrency = EUR)
        assertNull(electricityCost(station))
    }

    @Test
    fun meteredMultipliesPriceByDirectKwh() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.35"),
            electricityKwhUsed = BigDecimal("10"),
        )
        assertEquals(Money(350, EUR), electricityCost(station))
    }

    @Test
    fun meteredMultipliesPriceByMeterDifference() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.35"),
            electricityMeterStart = BigDecimal("120.5"),
            electricityMeterEnd = BigDecimal("135.5"),
        )
        assertEquals(Money(525, EUR), electricityCost(station))
    }

    @Test
    fun meterDifferenceTakesPrecedenceOverDirectKwh() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("1"),
            electricityMeterStart = BigDecimal("0"),
            electricityMeterEnd = BigDecimal("5"),
            electricityKwhUsed = BigDecimal("999"),
        )
        assertEquals(Money(500, EUR), electricityCost(station))
    }

    @Test
    fun meteredWithoutPriceOrKwhIsNull() {
        val withoutPrice = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityKwhUsed = BigDecimal("10"),
        )
        val withoutKwh = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.35"),
        )
        assertNull(electricityCost(withoutPrice))
        assertNull(electricityCost(withoutKwh))
    }

    @Test
    fun meteredRoundsHalfUpToTheCurrencyMinorUnit() {
        // 0.333 * 3 = 0.999 -> rounds up to 1.00 EUR.
        val roundsUp = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.333"),
            electricityKwhUsed = BigDecimal("3"),
        )
        assertEquals(Money(100, EUR), electricityCost(roundsUp))

        // 0.335 * 3 = 1.005 -> half up rounds to 1.01 EUR.
        val halfUp = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.335"),
            electricityKwhUsed = BigDecimal("3"),
        )
        assertEquals(Money(101, EUR), electricityCost(halfUp))
    }

    @Test
    fun meteredRoundsToAZeroFractionDigitCurrency() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = jpy,
            electricityPricePerKwh = BigDecimal("30"),
            electricityKwhUsed = BigDecimal("3.5"),
        )
        // 30 * 3.5 = 105 -> JPY has no minor unit, already an integer.
        assertEquals(Money(105, jpy), electricityCost(station))
    }

    @Test
    fun basePlusMeteredAddsBaseFeeAndMeteredPart() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.BASE_PLUS_METERED,
            electricityCurrency = EUR,
            electricityBaseFee = Money(300, EUR),
            electricityPricePerKwh = BigDecimal("0.5"),
            electricityKwhUsed = BigDecimal("10"),
        )
        assertEquals(Money(800, EUR), electricityCost(station))
    }

    @Test
    fun basePlusMeteredWithoutBaseFeeOrKwhIsNull() {
        val withoutBaseFee = base.copy(
            electricityBilling = ElectricityBilling.BASE_PLUS_METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.5"),
            electricityKwhUsed = BigDecimal("10"),
        )
        val withoutKwh = base.copy(
            electricityBilling = ElectricityBilling.BASE_PLUS_METERED,
            electricityCurrency = EUR,
            electricityBaseFee = Money(300, EUR),
            electricityPricePerKwh = BigDecimal("0.5"),
        )
        assertNull(electricityCost(withoutBaseFee))
        assertNull(electricityCost(withoutKwh))
    }

    @Test
    fun coinMultipliesPriceByCoinsUsed() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.COIN,
            electricityCurrency = EUR,
            electricityCoinPrice = Money(200, EUR),
            electricityCoinsUsed = 4,
        )
        assertEquals(Money(800, EUR), electricityCost(station))
    }

    @Test
    fun coinWithZeroCoinsUsedIsAZeroCost() {
        val station = base.copy(
            electricityBilling = ElectricityBilling.COIN,
            electricityCurrency = EUR,
            electricityCoinPrice = Money(200, EUR),
            electricityCoinsUsed = 0,
        )
        assertEquals(Money(0, EUR), electricityCost(station))
    }

    @Test
    fun coinWithoutPriceOrCountIsNull() {
        val withoutPrice = base.copy(
            electricityBilling = ElectricityBilling.COIN,
            electricityCurrency = EUR,
            electricityCoinsUsed = 4,
        )
        val withoutCount = base.copy(
            electricityBilling = ElectricityBilling.COIN,
            electricityCurrency = EUR,
            electricityCoinPrice = Money(200, EUR),
        )
        assertNull(electricityCost(withoutPrice))
        assertNull(electricityCost(withoutCount))
    }

    @Test
    fun kwhComesFromMeterDifferenceWhenBothReadingsArePresent() {
        val station = base.copy(electricityMeterStart = BigDecimal("10"), electricityMeterEnd = BigDecimal("17.5"))
        assertEquals(BigDecimal("7.5"), electricityKwh(station))
    }

    @Test
    fun negativeMeterDifferenceIsNull() {
        val station = base.copy(electricityMeterStart = BigDecimal("20"), electricityMeterEnd = BigDecimal("15"))
        assertNull(electricityKwh(station))
    }

    @Test
    fun kwhFallsBackToDirectValueWithoutMeterReadings() {
        val station = base.copy(electricityKwhUsed = BigDecimal("12.3"))
        assertEquals(BigDecimal("12.3"), electricityKwh(station))
    }

    @Test
    fun kwhFallsBackToCoinsTimesKwhPerCoinWithoutMeterOrDirectValue() {
        val withKwhPerCoin = base.copy(electricityCoinsUsed = 4, electricityKwhPerCoin = BigDecimal("0.5"))
        assertEquals(BigDecimal("2.0"), electricityKwh(withKwhPerCoin))

        val withoutKwhPerCoin = base.copy(electricityCoinsUsed = 4)
        assertNull(electricityKwh(withoutKwhPerCoin))
    }

    @Test
    fun kwhIsNullWithoutAnySource() {
        assertNull(electricityKwh(base))
    }

    @Test
    fun migratesTheLegacyFlatRateFlag() {
        assertEquals(ElectricityBilling.FLAT_PER_STAY, migrateLegacyElectricityFlatRate(ElectricityFlatRate.YES))
        assertEquals(ElectricityBilling.METERED, migrateLegacyElectricityFlatRate(ElectricityFlatRate.NO))
        assertEquals(ElectricityBilling.NONE, migrateLegacyElectricityFlatRate(ElectricityFlatRate.NOT_USED))
        assertNull(migrateLegacyElectricityFlatRate(null))
    }
}
