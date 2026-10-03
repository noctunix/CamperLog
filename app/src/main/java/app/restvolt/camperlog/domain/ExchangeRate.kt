package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Höchstzahl der Nachkommastellen eines Kurses. */
const val RATE_FRACTION_DIGITS = 6

/** Manuell gepflegter Kurs: 1 EUR = [perEuro] [currency], Stand [date], laut [source] (darf leer sein). */
data class ExchangeRate(
    val currency: Currency,
    val perEuro: BigDecimal,
    val date: LocalDate,
    val source: String,
)

/** Wechselkurse und Hauptwährung; EUR hat immer den Kurs 1 und wird nicht gespeichert. */
interface ExchangeRateRepository {

    /** Alle Kurse, sortiert nach Währungscode. */
    fun observeRates(): Flow<List<ExchangeRate>>

    /** Legt den Kurs für [ExchangeRate.currency] an oder ersetzt ihn. */
    suspend fun saveRate(rate: ExchangeRate)

    suspend fun deleteRate(currency: Currency)

    /** Hauptwährung für umgerechnete Summen; ohne Einstellung EUR. */
    fun observeMainCurrency(): Flow<Currency>

    suspend fun setMainCurrency(currency: Currency)
}

/**
 * Ergebnis von [convert]: [total] ist die gerundete Summe in der Zielwährung oder `null`, wenn
 * für eine der Währungen in [missing] kein Kurs vorliegt.
 */
data class Conversion(val total: Money?, val missing: List<Currency>)

/**
 * Rechnet [amounts] über den Euro in [target] um und summiert sie. Gerechnet wird mit BigDecimal,
 * gerundet wird nur einmal am Ende, kaufmännisch auf die Nachkommastellen von [target].
 *
 * Fehlt für eine benötigte Währung der Kurs – auch für [target] selbst –, gibt es keine Teilsumme,
 * sondern nur die Liste der fehlenden Währungen, damit nichts Unvollständiges als Summe erscheint.
 */
fun convert(amounts: List<Money>, target: Currency, rates: List<ExchangeRate>): Conversion {
    val perEuro = rates.associate { it.currency to it.perEuro } + (EUR to BigDecimal.ONE)
    val foreign = amounts.map(Money::currency).filter { it != target }.distinct()
    val needed = if (foreign.isEmpty()) foreign else foreign + target
    val missing = needed.filter { it !in perEuro }.sortedBy(Currency::getCurrencyCode)
    if (missing.isNotEmpty()) return Conversion(total = null, missing = missing)

    val sum = amounts.fold(BigDecimal.ZERO) { acc, money ->
        val value = BigDecimal.valueOf(money.minor, money.currency.fractionDigits)
        acc + if (money.currency == target) {
            value
        } else {
            value.multiply(perEuro.getValue(target)).divide(perEuro.getValue(money.currency), MathContext.DECIMAL128)
        }
    }
    val minor = sum.setScale(target.fractionDigits, RoundingMode.HALF_UP).unscaledValue().toLong()
    return Conversion(total = Money(minor, target), missing = emptyList())
}

/**
 * Liest einen Kurs wie `11,485`, `0.8456` oder `1.234,5` mit den Trennzeichen-Regeln von [parseAmount].
 *
 * @return Kurs mit der eingegebenen Genauigkeit oder `null`, wenn die Eingabe ungültig, nicht
 *   positiv oder genauer als [RATE_FRACTION_DIGITS] Nachkommastellen ist
 */
fun parseRate(input: String, locale: Locale): BigDecimal? =
    parseDecimal(input, locale, RATE_FRACTION_DIGITS)?.takeIf { it.signum() > 0 }

/** Formatiert [rate] für die Anzeige mit genau den gespeicherten Nachkommastellen, z. B. `11,4850`. */
fun formatRate(rate: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = rate.scale().coerceAtLeast(0)
        maximumFractionDigits = rate.scale().coerceAtLeast(0)
    }.format(rate)

/** Formatiert [rate] als editierbaren Eingabewert ohne Tausendertrennzeichen, z. B. `11,4850`. */
fun rateToInput(rate: BigDecimal, locale: Locale): String =
    rate.toPlainString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
