package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency
import java.util.Locale

class MoneyTest {

    private val de = Locale.GERMANY
    private val us = Locale.US
    private val isk = Currency.getInstance("ISK")
    private val kwd = Currency.getInstance("KWD")

    private fun euro(text: String, locale: Locale = de) = parseAmount(text, EUR, locale)

    /** NumberFormat trennt Betrag und Symbol mit geschütztem Leerzeichen; für lesbare Vergleiche normalisieren. */
    private fun plain(text: String) = text.replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun parsesGermanAndDotFormats() {
        assertEquals(1_200L, euro("12"))
        assertEquals(1_250L, euro("12,5"))
        assertEquals(1_205L, euro("12,05"))
        assertEquals(123_456L, euro("1.234,56"))
        assertEquals(123_400L, euro("1.234"))
        assertEquals(100_000_000L, euro("1.000.000"))
        assertEquals(1_250L, euro("12.50"))
        assertEquals(1_250L, euro("12.5"))
        assertEquals(0L, euro("0"))
    }

    @Test
    fun parsesEnglishFormats() {
        assertEquals(123_456L, euro("1,234.56", us))
        assertEquals(123_400L, euro("1,234", us))
        assertEquals(1_250L, euro("12.50", us))
        assertEquals(1_250L, euro("12,50", us))
        assertNull(euro("1.234", us))
    }

    @Test
    fun ignoresSymbolsCodesAndSpaces() {
        assertEquals(4_990L, euro(" 49,90 € "))
        assertEquals(4_990L, euro("€49,90"))
        assertEquals(4_990L, euro("49,90 EUR"))
        assertEquals(123_456L, euro("1 234,56"))
        assertEquals(123_456L, euro("1\u202F234,56"))
        assertEquals(123_456L, euro("1'234.56", Locale.forLanguageTag("de-CH")))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf("", "abc", "-5", "12,345", "1,2,3", "12.", ",50", "1.23.45", "12,5x", "1.2,34", "99999999999999999999")
            .forEach { assertNull("'$it' sollte ungültig sein", euro(it)) }
    }

    @Test
    fun limitsAmountToMaxMinorUnits() {
        val de = Locale.GERMANY
        assertEquals(AmountReading.Valid(MAX_AMOUNT_MINOR), readAmount("100.000.000.000,00", EUR, de))
        assertEquals(AmountReading.TooLarge, readAmount("100.000.000.000,01", EUR, de))
        assertEquals(AmountReading.TooLarge, readAmount("99999999999999999999", EUR, de))
        assertEquals(AmountReading.Invalid, readAmount("12,5x", EUR, de))
        assertNull(parseAmount("100000000001", EUR, de))
    }

    @Test
    fun respectsFractionDigitsOfCurrency() {
        assertEquals(1_500L, parseAmount("1.500", isk, de))
        assertNull(parseAmount("15,5", isk, de))
        assertEquals(1_234L, parseAmount("1.234", kwd, us))
        assertEquals(1_234_000L, parseAmount("1,234", kwd, us))
    }

    @Test
    fun formatsForLocaleAndCurrency() {
        assertEquals("1.234,56 €", plain(formatAmount(123_456, EUR, de)))
        assertEquals("0,05 €", plain(formatAmount(5, EUR, de)))
        assertEquals("€1,234.56", plain(formatAmount(123_456, EUR, us)))
        assertEquals("1.500 ISK", plain(formatAmount(1_500, isk, de)))
    }

    @Test
    fun inputAndDecimalFormats() {
        assertEquals("1234,56", amountToInput(123_456, EUR, de))
        assertEquals("1234.56", amountToInput(123_456, EUR, us))
        assertEquals("1500", amountToInput(1_500, isk, de))
        assertEquals("1234.56", amountToDecimal(123_456, EUR))
        assertEquals("0.00", amountToDecimal(0, EUR))
        assertEquals("1.234", amountToDecimal(1_234, kwd))
    }

    @Test
    fun inputFormatParsesBackLosslessly() {
        listOf(0L, 1L, 99L, 100L, 123_456L, 987_654_321L).forEach {
            for (locale in listOf(de, us)) {
                assertEquals(it, euro(amountToInput(it, EUR, locale), locale))
                assertEquals(it, euro(amountToDecimal(it, EUR), locale))
            }
        }
    }

    @Test
    fun sumsPerCurrencySortedByCodeWithoutZeroSums() {
        val nok = Currency.getInstance("NOK")
        val sums = listOf(Money(100, nok), Money(250, EUR), Money(-100, nok), Money(50, EUR), Money(7, isk)).sumByCurrency()
        assertEquals(listOf(Money(300, EUR), Money(7, isk)), sums)
        assertEquals(emptyList<Money>(), emptyList<Money>().sumByCurrency())
    }

    @Test
    fun sumMinor_addsAndRejectsOverflow() {
        assertEquals(Long.MAX_VALUE, listOf(Money(Long.MAX_VALUE - 1, EUR), Money(1, EUR)).sumMinor())
        assertThrows(ArithmeticException::class.java) {
            listOf(Money(Long.MAX_VALUE, EUR), Money(1, EUR)).sumByCurrency()
        }
    }

    @Test
    fun formatsSeveralAmountsJoined() {
        assertEquals("0,00 €", plain(formatAmounts(emptyList(), de)))
        assertEquals("12,50 € · 1.500 ISK", plain(formatAmounts(listOf(Money(1_250, EUR), Money(1_500, isk)), de)))
    }

    @Test
    fun currencyListsContainQuickCurrenciesAndNoUnitsOfAccount() {
        assertEquals(EUR, QUICK_CURRENCIES.first())
        assertTrue(ALL_CURRENCIES.containsAll(QUICK_CURRENCIES))
        assertEquals(ALL_CURRENCIES.sortedBy { it.currencyCode }, ALL_CURRENCIES)
        assertFalse(Currency.getInstance("XAU") in ALL_CURRENCIES)
    }
}
