package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

/** Höchstlänge des Namens eines Beladungsgegenstands. */
const val MAX_LOAD_ITEM_NAME_LENGTH = 200

/** Höchstlänge des Namens eines Beladungsprofils. */
const val MAX_LOAD_PROFILE_NAME_LENGTH = 200

/** Höchstmasse eines einzelnen Beladungsgegenstands in kg. */
const val MAX_LOAD_ITEM_MASS_KG = 10_000

/** Höchstanzahl Stücke eines Beladungsgegenstands. */
const val MAX_LOAD_ITEM_QUANTITY = 999

/** Kleinster zulässiger Abstand von der Vorderachse in cm (deutlich vor dem Fahrzeug). */
const val MIN_LOAD_POSITION_CM = -500

/** Größter zulässiger Abstand von der Vorderachse in cm (deutlich hinter dem Fahrzeug). */
const val MAX_LOAD_POSITION_CM = 2500

/** Kleinste zulässige Dichte eines Tankinhalts in g/l. */
const val MIN_LOAD_TANK_DENSITY_GRAMS_PER_LITRE = 500

/** Größte zulässige Dichte eines Tankinhalts in g/l. */
const val MAX_LOAD_TANK_DENSITY_GRAMS_PER_LITRE = 1_500

/** Ob [category] bei einem neuen Gegenstand standardmäßig als bei der Wiegung vorhanden gilt. */
fun defaultInWeighing(category: LoadCategory): Boolean = category == LoadCategory.FIXTURE

