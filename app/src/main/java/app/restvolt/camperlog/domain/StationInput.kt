package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

/** Höchstlängen der Textfelder einer Station, wie bei Touren auch von der Sicherung wiederverwendet. */
const val MAX_STATION_NAME_LENGTH = 500
const val MAX_STATION_PLACE_LENGTH = 500
const val MAX_STATION_NOTES_LENGTH = 20_000
const val MAX_STATION_MAP_LINK_LENGTH = 4_000
const val MAX_STATION_LINK_LENGTH = 4_000
const val MAX_TOLL_PAYMENT_METHOD_LENGTH = 500
const val MAX_FERRY_BOOKING_REFERENCE_LENGTH = 500
const val MAX_STATION_COST_NOTE_LENGTH = 500

/** Höchstwerte der Stromabrechnung, wie bei Touren auch von der Sicherung wiederverwendet. */
const val MAX_ELECTRICITY_KWH = 100_000.0
const val MAX_PRICE_PER_KWH = 100.0
const val MAX_ELECTRICITY_COINS = 10_000

/** Nachkommastellen der Eingabefelder für kWh/Zählerstände bzw. den Preis je kWh. */
private const val ELECTRICITY_KWH_FRACTION_DIGITS = 3
private const val PRICE_PER_KWH_FRACTION_DIGITS = 4

/** WGS84-Wertebereiche gültiger Koordinaten. */
val LATITUDE_RANGE = -90.0..90.0
val LONGITUDE_RANGE = -180.0..180.0

/**
 * Eine Kostenzeile des Stationsformulars: Betrag als Text in [currency], mit Kategorie und optionaler
 * Notiz. Anders als bei Touren startet [StationInput.costs] leer; "Kosten hinzufügen" legt die erste Zeile an.
 */
@Serializable
data class StationCostInput(
    val category: CostCategory = CostCategory.OTHER,
    val amount: String = "",
    @Serializable(with = CurrencySerializer::class) val currency: Currency = EUR,
    val note: String = "",
)

/**
 * Unvalidierte Eingaben des Stationsformulars. Koordinaten liegen hier bereits als geparste
 * Gleitkommazahlen vor; das Einlesen eines eingefügten Texts oder Links übernimmt [parseLocationText].
 */
