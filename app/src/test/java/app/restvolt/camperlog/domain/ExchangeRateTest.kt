package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

class ExchangeRateTest {

    private val nok = Currency.getInstance("NOK")
    private val dkk = Currency.getInstance("DKK")
    private val isk = Currency.getInstance("ISK")
    private val day = LocalDate.of(2026, 10, 1)

    private fun rate(currency: Currency, perEuro: String) = ExchangeRate(currency, BigDecimal(perEuro), day, "EZB")

    private val rates = listOf(rate(nok, "11.4850"), rate(dkk, "7.4600"), rate(isk, "145"))

    @Test
    fun convertsForeignAmountsIntoEuro() {
        val result = convert(listOf(Money(11_485, nok), Money(1_000, EUR), Money(1_450, isk)), EUR, rates)

        // 114,85 NOK = 10 €, 10 €, 1.450 ISK = 10 €
        assertEquals(Conversion(Money(3_000, EUR), emptyList()), result)
    }

    @Test
    fun convertsBetweenTwoForeignCurrenciesAndRoundsOnceAtTheEnd() {
        // 3 × 1 DKK = 3 × 11,485/7,46 NOK = 4,61863… NOK; einzeln gerundet wären es 4,62.
        val amounts = List(3) { Money(100, dkk) } + Money(1_000, nok)

        assertEquals(Money(1_462, nok), convert(amounts, nok, rates).total)
    }

    @Test
    fun roundsHalfUpToTargetFractionDigits() {
        // 1 € = 145 ISK: 0,05 € = 7,25 ISK → 7 ISK, 0,01 € = 1,45 ISK → 1 ISK
        assertEquals(Money(7, isk), convert(listOf(Money(5, EUR)), isk, rates).total)
        assertEquals(Money(1, isk), convert(listOf(Money(1, EUR)), isk, rates).total)
        // 0,10 € = 14,5 ISK → 15 ISK (kaufmännisch aufgerundet)
        assertEquals(Money(15, isk), convert(listOf(Money(10, EUR)), isk, rates).total)
    }

    @Test
    fun reportsMissingRatesInsteadOfPartialSum() {
        val sek = Currency.getInstance("SEK")

        val result = convert(listOf(Money(500, sek), Money(100, nok), Money(200, sek)), EUR, rates)

        assertEquals(Conversion(null, listOf(sek)), result)
    }

    @Test
    fun missingTargetRateIsReportedToo() {
        val chf = Currency.getInstance("CHF")

        assertEquals(listOf(chf), convert(listOf(Money(100, EUR)), chf, rates).missing)
    }

    @Test
    fun amountsAlreadyInTargetNeedNoRate() {
        val chf = Currency.getInstance("CHF")

        assertEquals(Conversion(Money(250, chf), emptyList()), convert(listOf(Money(250, chf)), chf, emptyList()))
        assertEquals(Conversion(Money(0, EUR), emptyList()), convert(emptyList(), EUR, emptyList()))
    }

    @Test
    fun parsesRatesWithLocaleRules() {
        assertEquals(BigDecimal("11.4850"), parseRate("11,4850", Locale.GERMANY))
        assertEquals(BigDecimal("0.8456"), parseRate("0.8456", Locale.GERMANY))
        assertEquals(BigDecimal("1234.5"), parseRate("1.234,5", Locale.GERMANY))
        assertEquals(BigDecimal("11.485"), parseRate("11,485", Locale.GERMANY))
        assertEquals(BigDecimal("11485"), parseRate("11,485", Locale.US))
        assertEquals(BigDecimal("145"), parseRate(" 145 ", Locale.GERMANY))
    }

    @Test
    fun rejectsInvalidRates() {
        listOf("", "0", "0,000", "-1", "abc", "1,1234567", ",5").forEach {
            assertNull(it, parseRate(it, Locale.GERMANY))
        }
    }

    @Test
    fun formatsRatesWithStoredPrecision() {
        assertEquals("11,4850", formatRate(BigDecimal("11.4850"), Locale.GERMANY))
        assertEquals("17.500", formatRate(BigDecimal("17500"), Locale.GERMANY))
        assertEquals("11,4850", rateToInput(BigDecimal("11.4850"), Locale.GERMANY))
        assertEquals("17500", rateToInput(BigDecimal("17500"), Locale.GERMANY))
    }
}
