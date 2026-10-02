package de.hannes.camperlog.domain

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Euro, die Vorgabewährung. */
val EUR: Currency = Currency.getInstance("EUR")

/** Nachkommastellen von [currency], z. B. 2 für EUR und 0 für ISK. */
val Currency.fractionDigits: Int
    get() = defaultFractionDigits.coerceAtLeast(0)

/**
 * Wandelt einen Betrag wie `12`, `12,5`, `1.234,56`, `1,234.56` oder `12.50` verlustfrei in die
 * kleinste Einheit von [currency] um (Cent bei EUR).
 *
 * Komma und Punkt werden beide akzeptiert. Kommen beide vor, ist das letzte Zeichen das
 * Dezimaltrennzeichen. Steht nur ein einzelnes Trennzeichen vor genau drei Ziffern, entscheidet
 * [locale]: Ist es dort das Dezimaltrennzeichen, wäre der Betrag zu genau und ist ungültig, sonst
 * ist es ein Tausendertrennzeichen. Leerzeichen, Apostrophe, Währungssymbol und -code werden ignoriert.
 *
 * @return Betrag in der kleinsten Einheit oder `null`, wenn die Eingabe kein gültiger, nicht
 *   negativer Betrag ist
 */
fun parseAmount(input: String, currency: Currency, locale: Locale): Long? {
    val text = input
        .replace(currency.currencyCode, "", ignoreCase = true)
        .replace(currency.getSymbol(locale), "")
        .replace("€", "")
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) || it == '\'' || it == '’' }
    if (text.isEmpty() || text.any { !it.isDigit() && it != '.' && it != ',' }) return null

    val digits = currency.fractionDigits
    val lastSeparator = text.indexOfLast { it == '.' || it == ',' }
    if (lastSeparator < 0) return toMinor(text, "", digits)

    val separator = text[lastSeparator]
    val tail = text.substring(lastSeparator + 1)
    val head = text.substring(0, lastSeparator)
    val mixed = head.any { it != separator && (it == '.' || it == ',') }
    val single = separator !in head
    val isDecimal = when {
        mixed -> true
        !single -> false
        tail.length != 3 -> true
        else -> separator == DecimalFormatSymbols.getInstance(locale).decimalSeparator
    }
    return if (isDecimal) {
        val integer = if (mixed) ungroup(head, head.first { !it.isDigit() }) else head
        if (tail.isEmpty() || tail.length > digits) null else integer?.let { toMinor(it, tail, digits) }
    } else {
        ungroup(text, separator)?.let { toMinor(it, "", digits) }
    }
}

/** Entfernt Tausendertrennzeichen [separator]; `null`, wenn die Gruppen nicht dreistellig sind. */
private fun ungroup(text: String, separator: Char): String? {
    val groups = text.split(separator)
    val valid = groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 } &&
        groups.all { group -> group.all(Char::isDigit) }
    return if (valid) groups.joinToString("") else null
}

private fun toMinor(integer: String, fraction: String, digits: Int): Long? {
    if (integer.isEmpty()) return null
    return (integer + fraction.padEnd(digits, '0')).toLongOrNull()
}

/** Formatiert [minor] für die Anzeige nach den Regeln von [locale], z. B. `1.234,56 €`. */
fun formatAmount(minor: Long, currency: Currency, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = currency.fractionDigits
        maximumFractionDigits = currency.fractionDigits
    }.format(BigDecimal.valueOf(minor, currency.fractionDigits))

/** Formatiert [minor] als editierbaren Eingabewert ohne Tausendertrennzeichen, z. B. `1234,56`. */
fun amountToInput(minor: Long, currency: Currency, locale: Locale): String =
    amountToDecimal(minor, currency).replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

/** Formatiert [minor] als maschinenlesbare Dezimalzahl mit Punkt, z. B. `1234.56`. */
fun amountToDecimal(minor: Long, currency: Currency): String =
    BigDecimal.valueOf(minor, currency.fractionDigits).toPlainString()
