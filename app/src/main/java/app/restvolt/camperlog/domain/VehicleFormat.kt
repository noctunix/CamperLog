package app.restvolt.camperlog.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Formatiert eine in Zentimetern gespeicherte Länge mit Tausendertrennzeichen, z. B. `450 cm`. */
fun formatCm(cm: Int, locale: Locale): String = "${integerFormat(locale).format(cm)} cm"

/** Formatiert ein Gewicht in kg mit Tausendertrennzeichen, z. B. `3.500 kg`. */
fun formatKg(kg: Int, locale: Locale): String = "${integerFormat(locale).format(kg)} kg"

/** Formatiert eine Motorleistung mit der abgeleiteten PS-Zahl, z. B. `120 kW (163 PS)`. */
fun formatPower(kw: Int, locale: Locale): String {
    val ps = (kw * 1.35962).roundToInt()
    return "${integerFormat(locale).format(kw)} kW (${integerFormat(locale).format(ps)} PS)"
}

/** Formatiert einen in Millibar gespeicherten Reifendruck als bar mit 2 Nachkommastellen, z. B. `2,80 bar`. */
fun formatBar(mbar: Int, locale: Locale): String = "${decimalFormat(locale, 2).format(BigDecimal.valueOf(mbar.toLong(), 3))} bar"

/** Formatiert ein in Deziliter gespeichertes Volumen als Liter; eine glatte Nachkommastelle entfällt. */
fun formatLitres(dl: Int, locale: Locale): String {
    val value = BigDecimal.valueOf(dl.toLong(), 1)
    val hasFraction = value.stripTrailingZeros().scale() > 0
    val format = decimalFormat(locale, if (hasFraction) 1 else 0)
    return "${format.format(value)} l"
}

/** Formatiert eine Batteriekapazität mit Tausendertrennzeichen, z. B. `200 Ah`. */
fun formatAh(ah: Int, locale: Locale): String = "${integerFormat(locale).format(ah)} Ah"

/** Formatiert eine Solarleistung mit Tausendertrennzeichen, z. B. `300 Wp`. */
fun formatWp(wp: Int, locale: Locale): String = "${integerFormat(locale).format(wp)} Wp"

private fun integerFormat(locale: Locale): NumberFormat = NumberFormat.getIntegerInstance(locale)

private fun decimalFormat(locale: Locale, fractionDigits: Int): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
