package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Höchstwerte der Plausibilitätsprüfung, jeweils in der Einheit des Formularfelds. */
const val MAX_DIMENSION_CM = 3_000
const val MAX_WEIGHT_KG = 100_000
const val MAX_POWER_KW = 2_000
const val MAX_TIRE_PRESSURE_BAR = 15.0
const val MAX_TANK_L = 10_000.0
const val MAX_BATTERY_AH = 100_000
const val MAX_SOLAR_WP = 100_000
const val MAX_ODOMETER_KM = 10_000_000
const val MAX_PHONE_LENGTH = 30

private val PHONE_PATTERN = Regex("""[0-9 +\-/()]+""")

/** Ob [text] nur Ziffern, Leerzeichen und die Zeichen `+ - / ( )` enthält und höchstens [MAX_PHONE_LENGTH] lang ist. */
fun isValidPhone(text: String): Boolean = text.length <= MAX_PHONE_LENGTH && PHONE_PATTERN.matches(text)

/**
 * Unvalidierte Eingaben des Fahrzeugformulars. Zahlen und Beträge liegen als Text im Zahlenformat
 * der Oberfläche vor.
 */
@Serializable
data class VehicleInput(
    val name: String = "",
    val licensePlate: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val vin: String = "",
    @Serializable(with = LocalDateSerializer::class) val firstRegistration: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val purchaseDate: LocalDate? = null,
    val purchasePrice: String = "",
    @Serializable(with = CurrencySerializer::class) val purchasePriceCurrency: Currency = EUR,
    val purchaseOdometerKm: String = "",
    @Serializable(with = LocalDateSerializer::class) val saleDate: LocalDate? = null,
    val salePrice: String = "",
    @Serializable(with = CurrencySerializer::class) val salePriceCurrency: Currency = EUR,
    val insurer: String = "",
    val insurancePolicyNumber: String = "",
    val insurancePremiumPerYear: String = "",
    @Serializable(with = CurrencySerializer::class) val insurancePremiumPerYearCurrency: Currency = EUR,
    val vehicleTaxPerYear: String = "",
    @Serializable(with = CurrencySerializer::class) val vehicleTaxPerYearCurrency: Currency = EUR,
    val lengthCm: String = "",
    val widthCm: String = "",
    val heightCm: String = "",
    val grossWeightKg: String = "",
    val measuredEmptyWeightKg: String = "",
    val breakdownProvider: String = "",
    val breakdownMembershipNumber: String = "",
    val breakdownPhone: String = "",
    val travelProtectionProvider: String = "",
    val travelProtectionContractNumber: String = "",
    val travelProtectionPhone: String = "",
    val insurerClaimsPhone: String = "",
    val powerKw: String = "",
    val tireSize: String = "",
    val tirePressureFrontBar: String = "",
    val tirePressureRearBar: String = "",
    val requiredEnergyTypes: Set<EnergyType> = emptySet(),
    val fuelTankL: String = "",
    val adBlueTankL: String = "",
    val freshWaterTankL: String = "",
    val greyWaterTankL: String = "",
    val boilerL: String = "",
    val cassetteL: String = "",
    val batteryCapacityAh: String = "",
    val solarPowerWp: String = "",
    @Serializable(with = LocalDateSerializer::class) val nextInspectionDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val nextGasCheckDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val nextLeakTestDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val lastOilChangeDate: LocalDate? = null,
    val lastOilChangeOdometerKm: String = "",
    val notes: String = "",
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class VehicleField {
    NAME,
    FIRST_REGISTRATION,
    PURCHASE_PRICE,
    PURCHASE_ODOMETER_KM,
    SALE_DATE,
    SALE_PRICE,
    INSURANCE_PREMIUM_PER_YEAR,
    VEHICLE_TAX_PER_YEAR,
    LENGTH,
    WIDTH,
    HEIGHT,
    GROSS_WEIGHT_KG,
    MEASURED_EMPTY_WEIGHT_KG,
    BREAKDOWN_PHONE,
    TRAVEL_PROTECTION_PHONE,
    INSURER_CLAIMS_PHONE,
    POWER_KW,
    TIRE_PRESSURE_FRONT,
    TIRE_PRESSURE_REAR,
    FUEL_TANK,
    AD_BLUE_TANK,
    FRESH_WATER_TANK,
    GREY_WATER_TANK,
    BOILER,
    CASSETTE,
    BATTERY_CAPACITY_AH,
    SOLAR_POWER_WP,
    LAST_OIL_CHANGE_DATE,
    LAST_OIL_CHANGE_ODOMETER_KM,
}

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class VehicleError {
    REQUIRED,
    NOT_POSITIVE,
    NEGATIVE_NUMBER,
    INVALID_NUMBER,
    TOO_LARGE,
    INVALID_AMOUNT,
    AMOUNT_TOO_LARGE,
    DATE_IN_FUTURE,
    SALE_BEFORE_PURCHASE,
    INVALID_PHONE,
}

/**
 * Prüft alle fachlichen Regeln einer Fahrzeug-Eingabe.
 *
 * @param locale bestimmt, wie mehrdeutige Zahlen und Beträge gelesen werden
 * @param isNew ob ein neues Fahrzeug angelegt wird; nur dann ist der Name Pflicht
 * @param today Bezugstag für die Prüfung der Erstzulassung
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun VehicleInput.validate(locale: Locale, isNew: Boolean, today: LocalDate = LocalDate.now()): Map<VehicleField, VehicleError> =
    buildMap {
        if (isNew && name.isBlank()) put(VehicleField.NAME, VehicleError.REQUIRED)
        if (firstRegistration != null && firstRegistration.isAfter(today)) {
            put(VehicleField.FIRST_REGISTRATION, VehicleError.DATE_IN_FUTURE)
        }
        if (saleDate != null && purchaseDate != null && saleDate.isBefore(purchaseDate)) {
            put(VehicleField.SALE_DATE, VehicleError.SALE_BEFORE_PURCHASE)
        }
        amountError(purchasePrice, purchasePriceCurrency, locale)?.let { put(VehicleField.PURCHASE_PRICE, it) }
        amountError(salePrice, salePriceCurrency, locale)?.let { put(VehicleField.SALE_PRICE, it) }
        amountError(insurancePremiumPerYear, insurancePremiumPerYearCurrency, locale)
            ?.let { put(VehicleField.INSURANCE_PREMIUM_PER_YEAR, it) }
        amountError(vehicleTaxPerYear, vehicleTaxPerYearCurrency, locale)?.let { put(VehicleField.VEHICLE_TAX_PER_YEAR, it) }
        intError(purchaseOdometerKm, MAX_ODOMETER_KM)?.let { put(VehicleField.PURCHASE_ODOMETER_KM, it) }
        positiveIntError(lengthCm, MAX_DIMENSION_CM)?.let { put(VehicleField.LENGTH, it) }
        positiveIntError(widthCm, MAX_DIMENSION_CM)?.let { put(VehicleField.WIDTH, it) }
        positiveIntError(heightCm, MAX_DIMENSION_CM)?.let { put(VehicleField.HEIGHT, it) }
        intError(grossWeightKg, MAX_WEIGHT_KG)?.let { put(VehicleField.GROSS_WEIGHT_KG, it) }
        intError(measuredEmptyWeightKg, MAX_WEIGHT_KG)?.let { put(VehicleField.MEASURED_EMPTY_WEIGHT_KG, it) }
        phoneError(breakdownPhone)?.let { put(VehicleField.BREAKDOWN_PHONE, it) }
        phoneError(travelProtectionPhone)?.let { put(VehicleField.TRAVEL_PROTECTION_PHONE, it) }
        phoneError(insurerClaimsPhone)?.let { put(VehicleField.INSURER_CLAIMS_PHONE, it) }
        intError(powerKw, MAX_POWER_KW)?.let { put(VehicleField.POWER_KW, it) }
        decimalError(tirePressureFrontBar, locale, 2, MAX_TIRE_PRESSURE_BAR)?.let { put(VehicleField.TIRE_PRESSURE_FRONT, it) }
        decimalError(tirePressureRearBar, locale, 2, MAX_TIRE_PRESSURE_BAR)?.let { put(VehicleField.TIRE_PRESSURE_REAR, it) }
        decimalError(fuelTankL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.FUEL_TANK, it) }
        decimalError(adBlueTankL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.AD_BLUE_TANK, it) }
        decimalError(freshWaterTankL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.FRESH_WATER_TANK, it) }
        decimalError(greyWaterTankL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.GREY_WATER_TANK, it) }
        decimalError(boilerL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.BOILER, it) }
        decimalError(cassetteL, locale, 1, MAX_TANK_L)?.let { put(VehicleField.CASSETTE, it) }
        intError(batteryCapacityAh, MAX_BATTERY_AH)?.let { put(VehicleField.BATTERY_CAPACITY_AH, it) }
        intError(solarPowerWp, MAX_SOLAR_WP)?.let { put(VehicleField.SOLAR_POWER_WP, it) }
        if (lastOilChangeDate != null && lastOilChangeDate.isAfter(today)) {
            put(VehicleField.LAST_OIL_CHANGE_DATE, VehicleError.DATE_IN_FUTURE)
        }
        intError(lastOilChangeOdometerKm, MAX_ODOMETER_KM)?.let { put(VehicleField.LAST_OIL_CHANGE_ODOMETER_KM, it) }
    }

/**
 * Erzeugt aus einer gültigen Eingabe ein [Vehicle]. Vorher muss [validate] leer sein.
 *
 * @param original das bearbeitete Fahrzeug oder `null` für ein neues Fahrzeug
 * @param locale dieselbe Sprache wie bei [validate]
 * @return Fahrzeug mit id und Zeitstempeln von [original]
 */
fun VehicleInput.toVehicle(original: Vehicle?, locale: Locale): Vehicle = Vehicle(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    name = name.trim(),
    licensePlate = licensePlate.trim(),
    manufacturer = manufacturer.trim(),
    model = model.trim(),
    vin = vin.trim(),
    firstRegistration = firstRegistration,
    notes = notes.trim(),
    purchaseDate = purchaseDate,
    purchasePrice = parseMoney(purchasePrice, purchasePriceCurrency, locale),
    purchaseOdometerKm = parseOptionalInt(purchaseOdometerKm),
    saleDate = saleDate,
    salePrice = parseMoney(salePrice, salePriceCurrency, locale),
    insurer = insurer.trim(),
    insurancePolicyNumber = insurancePolicyNumber.trim(),
    insurancePremiumPerYear = parseMoney(insurancePremiumPerYear, insurancePremiumPerYearCurrency, locale),
    vehicleTaxPerYear = parseMoney(vehicleTaxPerYear, vehicleTaxPerYearCurrency, locale),
    lengthCm = parseOptionalInt(lengthCm),
    widthCm = parseOptionalInt(widthCm),
    heightCm = parseOptionalInt(heightCm),
    grossWeightKg = parseOptionalInt(grossWeightKg),
    measuredEmptyWeightKg = parseOptionalInt(measuredEmptyWeightKg),
    breakdownProvider = breakdownProvider.trim(),
    breakdownMembershipNumber = breakdownMembershipNumber.trim(),
    breakdownPhone = breakdownPhone.trim(),
    travelProtectionProvider = travelProtectionProvider.trim(),
    travelProtectionContractNumber = travelProtectionContractNumber.trim(),
    travelProtectionPhone = travelProtectionPhone.trim(),
    insurerClaimsPhone = insurerClaimsPhone.trim(),
    powerKw = parseOptionalInt(powerKw),
    tireSize = tireSize.trim(),
    tirePressureFrontMbar = parseScaledDecimal(tirePressureFrontBar, locale, uiFractionDigits = 2, storageExponent = 3),
    tirePressureRearMbar = parseScaledDecimal(tirePressureRearBar, locale, uiFractionDigits = 2, storageExponent = 3),
    requiredEnergyTypes = requiredEnergyTypes,
    fuelTankDl = parseScaledDecimal(fuelTankL, locale, uiFractionDigits = 1, storageExponent = 1),
    adBlueTankDl = parseScaledDecimal(adBlueTankL, locale, uiFractionDigits = 1, storageExponent = 1),
    freshWaterTankDl = parseScaledDecimal(freshWaterTankL, locale, uiFractionDigits = 1, storageExponent = 1),
    greyWaterTankDl = parseScaledDecimal(greyWaterTankL, locale, uiFractionDigits = 1, storageExponent = 1),
    boilerDl = parseScaledDecimal(boilerL, locale, uiFractionDigits = 1, storageExponent = 1),
    cassetteDl = parseScaledDecimal(cassetteL, locale, uiFractionDigits = 1, storageExponent = 1),
    batteryCapacityAh = parseOptionalInt(batteryCapacityAh),
    solarPowerWp = parseOptionalInt(solarPowerWp),
    nextInspectionDate = nextInspectionDate,
    nextGasCheckDate = nextGasCheckDate,
    nextLeakTestDate = nextLeakTestDate,
    lastOilChangeDate = lastOilChangeDate,
    lastOilChangeOdometerKm = parseOptionalInt(lastOilChangeOdometerKm),
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt ein gespeichertes Fahrzeug in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun Vehicle.toInput(locale: Locale): VehicleInput = VehicleInput(
    name = name,
    licensePlate = licensePlate,
    manufacturer = manufacturer,
    model = model,
    vin = vin,
    firstRegistration = firstRegistration,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice.toInput(locale),
    purchasePriceCurrency = purchasePrice?.currency ?: EUR,
    purchaseOdometerKm = purchaseOdometerKm?.toString().orEmpty(),
    saleDate = saleDate,
    salePrice = salePrice.toInput(locale),
    salePriceCurrency = salePrice?.currency ?: EUR,
    insurer = insurer,
    insurancePolicyNumber = insurancePolicyNumber,
    insurancePremiumPerYear = insurancePremiumPerYear.toInput(locale),
    insurancePremiumPerYearCurrency = insurancePremiumPerYear?.currency ?: EUR,
    vehicleTaxPerYear = vehicleTaxPerYear.toInput(locale),
    vehicleTaxPerYearCurrency = vehicleTaxPerYear?.currency ?: EUR,
    lengthCm = lengthCm?.toString().orEmpty(),
    widthCm = widthCm?.toString().orEmpty(),
    heightCm = heightCm?.toString().orEmpty(),
    grossWeightKg = grossWeightKg?.toString().orEmpty(),
    measuredEmptyWeightKg = measuredEmptyWeightKg?.toString().orEmpty(),
    breakdownProvider = breakdownProvider,
    breakdownMembershipNumber = breakdownMembershipNumber,
    breakdownPhone = breakdownPhone,
    travelProtectionProvider = travelProtectionProvider,
    travelProtectionContractNumber = travelProtectionContractNumber,
    travelProtectionPhone = travelProtectionPhone,
    insurerClaimsPhone = insurerClaimsPhone,
    powerKw = powerKw?.toString().orEmpty(),
    tireSize = tireSize,
    tirePressureFrontBar = scaledToInput(tirePressureFrontMbar, locale, uiFractionDigits = 2, storageExponent = 3),
    tirePressureRearBar = scaledToInput(tirePressureRearMbar, locale, uiFractionDigits = 2, storageExponent = 3),
    requiredEnergyTypes = requiredEnergyTypes,
    fuelTankL = scaledToInput(fuelTankDl, locale, uiFractionDigits = 1, storageExponent = 1),
    adBlueTankL = scaledToInput(adBlueTankDl, locale, uiFractionDigits = 1, storageExponent = 1),
    freshWaterTankL = scaledToInput(freshWaterTankDl, locale, uiFractionDigits = 1, storageExponent = 1),
    greyWaterTankL = scaledToInput(greyWaterTankDl, locale, uiFractionDigits = 1, storageExponent = 1),
    boilerL = scaledToInput(boilerDl, locale, uiFractionDigits = 1, storageExponent = 1),
    cassetteL = scaledToInput(cassetteDl, locale, uiFractionDigits = 1, storageExponent = 1),
    batteryCapacityAh = batteryCapacityAh?.toString().orEmpty(),
    solarPowerWp = solarPowerWp?.toString().orEmpty(),
    nextInspectionDate = nextInspectionDate,
    nextGasCheckDate = nextGasCheckDate,
    nextLeakTestDate = nextLeakTestDate,
    lastOilChangeDate = lastOilChangeDate,
    lastOilChangeOdometerKm = lastOilChangeOdometerKm?.toString().orEmpty(),
    notes = notes,
)

internal fun Money?.toInput(locale: Locale): String = this?.let { amountToInput(it.minor, it.currency, locale) }.orEmpty()

internal fun parseMoney(text: String, currency: Currency, locale: Locale): Money? =
    if (text.isBlank()) null else parseAmount(text, currency, locale)?.let { Money(it, currency) }

internal fun parseOptionalInt(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

/**
 * Wandelt einen Dezimalwert der Oberfläche (mit [uiFractionDigits] Nachkommastellen) in die
 * gespeicherte kleinste Einheit um, z. B. Liter (1 Nachkommastelle) in Deziliter ([storageExponent] 1).
 */
private fun parseScaledDecimal(text: String, locale: Locale, uiFractionDigits: Int, storageExponent: Int): Int? {
    if (text.isBlank()) return null
    val value = parseDecimal(text, locale, maxFractionDigits = uiFractionDigits) ?: return null
    return runCatching { value.movePointRight(storageExponent).intValueExact() }.getOrNull()
}

/** Kehrt [parseScaledDecimal] um; rundet auf [uiFractionDigits] Nachkommastellen. */
private fun scaledToInput(stored: Int?, locale: Locale, uiFractionDigits: Int, storageExponent: Int): String {
    if (stored == null) return ""
    val value = BigDecimal.valueOf(stored.toLong(), storageExponent).setScale(uiFractionDigits, java.math.RoundingMode.HALF_UP)
    return amountToDecimalString(value, locale)
}

private fun amountToDecimalString(value: BigDecimal, locale: Locale): String =
    value.toPlainString().replace('.', java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator)

private fun intError(text: String, max: Int): VehicleError? {
    if (text.isBlank()) return null
    val value = text.trim().toIntOrNull() ?: return VehicleError.INVALID_NUMBER
    return when {
        value < 0 -> VehicleError.NEGATIVE_NUMBER
        value > max -> VehicleError.TOO_LARGE
        else -> null
    }
}

private fun decimalError(text: String, locale: Locale, fractionDigits: Int, max: Double): VehicleError? {
    if (text.isBlank()) return null
    val value = parseDecimal(text, locale, maxFractionDigits = fractionDigits) ?: return VehicleError.INVALID_NUMBER
    return if (value > BigDecimal.valueOf(max)) VehicleError.TOO_LARGE else null
}

private fun positiveIntError(text: String, max: Int): VehicleError? {
    if (text.isBlank()) return null
    val value = text.trim().toIntOrNull() ?: return VehicleError.INVALID_NUMBER
    return when {
        value <= 0 -> VehicleError.NOT_POSITIVE
        value > max -> VehicleError.TOO_LARGE
        else -> null
    }
}

private fun phoneError(text: String): VehicleError? =
    if (text.isBlank() || isValidPhone(text.trim())) null else VehicleError.INVALID_PHONE

private fun amountError(text: String, currency: Currency, locale: Locale): VehicleError? {
    if (text.isBlank()) return null
    return when (readAmount(text, currency, locale)) {
        is AmountReading.Valid -> null
        AmountReading.TooLarge -> VehicleError.AMOUNT_TOO_LARGE
        AmountReading.Invalid -> VehicleError.INVALID_AMOUNT
    }
}
