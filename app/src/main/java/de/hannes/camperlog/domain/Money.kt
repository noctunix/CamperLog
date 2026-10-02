package de.hannes.camperlog.domain

import java.util.Locale

private val germanAmount = Regex("""(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?""")
private val dotDecimalAmount = Regex("""(\d+)\.(\d{1,2})""")

/**
 * Wandelt einen Euro-Betrag wie `12`, `12,5`, `1.234,56` oder `12.50` verlustfrei in Cent um.
 * Ein Eurozeichen und Leerzeichen werden ignoriert.
 *
 * @return Cent-Betrag oder `null`, wenn die Eingabe kein gültiger, nicht negativer Betrag ist
 */
fun parseEuroToCents(input: String): Long? {
    val text = input.replace("€", "").filterNot(Char::isWhitespace)
    val match = dotDecimalAmount.matchEntire(text) ?: germanAmount.matchEntire(text) ?: return null
    val euros = match.groupValues[1].replace(".", "")
    val cents = match.groupValues[2].padEnd(2, '0')
    return (euros + cents).toLongOrNull()
}

/** Formatiert [cents] für die Anzeige, z. B. `1.234,56 €`. */
fun formatEuro(cents: Long): String =
    String.format(Locale.GERMANY, "%,d,%02d €", cents / 100, cents % 100)

/** Formatiert [cents] als editierbaren Eingabewert, z. B. `1234,56`. */
fun centsToInput(cents: Long): String = "%d,%02d".format(Locale.ROOT, cents / 100, cents % 100)

/** Formatiert [cents] als maschinenlesbare Dezimalzahl mit Punkt, z. B. `1234.56`. */
fun centsToDecimal(cents: Long): String = "%d.%02d".format(Locale.ROOT, cents / 100, cents % 100)