@Serializable
data class StationInput(
    val vehicleId: Long = 0,
    val tourId: Long? = null,
    val type: StationType = StationType.OVERNIGHT,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate? = null,
    @Serializable(with = LocalTimeSerializer::class) val time: LocalTime? = null,
    val name: String = "",
    val place: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateSource: CoordinateSource? = null,
    val accuracyM: Int? = null,
    val mapLink: String? = null,
    val notes: String = "",
    val nights: String = "",
    val siteKind: SiteKind? = null,
    val pitchAssigned: Boolean? = null,
    val lteQuality: LteQuality? = null,
    val pitchSlope: PitchSlope? = null,
    val levelingBlocksUsed: Boolean? = null,
    val electricityBilling: ElectricityBilling? = null,
    @Serializable(with = CurrencySerializer::class) val electricityCurrency: Currency = EUR,
    val electricityFlatAmount: String = "",
    val electricityBaseFee: String = "",
    val electricityPricePerKwh: String = "",
    val electricityCoinPrice: String = "",
    val electricityCoinsUsed: String = "",
    val electricityKwhPerCoin: String = "",
    val electricityMeterStart: String = "",
    val electricityMeterEnd: String = "",
    val electricityKwhUsed: String = "",
    val tollKind: TollKind? = null,
    val tollPaymentMethod: String = "",
    val tollCountry: String = "",
    @Serializable(with = LocalDateSerializer::class) val tollValidFrom: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val tollValidUntil: LocalDate? = null,
    val ferryBookingReference: String = "",
    val costs: List<StationCostInput> = emptyList(),
    val services: Set<StationService> = emptySet(),
    val favorite: Boolean = false,
    /** 1–5, für jeden Stationstyp; `null` bedeutet "keine Bewertung". Direkt gesetzt, kein Freitext. */
    val rating: Int? = null,
    val odometerKm: String = "",
    /** Eingabe in ganzen Grad Celsius; siehe [Station.manualTemperatureDeciC] für die interne Einheit. */
    val manualTemperatureC: String = "",
    /** Nur bei [StationType.OVERNIGHT] genutzt. */
    val link: String = "",
    /** Rohtext des Felds "Koordinaten oder Kartenlink"; nur fürs Formular, nicht Teil der Station. */
    val locationText: String = "",
    val weather: WeatherSnapshot? = null,
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class StationField {
    DATE, COORDINATES, NIGHTS, NAME, PLACE, NOTES, MAP_LINK, SERVICES, ELECTRICITY,
    TOLL_COUNTRY, TOLL_VALID_UNTIL, TOLL_PAYMENT_METHOD, FERRY_BOOKING_REFERENCE, COST,
    ODOMETER_KM, MANUAL_TEMPERATURE, LINK,
}

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class StationError {
    REQUIRED,
    INVALID_NUMBER,
    NEGATIVE_NUMBER,
    TOO_SMALL,
    COORDINATES_INCOMPLETE,
    COORDINATES_OUT_OF_RANGE,
    TOO_LONG,
    NOT_A_WEB_LINK,
    FUTURE_DATE,
    INVALID_COUNTRY,
    END_BEFORE_START,
    AMOUNT_TOO_LARGE,
}

/**
 * Prüft alle fachlichen Regeln einer Stations-Eingabe. Felder, die nicht zu [StationInput.type]
 * gehören (z. B. Stellplatz-Details bei einer Nicht-Übernachtung), werden nicht geprüft, sondern in
 * [toStation] stillschweigend verworfen, falls der Typ zuvor gewechselt wurde.
 *
 * @param today Bezugsdatum für die Zukunftsprüfung der Ver-/Entsorgungs-Häkchen
 * @param locale bestimmt, wie mehrdeutige Beträge und Dezimalzahlen der Stromabrechnung gelesen werden
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun StationInput.validate(today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): Map<StationField, StationError> =
    validation(today, locale).errors

internal data class StationValidation(val errors: Map<StationField, StationError>, val costErrors: Map<Int, StationError>)

/** Wie [validate], liefert aber zusätzlich die Fehler je Kostenzeile für die Kostenliste des Formulars. */
internal fun StationInput.validation(today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): StationValidation {
    val costErrs = costErrors(locale)
    val errors = buildMap {
        if (date == null) put(StationField.DATE, StationError.REQUIRED)

        if ((latitude == null) != (longitude == null)) {
            put(StationField.COORDINATES, StationError.COORDINATES_INCOMPLETE)
        } else if (latitude != null && longitude != null && (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE)) {
            put(StationField.COORDINATES, StationError.COORDINATES_OUT_OF_RANGE)
        }

        if (type == StationType.OVERNIGHT && nights.isNotBlank()) {
            val value = nights.trim().toIntOrNull()
            when {
                value == null -> put(StationField.NIGHTS, StationError.INVALID_NUMBER)
                value < 1 -> put(StationField.NIGHTS, StationError.TOO_SMALL)
            }
        }

        if (name.length > MAX_STATION_NAME_LENGTH) put(StationField.NAME, StationError.TOO_LONG)
        if (place.length > MAX_STATION_PLACE_LENGTH) put(StationField.PLACE, StationError.TOO_LONG)
        if (notes.length > MAX_STATION_NOTES_LENGTH) put(StationField.NOTES, StationError.TOO_LONG)

        val link = mapLink?.trim()
        if (!link.isNullOrEmpty()) {
            when {
                link.length > MAX_STATION_MAP_LINK_LENGTH -> put(StationField.MAP_LINK, StationError.TOO_LONG)
                !isWebUrl(link) -> put(StationField.MAP_LINK, StationError.NOT_A_WEB_LINK)
            }
        }

        if (date != null && date > today && services.any { it in SYNCED_SERVICE_LOG_TYPES }) {
            put(StationField.SERVICES, StationError.FUTURE_DATE)
        }

        electricityError(locale)?.let { put(StationField.ELECTRICITY, it) }

        if (type == StationType.TOLL) {
            if (tollCountry.isNotBlank() && tollCountry.trim().uppercase(Locale.ROOT) !in ALL_COUNTRY_CODES) {
                put(StationField.TOLL_COUNTRY, StationError.INVALID_COUNTRY)
            }
            if (tollValidFrom != null && tollValidUntil != null && tollValidUntil < tollValidFrom) {
                put(StationField.TOLL_VALID_UNTIL, StationError.END_BEFORE_START)
            }
            if (tollPaymentMethod.length > MAX_TOLL_PAYMENT_METHOD_LENGTH) {
                put(StationField.TOLL_PAYMENT_METHOD, StationError.TOO_LONG)
            }
        }

        if (type == StationType.FERRY && ferryBookingReference.length > MAX_FERRY_BOOKING_REFERENCE_LENGTH) {
            put(StationField.FERRY_BOOKING_REFERENCE, StationError.TOO_LONG)
        }

        val odometerText = odometerKm.trim()
        if (odometerText.isNotEmpty()) {
            val value = odometerText.toIntOrNull()
            when {
                value == null -> put(StationField.ODOMETER_KM, StationError.INVALID_NUMBER)
                value < 0 -> put(StationField.ODOMETER_KM, StationError.NEGATIVE_NUMBER)
            }
        }

        val temperatureText = manualTemperatureC.trim()
        if (temperatureText.isNotEmpty() && temperatureText.toIntOrNull() == null) {
            put(StationField.MANUAL_TEMPERATURE, StationError.INVALID_NUMBER)
        }

        if (type == StationType.OVERNIGHT) {
            val trimmedLink = this@validation.link.trim()
            if (trimmedLink.isNotEmpty()) {
                when {
                    trimmedLink.length > MAX_STATION_LINK_LENGTH -> put(StationField.LINK, StationError.TOO_LONG)
                    !isWebUrl(trimmedLink) -> put(StationField.LINK, StationError.NOT_A_WEB_LINK)
                }
            }
        }

        costErrs.values.firstOrNull()?.let { put(StationField.COST, it) }
    }
    return StationValidation(errors, costErrs)
}

/** Fehler je Kostenzeile, Schlüssel ist der Index in [StationInput.costs]; leere Beträge sind gültig. */
fun StationInput.costErrors(locale: Locale): Map<Int, StationError> = buildMap {
    costs.forEachIndexed { index, cost ->
        if (cost.note.length > MAX_STATION_COST_NOTE_LENGTH) {
            put(index, StationError.TOO_LONG)
            return@forEachIndexed
        }
        if (cost.amount.isBlank()) return@forEachIndexed
        when (readAmount(cost.amount, cost.currency, locale)) {
            is AmountReading.Valid -> Unit
            AmountReading.TooLarge -> put(index, StationError.AMOUNT_TOO_LARGE)
            AmountReading.Invalid -> put(index, StationError.INVALID_NUMBER)
        }
    }
}

/** Erster Fehler der Stromabrechnungs-Felder; nur bei [StationType.OVERNIGHT] geprüft, sonst immer `null`. */
private fun StationInput.electricityError(locale: Locale): StationError? {
    if (type != StationType.OVERNIGHT) return null
    return moneyFieldError(electricityFlatAmount, electricityCurrency, locale)
        ?: moneyFieldError(electricityBaseFee, electricityCurrency, locale)
        ?: decimalBoundError(electricityPricePerKwh, locale, PRICE_PER_KWH_FRACTION_DIGITS, MAX_PRICE_PER_KWH)
        ?: moneyFieldError(electricityCoinPrice, electricityCurrency, locale)
        ?: intBoundError(electricityCoinsUsed, MAX_ELECTRICITY_COINS)
        ?: decimalBoundError(electricityKwhPerCoin, locale, ELECTRICITY_KWH_FRACTION_DIGITS, MAX_ELECTRICITY_KWH)
        ?: decimalBoundError(electricityMeterStart, locale, ELECTRICITY_KWH_FRACTION_DIGITS, MAX_ELECTRICITY_KWH)
        ?: decimalBoundError(electricityMeterEnd, locale, ELECTRICITY_KWH_FRACTION_DIGITS, MAX_ELECTRICITY_KWH)
        ?: decimalBoundError(electricityKwhUsed, locale, ELECTRICITY_KWH_FRACTION_DIGITS, MAX_ELECTRICITY_KWH)
}

private fun moneyFieldError(text: String, currency: Currency, locale: Locale): StationError? {
    if (text.isBlank()) return null
    return when (readAmount(text, currency, locale)) {
        is AmountReading.Valid -> null
        AmountReading.TooLarge, AmountReading.Invalid -> StationError.INVALID_NUMBER
    }
}

private fun decimalBoundError(text: String, locale: Locale, fractionDigits: Int, max: Double): StationError? {
    if (text.isBlank()) return null
    val value = parseDecimal(text, locale, maxFractionDigits = fractionDigits) ?: return StationError.INVALID_NUMBER
    return if (value.signum() < 0 || value > BigDecimal.valueOf(max)) StationError.INVALID_NUMBER else null
}

private fun intBoundError(text: String, max: Int): StationError? {
    if (text.isBlank()) return null
    val value = text.trim().toIntOrNull() ?: return StationError.INVALID_NUMBER
    return if (value < 0 || value > max) StationError.INVALID_NUMBER else null
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [Station]. Vorher muss [validate] leer sein.
 * Typspezifische Felder und nicht erlaubte [StationService]-Werte werden hier anhand von
 * [StationInput.type] verworfen: ein Typwechsel lässt nicht passende Werte fallen. [costs] bleiben
 * unverändert von [original], da das Formular sie (noch) nicht bearbeitet. [mapLink] wird nur
 * übernommen, wenn keine Koordinaten gesetzt sind.
 *
 * @param original die bearbeitete Station oder `null` für eine neue Station
 * @param locale bestimmt, wie mehrdeutige Beträge und Dezimalzahlen der Stromabrechnung gelesen werden
 */
fun StationInput.toStation(original: Station?, locale: Locale = Locale.getDefault()): Station {
    val isOvernight = type == StationType.OVERNIGHT
    val isToll = type == StationType.TOLL
    val isFerry = type == StationType.FERRY
    val hasCoordinates = latitude != null && longitude != null
    val billing = if (isOvernight) electricityBilling else null
    return Station(
        id = original?.id ?: 0,
        uuid = original?.uuid.orEmpty(),
        vehicleId = vehicleId,
        tourId = tourId,
        type = type,
        date = checkNotNull(date) { "Datum muss vor dem Speichern validiert sein" },
        time = time,
        name = name.trim(),
        place = place.trim(),
        latitude = latitude,
        longitude = longitude,
        coordinateSource = coordinateSource.takeIf { hasCoordinates },
        accuracyM = accuracyM.takeIf { hasCoordinates },
        mapLink = mapLink?.trim()?.ifEmpty { null }?.takeIf { !hasCoordinates },
        notes = notes.trim(),
        nights = if (isOvernight) nights.trim().toIntOrNull()?.takeIf { it >= 1 } else null,
        siteKind = siteKind.takeIf { isOvernight },
        pitchAssigned = pitchAssigned.takeIf { isOvernight },
        lteQuality = lteQuality.takeIf { isOvernight },
        pitchSlope = pitchSlope.takeIf { isOvernight },
        levelingBlocksUsed = levelingBlocksUsed.takeIf { isOvernight },
        electricityBilling = billing,
        electricityCurrency = if (billing != null) electricityCurrency else null,
        electricityFlatAmount = if (billing != null) parseMoneyField(electricityFlatAmount, electricityCurrency, locale) else null,
        electricityBaseFee = if (billing != null) parseMoneyField(electricityBaseFee, electricityCurrency, locale) else null,
        electricityPricePerKwh = if (billing != null) {
            parseDecimalField(electricityPricePerKwh, locale, PRICE_PER_KWH_FRACTION_DIGITS)
        } else {
            null
        },
        electricityCoinPrice = if (billing != null) parseMoneyField(electricityCoinPrice, electricityCurrency, locale) else null,
        electricityCoinsUsed = if (billing != null) electricityCoinsUsed.trim().toIntOrNull() else null,
        electricityKwhPerCoin = if (billing != null) {
            parseDecimalField(electricityKwhPerCoin, locale, ELECTRICITY_KWH_FRACTION_DIGITS)
        } else {
            null
        },
        electricityMeterStart = if (billing != null) {
            parseDecimalField(electricityMeterStart, locale, ELECTRICITY_KWH_FRACTION_DIGITS)
        } else {
            null
        },
        electricityMeterEnd = if (billing != null) {
            parseDecimalField(electricityMeterEnd, locale, ELECTRICITY_KWH_FRACTION_DIGITS)
        } else {
            null
        },
        electricityKwhUsed = if (billing != null) {
            parseDecimalField(electricityKwhUsed, locale, ELECTRICITY_KWH_FRACTION_DIGITS)
        } else {
            null
        },
        tollKind = tollKind.takeIf { isToll },
        tollPaymentMethod = if (isToll) tollPaymentMethod.trim() else "",
        tollCountry = if (isToll) tollCountry.trim().uppercase(Locale.ROOT).ifEmpty { null } else null,
        tollValidFrom = tollValidFrom.takeIf { isToll },
        tollValidUntil = tollValidUntil.takeIf { isToll },
        ferryBookingReference = if (isFerry) ferryBookingReference.trim() else "",
        costs = costs
            .mapNotNull { line ->
                if (line.amount.isBlank()) return@mapNotNull null
                val minor = checkNotNull(parseAmount(line.amount, line.currency, locale)) {
                    "Kostenzeilen müssen vor dem Speichern validiert sein"
                }
                StationCost(line.category, Money(minor, line.currency), line.note.trim())
            }
            .groupBy { it.category to it.amount.currency }
            .map { (key, group) -> StationCost(key.first, Money(group.map { it.amount }.sumMinor(), key.second), group.first().note) }
            .filter { it.amount.minor != 0L },
        services = services.intersect(type.allowedServices),
        weather = weather,
        favorite = favorite && isOvernight,
        rating = rating?.takeIf { it in 1..5 },
        odometerKm = odometerKm.trim().toIntOrNull()?.takeIf { it >= 0 },
        manualTemperatureDeciC = manualTemperatureC.trim().toIntOrNull()?.let { it * 10 },
        link = link.trim().ifEmpty { null }?.takeIf { isOvernight },
        createdAt = original?.createdAt ?: Instant.EPOCH,
        updatedAt = original?.updatedAt ?: Instant.EPOCH,
    )
}

/** Wandelt eine gespeicherte Station in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun Station.toInput(locale: Locale = Locale.getDefault()): StationInput = StationInput(
    vehicleId = vehicleId,
    tourId = tourId,
    type = type,
    date = date,
    time = time,
    name = name,
    place = place,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource,
    accuracyM = accuracyM,
    mapLink = mapLink,
    notes = notes,
    nights = nights?.toString().orEmpty(),
    siteKind = siteKind,
    pitchAssigned = pitchAssigned,
    lteQuality = lteQuality,
    pitchSlope = pitchSlope,
    levelingBlocksUsed = levelingBlocksUsed,
    electricityBilling = electricityBilling,
    electricityCurrency = electricityCurrency ?: EUR,
    electricityFlatAmount = electricityFlatAmount.toInput(locale),
    electricityBaseFee = electricityBaseFee.toInput(locale),
    electricityPricePerKwh = decimalToInput(electricityPricePerKwh, locale),
    electricityCoinPrice = electricityCoinPrice.toInput(locale),
    electricityCoinsUsed = electricityCoinsUsed?.toString().orEmpty(),
    electricityKwhPerCoin = decimalToInput(electricityKwhPerCoin, locale),
    electricityMeterStart = decimalToInput(electricityMeterStart, locale),
    electricityMeterEnd = decimalToInput(electricityMeterEnd, locale),
    electricityKwhUsed = decimalToInput(electricityKwhUsed, locale),
    tollKind = tollKind,
    tollPaymentMethod = tollPaymentMethod,
    tollCountry = tollCountry.orEmpty(),
    tollValidFrom = tollValidFrom,
    tollValidUntil = tollValidUntil,
    ferryBookingReference = ferryBookingReference,
    costs = costs.map { StationCostInput(it.category, amountToInput(it.amount.minor, it.amount.currency, locale), it.amount.currency, it.note) },
    services = services,
    favorite = favorite,
    rating = rating,
    odometerKm = odometerKm?.toString().orEmpty(),
    manualTemperatureC = manualTemperatureDeciC?.let { Math.round(it / 10.0).toString() }.orEmpty(),
    link = link.orEmpty(),
    locationText = mapLink ?: if (latitude != null && longitude != null) "$latitude, $longitude" else "",
    weather = weather,
)

private fun parseMoneyField(text: String, currency: Currency, locale: Locale): Money? =
    if (text.isBlank()) null else parseAmount(text, currency, locale)?.let { Money(it, currency) }

private fun parseDecimalField(text: String, locale: Locale, maxFractionDigits: Int): BigDecimal? =
    if (text.isBlank()) null else parseDecimal(text, locale, maxFractionDigits)

private fun decimalToInput(value: BigDecimal?, locale: Locale): String =
    value?.toPlainString()?.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator).orEmpty()

/** Live-Vorschau der Stromabrechnung aus [StationInput.electricityPreview]: Kosten und kWh, soweit ermittelbar. */
data class ElectricityPreview(val cost: Money?, val kwh: BigDecimal?)

/**
 * Berechnet [ElectricityPreview] direkt aus den noch unvalidierten Formularfeldern, ohne die Station
 * zu speichern; ungültige Eingaben zählen dabei wie fehlende (kein Fehler, nur eine unvollständige
 * Vorschau). Nutzt dieselbe Kernlogik wie [electricityCost] und [electricityKwh].
 */
fun StationInput.electricityPreview(locale: Locale = Locale.getDefault()): ElectricityPreview {
    val billing = electricityBilling ?: return ElectricityPreview(null, null)
    val kwh = electricityKwhCore(
        meterStart = parseDecimalField(electricityMeterStart, locale, ELECTRICITY_KWH_FRACTION_DIGITS),
        meterEnd = parseDecimalField(electricityMeterEnd, locale, ELECTRICITY_KWH_FRACTION_DIGITS),
        kwhUsed = parseDecimalField(electricityKwhUsed, locale, ELECTRICITY_KWH_FRACTION_DIGITS),
        coinsUsed = electricityCoinsUsed.trim().toIntOrNull(),
        kwhPerCoin = parseDecimalField(electricityKwhPerCoin, locale, ELECTRICITY_KWH_FRACTION_DIGITS),
    )
    val cost = electricityCostCore(
        billing = billing,
        currency = electricityCurrency,
        flatAmount = parseMoneyField(electricityFlatAmount, electricityCurrency, locale),
        baseFee = parseMoneyField(electricityBaseFee, electricityCurrency, locale),
        pricePerKwh = parseDecimalField(electricityPricePerKwh, locale, PRICE_PER_KWH_FRACTION_DIGITS),
        coinPrice = parseMoneyField(electricityCoinPrice, electricityCurrency, locale),
        coinsUsed = electricityCoinsUsed.trim().toIntOrNull(),
        kwh = kwh,
        nights = nights.trim().toIntOrNull()?.takeIf { it >= 1 },
    )
    return ElectricityPreview(cost, kwh)
}

/**
 * Wechselt [StationInput.electricityBilling] und leert dabei die Felder, die zur neuen Abrechnungsart
 * nicht gehören, damit beim erneuten Wechsel keine veralteten Werte unbemerkt wieder greifen. Felder,
 * die die neue Art mit der alten teilt (z. B. der Zähler- bzw. kWh-Stand bei [ElectricityBilling.METERED]
 * und [ElectricityBilling.BASE_PLUS_METERED]), bleiben erhalten.
 */
fun StationInput.withElectricityBilling(billing: ElectricityBilling?): StationInput {
    if (billing == electricityBilling) return this
    val cleared = copy(
        electricityBilling = billing,
        electricityFlatAmount = "",
        electricityBaseFee = "",
        electricityPricePerKwh = "",
        electricityCoinPrice = "",
        electricityCoinsUsed = "",
        electricityKwhPerCoin = "",
        electricityMeterStart = "",
        electricityMeterEnd = "",
        electricityKwhUsed = "",
    )
    return when (billing) {
        null, ElectricityBilling.NONE, ElectricityBilling.INCLUDED -> cleared
        ElectricityBilling.FLAT_PER_NIGHT, ElectricityBilling.FLAT_PER_STAY -> cleared.copy(electricityFlatAmount = electricityFlatAmount)
        ElectricityBilling.METERED -> cleared.copy(
            electricityPricePerKwh = electricityPricePerKwh,
            electricityMeterStart = electricityMeterStart,
            electricityMeterEnd = electricityMeterEnd,
            electricityKwhUsed = electricityKwhUsed,
        )
        ElectricityBilling.BASE_PLUS_METERED -> cleared.copy(
            electricityBaseFee = electricityBaseFee,
            electricityPricePerKwh = electricityPricePerKwh,
            electricityMeterStart = electricityMeterStart,
            electricityMeterEnd = electricityMeterEnd,
            electricityKwhUsed = electricityKwhUsed,
        )
        ElectricityBilling.COIN -> cleared.copy(
            electricityCoinPrice = electricityCoinPrice,
            electricityCoinsUsed = electricityCoinsUsed,
            electricityKwhPerCoin = electricityKwhPerCoin,
        )
    }
}
