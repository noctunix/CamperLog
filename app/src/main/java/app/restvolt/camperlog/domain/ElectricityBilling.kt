package app.restvolt.camperlog.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

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
fun electricityKwh(station: Station): BigDecimal? {
    val meterStart = station.electricityMeterStart
    val meterEnd = station.electricityMeterEnd
    val kwhUsed = station.electricityKwhUsed
    val coinsUsed = station.electricityCoinsUsed
    val kwhPerCoin = station.electricityKwhPerCoin
    return when {
        meterStart != null && meterEnd != null -> meterEnd.subtract(meterStart).takeIf { it.signum() >= 0 }
        kwhUsed != null -> kwhUsed.takeIf { it.signum() >= 0 }
        coinsUsed != null && kwhPerCoin != null -> kwhPerCoin.multiply(BigDecimal(coinsUsed)).takeIf { it.signum() >= 0 }
        else -> null
    }
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
    return when (billing) {
        ElectricityBilling.NONE, ElectricityBilling.INCLUDED -> null
        ElectricityBilling.FLAT_PER_NIGHT -> {
            val amount = station.electricityFlatAmount ?: return null
            val nights = station.nights?.takeIf { it >= 1 } ?: return null
            Money(Math.multiplyExact(amount.minor, nights.toLong()), amount.currency)
        }
        ElectricityBilling.FLAT_PER_STAY -> station.electricityFlatAmount
        ElectricityBilling.METERED -> {
            val price = station.electricityPricePerKwh?.takeIf { it.signum() >= 0 } ?: return null
            val kwh = electricityKwh(station) ?: return null
            roundToMinor(price.multiply(kwh), currency)
        }
        ElectricityBilling.BASE_PLUS_METERED -> {
            val base = station.electricityBaseFee ?: return null
            val price = station.electricityPricePerKwh?.takeIf { it.signum() >= 0 } ?: return null
            val kwh = electricityKwh(station) ?: return null
            val metered = roundToMinor(price.multiply(kwh), currency) ?: return null
            Money(Math.addExact(base.minor, metered.minor), currency)
        }
        ElectricityBilling.COIN -> {
            val price = station.electricityCoinPrice ?: return null
            val count = station.electricityCoinsUsed?.takeIf { it >= 0 } ?: return null
            Money(Math.multiplyExact(price.minor, count.toLong()), price.currency)
        }
    }
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
