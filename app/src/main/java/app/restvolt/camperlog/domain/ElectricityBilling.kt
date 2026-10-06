package app.restvolt.camperlog.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Abrechnung des Stroms an einem Übernachtungsplatz, mit stabilem Exportwert [csvValue]; `null` auf
 * [Station.electricityBilling] bedeutet "nicht angegeben". NONE: nicht genutzt. INCLUDED: im
 * Stellplatzpreis enthalten. FLAT_PER_NIGHT/FLAT_PER_STAY: [Station.electricityFlatAmount] je Nacht
 * bzw. einmalig. METERED: [Station.electricityPricePerKwh] je kWh. BASE_PLUS_METERED:
 * [Station.electricityBaseFee] plus Preis je kWh. COIN: [Station.electricityCoinPrice] je
 * [Station.electricityCoinsUsed], optional mit [Station.electricityKwhPerCoin].
 */
enum class ElectricityBilling(val csvValue: String) {
    NONE("nicht_genutzt"),
    INCLUDED("inklusive"),
    FLAT_PER_NIGHT("pauschale_pro_nacht"),
    FLAT_PER_STAY("pauschale_gesamt"),
    METERED("nach_verbrauch"),
    BASE_PLUS_METERED("grundgebuehr_plus_verbrauch"),
    COIN("muenzen"),
}

/**
 * Aus [Station.electricityMeterStart]/[Station.electricityMeterEnd] abgeleitete kWh-Menge, sonst
 * direkt aus [Station.electricityKwhUsed], sonst aus [Station.electricityCoinsUsed] ×
 * [Station.electricityKwhPerCoin]; `null`, wenn keine der drei Angaben vollständig vorliegt oder das
 * Ergebnis negativ wäre (z. B. ein rückwärts gelaufener Zähler).
 */
fun electricityKwh(station: Station): BigDecimal? = electricityKwhCore(
    station.electricityMeterStart,
    station.electricityMeterEnd,
    station.electricityKwhUsed,
    station.electricityCoinsUsed,
    station.electricityKwhPerCoin,
)

/**
 * Kernlogik von [electricityKwh], auch für die Live-Vorschau des Formulars ([StationInput.electricityPreview])
 * aus den noch unvalidierten, aber bereits geparsten Einzelwerten.
 */
internal fun electricityKwhCore(
    meterStart: BigDecimal?,
    meterEnd: BigDecimal?,
    kwhUsed: BigDecimal?,
    coinsUsed: Int?,
    kwhPerCoin: BigDecimal?,
): BigDecimal? = when {
    meterStart != null && meterEnd != null -> meterEnd.subtract(meterStart).takeIf { it.signum() >= 0 }
    kwhUsed != null -> kwhUsed.takeIf { it.signum() >= 0 }
    coinsUsed != null && kwhPerCoin != null -> kwhPerCoin.multiply(BigDecimal(coinsUsed)).takeIf { it.signum() >= 0 }
    else -> null
}

/**
 * Von [Station.electricityBilling] abgeleiteter Kostenbetrag, kaufmännisch gerundet auf die kleinste
 * Einheit von [Station.electricityCurrency]; `null` ohne Abrechnungsart, ohne Währung oder ohne die
 * für die Abrechnungsart nötigen Werte (z. B. fehlende Nächte bei [ElectricityBilling.FLAT_PER_NIGHT]
 * oder fehlende kWh bei [ElectricityBilling.METERED]). Nie negativ.
 */
fun electricityCost(station: Station): Money? {
    val billing = station.electricityBilling ?: return null
    val currency = station.electricityCurrency ?: return null
    return electricityCostCore(
        billing = billing,
        currency = currency,
        flatAmount = station.electricityFlatAmount,
        baseFee = station.electricityBaseFee,
        pricePerKwh = station.electricityPricePerKwh,
        coinPrice = station.electricityCoinPrice,
        coinsUsed = station.electricityCoinsUsed,
        kwh = electricityKwh(station),
        nights = station.nights,
    )
}

/**
 * Kernlogik von [electricityCost], auch für die Live-Vorschau des Formulars ([StationInput.electricityPreview])
 * aus den noch unvalidierten, aber bereits geparsten Einzelwerten; [kwh] kommt aus [electricityKwhCore].
 */
internal fun electricityCostCore(
    billing: ElectricityBilling,
    currency: Currency,
    flatAmount: Money?,
    baseFee: Money?,
    pricePerKwh: BigDecimal?,
    coinPrice: Money?,
    coinsUsed: Int?,
    kwh: BigDecimal?,
    nights: Int?,
): Money? = when (billing) {
    ElectricityBilling.NONE, ElectricityBilling.INCLUDED -> null
    ElectricityBilling.FLAT_PER_NIGHT -> {
        val amount = flatAmount ?: return null
        val n = nights?.takeIf { it >= 1 } ?: return null
        Money(Math.multiplyExact(amount.minor, n.toLong()), amount.currency)
    }
    ElectricityBilling.FLAT_PER_STAY -> flatAmount
    ElectricityBilling.METERED -> {
        val price = pricePerKwh?.takeIf { it.signum() >= 0 } ?: return null
        val k = kwh ?: return null
        roundToMinor(price.multiply(k), currency)
    }
    ElectricityBilling.BASE_PLUS_METERED -> {
        val base = baseFee ?: return null
        val price = pricePerKwh?.takeIf { it.signum() >= 0 } ?: return null
        val k = kwh ?: return null
        val metered = roundToMinor(price.multiply(k), currency) ?: return null
        Money(Math.addExact(base.minor, metered.minor), currency)
    }
    ElectricityBilling.COIN -> {
        val price = coinPrice ?: return null
        val count = coinsUsed?.takeIf { it >= 0 } ?: return null
        Money(Math.multiplyExact(price.minor, count.toLong()), price.currency)
    }
}

/** Formatiert eine kWh-Menge mit so vielen Nachkommastellen wie nötig (höchstens 3), z. B. `21,5 kWh`. */
fun formatKwh(value: BigDecimal, locale: Locale): String {
    val fractionDigits = value.stripTrailingZeros().scale().coerceIn(0, 3)
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
    return "${format.format(value)} kWh"
}

/** Rundet [amount] (in Hauptwährungseinheiten) kaufmännisch auf die kleinste Einheit von [currency]; `null` bei Überlauf. */
private fun roundToMinor(amount: BigDecimal, currency: Currency): Money? {
    val minor = amount.setScale(currency.fractionDigits, RoundingMode.HALF_UP).movePointRight(currency.fractionDigits)
    return (amountReading(minor) as? AmountReading.Valid)?.let { Money(it.minor, currency) }
}

/**
 * Migriert das alte Strom-Häkchen einer Station (vor Room-Schema 10 bzw. Sicherungsformat 5) auf
 * [ElectricityBilling]: `YES` meinte "Pauschale galt" (ohne bekannten Betrag), `NO` "nach Verbrauch
 * bezahlt" (ohne bekannte Werte), `NOT_USED` "nicht genutzt".
 */
fun migrateLegacyElectricityFlatRate(rate: ElectricityFlatRate?): ElectricityBilling? = when (rate) {
    null -> null
    ElectricityFlatRate.YES -> ElectricityBilling.FLAT_PER_STAY
    ElectricityFlatRate.NO -> ElectricityBilling.METERED
    ElectricityFlatRate.NOT_USED -> ElectricityBilling.NONE
}