/** Unvalidierte Eingaben des Formulars für einen Beladungsgegenstand. */
@Serializable
data class LoadItemInput(
    val name: String = "",
    val category: LoadCategory = LoadCategory.EQUIPMENT,
    val massKg: String = "",
    val quantity: String = "1",
    val positionCm: String = "",
    val inWeighing: Boolean = false,
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class LoadItemField { NAME, MASS, QUANTITY, POSITION }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class LoadItemError { REQUIRED, TOO_LONG, INVALID_NUMBER, NOT_POSITIVE, TOO_LARGE, OUT_OF_RANGE }

/**
 * Prüft alle fachlichen Regeln einer Gegenstands-Eingabe.
 *
 * @param locale bestimmt, wie die Masse gelesen wird
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun LoadItemInput.validate(locale: Locale): Map<LoadItemField, LoadItemError> = buildMap {
    when {
        name.isBlank() -> put(LoadItemField.NAME, LoadItemError.REQUIRED)
        name.trim().length > MAX_LOAD_ITEM_NAME_LENGTH -> put(LoadItemField.NAME, LoadItemError.TOO_LONG)
    }
    val mass = parseDecimal(massKg, locale, maxFractionDigits = LOAD_MASS_FRACTION_DIGITS)
    when {
        massKg.isBlank() || mass == null -> put(LoadItemField.MASS, LoadItemError.INVALID_NUMBER)
        mass.signum() <= 0 -> put(LoadItemField.MASS, LoadItemError.NOT_POSITIVE)
        mass > BigDecimal.valueOf(MAX_LOAD_ITEM_MASS_KG.toLong()) -> put(LoadItemField.MASS, LoadItemError.TOO_LARGE)
    }
    val quantityValue = quantity.trim().toIntOrNull()
    when {
        quantityValue == null -> put(LoadItemField.QUANTITY, LoadItemError.INVALID_NUMBER)
        quantityValue < 1 -> put(LoadItemField.QUANTITY, LoadItemError.NOT_POSITIVE)
        quantityValue > MAX_LOAD_ITEM_QUANTITY -> put(LoadItemField.QUANTITY, LoadItemError.TOO_LARGE)
    }
    if (positionCm.isNotBlank()) {
        val positionValue = positionCm.trim().toIntOrNull()
        when {
            positionValue == null -> put(LoadItemField.POSITION, LoadItemError.INVALID_NUMBER)
            positionValue < MIN_LOAD_POSITION_CM || positionValue > MAX_LOAD_POSITION_CM ->
                put(LoadItemField.POSITION, LoadItemError.OUT_OF_RANGE)
        }
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe einen [LoadItem]. Vorher muss [validate] leer sein.
 *
 * @param original der bearbeitete Gegenstand oder `null` für einen neuen Gegenstand
 * @param vehicleId das Fahrzeug, zu dem der Gegenstand gehört
 * @param locale dieselbe Sprache wie bei [validate]
 */
fun LoadItemInput.toItem(original: LoadItem?, vehicleId: Long, locale: Locale): LoadItem = LoadItem(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    vehicleId = vehicleId,
    name = name.trim(),
    category = category,
    massGrams = checkNotNull(parseMassGrams(massKg, locale)) { "Masse muss vor dem Speichern validiert sein" },
    quantity = checkNotNull(quantity.trim().toIntOrNull()) { "Anzahl muss vor dem Speichern validiert sein" },
    positionCm = positionCm.trim().toIntOrNull(),
    inWeighing = inWeighing,
    sortOrder = original?.sortOrder ?: 0,
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt einen gespeicherten Gegenstand in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun LoadItem.toInput(locale: Locale): LoadItemInput = LoadItemInput(
    name = name,
    category = category,
    massKg = massGramsToInput(massGrams, locale),
    quantity = quantity.toString(),
    positionCm = positionCm?.toString().orEmpty(),
    inWeighing = inWeighing,
)

/** Unvalidierte Eingaben des Formulars für ein Beladungsprofil. */
@Serializable
data class LoadProfileInput(val name: String = "")

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class LoadProfileField { NAME }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class LoadProfileError { REQUIRED, TOO_LONG }

/** Prüft die fachlichen Regeln einer Profil-Eingabe: der Name ist Pflicht und begrenzt lang. */
fun LoadProfileInput.validate(): Map<LoadProfileField, LoadProfileError> = buildMap {
    when {
        name.isBlank() -> put(LoadProfileField.NAME, LoadProfileError.REQUIRED)
        name.trim().length > MAX_LOAD_PROFILE_NAME_LENGTH -> put(LoadProfileField.NAME, LoadProfileError.TOO_LONG)
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe ein [LoadProfile]. Vorher muss [validate] leer sein.
 *
 * @param original das bearbeitete Profil oder `null` für ein neues Profil
 * @param vehicleId das Fahrzeug, zu dem das Profil gehört
 */
fun LoadProfileInput.toProfile(original: LoadProfile?, vehicleId: Long): LoadProfile = LoadProfile(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    vehicleId = vehicleId,
    name = name.trim(),
    sortOrder = original?.sortOrder ?: 0,
)

/** Wandelt ein gespeichertes Profil in editierbare Formulardaten um. */
fun LoadProfile.toInput(): LoadProfileInput = LoadProfileInput(name = name)

/** Unvalidierte Eingaben des Formulars für die Einstellungen eines Tanks. */
@Serializable
data class LoadTankSettingsInput(
    val positionCm: String = "",
    val densityGramsPerLitre: String = "",
    val weighingLevelL: String = "",
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class LoadTankSettingsField { POSITION, DENSITY, WEIGHING_LEVEL }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class LoadTankSettingsError { INVALID_NUMBER, OUT_OF_RANGE, TOO_LARGE, NEGATIVE_NUMBER }

/**
 * Prüft alle fachlichen Regeln einer Tankeinstellungs-Eingabe.
 *
 * @param capacityDl Kapazität des Tanks in dl, Obergrenze des Füllstands
 * @param locale bestimmt, wie der Füllstand gelesen wird
 */
fun LoadTankSettingsInput.validate(capacityDl: Int, locale: Locale): Map<LoadTankSettingsField, LoadTankSettingsError> = buildMap {
    if (positionCm.isNotBlank()) {
        val positionValue = positionCm.trim().toIntOrNull()
        when {
            positionValue == null -> put(LoadTankSettingsField.POSITION, LoadTankSettingsError.INVALID_NUMBER)
            positionValue < MIN_LOAD_POSITION_CM || positionValue > MAX_LOAD_POSITION_CM ->
                put(LoadTankSettingsField.POSITION, LoadTankSettingsError.OUT_OF_RANGE)
        }
    }
    val densityValue = densityGramsPerLitre.trim().toIntOrNull()
    when {
        densityGramsPerLitre.isBlank() || densityValue == null -> put(LoadTankSettingsField.DENSITY, LoadTankSettingsError.INVALID_NUMBER)
        densityValue < MIN_LOAD_TANK_DENSITY_GRAMS_PER_LITRE || densityValue > MAX_LOAD_TANK_DENSITY_GRAMS_PER_LITRE ->
            put(LoadTankSettingsField.DENSITY, LoadTankSettingsError.OUT_OF_RANGE)
    }
    val levelDl = parseScaledLitres(weighingLevelL, locale)
    when {
        weighingLevelL.isBlank() || levelDl == null -> put(LoadTankSettingsField.WEIGHING_LEVEL, LoadTankSettingsError.INVALID_NUMBER)
        levelDl < 0 -> put(LoadTankSettingsField.WEIGHING_LEVEL, LoadTankSettingsError.NEGATIVE_NUMBER)
        levelDl > capacityDl -> put(LoadTankSettingsField.WEIGHING_LEVEL, LoadTankSettingsError.TOO_LARGE)
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe die Einstellungen eines Tanks. Vorher muss [validate] leer sein.
 *
 * @param vehicleId das Fahrzeug, zu dem der Tank gehört
 * @param kind die Tankart
 * @param locale dieselbe Sprache wie bei [validate]
 */
fun LoadTankSettingsInput.toSettings(vehicleId: Long, kind: LoadTankKind, locale: Locale): LoadTankSettings = LoadTankSettings(
    vehicleId = vehicleId,
    kind = kind,
    positionCm = positionCm.trim().toIntOrNull(),
    densityGramsPerLitre = checkNotNull(densityGramsPerLitre.trim().toIntOrNull()) { "Dichte muss vor dem Speichern validiert sein" },
    weighingLevelDl = checkNotNull(parseScaledLitres(weighingLevelL, locale)) { "Füllstand muss vor dem Speichern validiert sein" },
)

/** Wandelt gespeicherte Tankeinstellungen in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun LoadTankSettings.toInput(locale: Locale): LoadTankSettingsInput = LoadTankSettingsInput(
    positionCm = positionCm?.toString().orEmpty(),
    densityGramsPerLitre = densityGramsPerLitre.toString(),
    weighingLevelL = scaledLitresToInput(weighingLevelDl, locale),
)

private const val LOAD_MASS_FRACTION_DIGITS = 3
private const val LOAD_TANK_LEVEL_FRACTION_DIGITS = 1

private fun parseMassGrams(text: String, locale: Locale): Long? {
    if (text.isBlank()) return null
    val value = parseDecimal(text, locale, maxFractionDigits = LOAD_MASS_FRACTION_DIGITS) ?: return null
    return runCatching { value.movePointRight(LOAD_MASS_FRACTION_DIGITS).longValueExact() }.getOrNull()
}

private fun massGramsToInput(massGrams: Long, locale: Locale): String {
    val value = BigDecimal.valueOf(massGrams, LOAD_MASS_FRACTION_DIGITS).stripTrailingZeros()
    return value.toPlainString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}

private fun parseScaledLitres(text: String, locale: Locale): Int? {
    if (text.isBlank()) return null
    val value = parseDecimal(text, locale, maxFractionDigits = LOAD_TANK_LEVEL_FRACTION_DIGITS) ?: return null
    return runCatching { value.movePointRight(LOAD_TANK_LEVEL_FRACTION_DIGITS).intValueExact() }.getOrNull()
}

private fun scaledLitresToInput(levelDl: Int, locale: Locale): String {
    val value = BigDecimal.valueOf(levelDl.toLong(), LOAD_TANK_LEVEL_FRACTION_DIGITS)
    return value.toPlainString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}
