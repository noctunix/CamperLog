package de.hannes.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parsesGermanAndDotFormats() {
        assertEquals(1_200L, parseEuroToCents("12"))
        assertEquals(1_250L, parseEuroToCents("12,5"))
        assertEquals(1_205L, parseEuroToCents("12,05"))
        assertEquals(123_456L, parseEuroToCents("1.234,56"))
        assertEquals(123_400L, parseEuroToCents("1.234"))
        assertEquals(100_000_000L, parseEuroToCents("1.000.000"))
        assertEquals(1_250L, parseEuroToCents("12.50"))
        assertEquals(1_250L, parseEuroToCents("12.5"))
        assertEquals(0L, parseEuroToCents("0"))
    }

    @Test
    fun ignoresEuroSignAndWhitespace() {
        assertEquals(4_990L, parseEuroToCents(" 49,90 € "))
        assertEquals(4_990L, parseEuroToCents("€49,90"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf("", "abc", "-5", "12,345", "1,2,3", "12.", ",50", "1.23.45", "12,5x", "99999999999999999999")
            .forEach { assertNull("'$it' sollte ungültig sein", parseEuroToCents(it)) }
    }

    @Test
    fun formatsCents() {
        assertEquals("1.234,56 €", formatEuro(123_456))
        assertEquals("0,05 €", formatEuro(5))
        assertEquals("1234,56", centsToInput(123_456))
        assertEquals("1234.56", centsToDecimal(123_456))
        assertEquals("0.00", centsToDecimal(0))
    }

    @Test
    fun inputFormatParsesBackLosslessly() {
        listOf(0L, 1L, 99L, 100L, 123_456L, 987_654_321L).forEach {
            assertEquals(it, parseEuroToCents(centsToInput(it)))
            assertEquals(it, parseEuroToCents(centsToDecimal(it)))
        }
    }
}
