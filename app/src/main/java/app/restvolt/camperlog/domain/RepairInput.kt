package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Unvalidierte Eingaben des Reparaturformulars. Zahl und Betrag liegen als Text vor. */
@Serializable
data class RepairInput(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate? = null,
    val description: String = "",
    val odometerKm: String = "",
    val cost: String = "",
    @Serializable(with = CurrencySerializer::class) val costCurrency: Currency = EUR,
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class RepairField { DATE, DESCRIPTION, ODOMETER_KM, COST }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class RepairError { REQUIRED, NEGATIVE_NUMBER, INVALID_NUMBER, TOO_LARGE, INVALID_AMOUNT, AMOUNT_TOO_LARGE }

/**
 * Prüft alle fachlichen Regeln einer Reparatur-Eingabe.
 *
 * @param locale bestimmt, wie mehrdeutige Zahlen und Beträge gelesen werden
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun RepairInput.validate(locale: Locale): Map<RepairField, RepairError> = buildMap {
    if (date == null) put(RepairField.DATE, RepairError.REQUIRED)
    if (description.isBlank()) put(RepairField.DESCRIPTION, RepairError.REQUIRED)
    if (odometerKm.isNotBlank()) {
        val value = odometerKm.trim().toIntOrNull()
        when {
            value == null -> put(RepairField.ODOMETER_KM, RepairError.INVALID_NUMBER)
            value < 0 -> put(RepairField.ODOMETER_KM, RepairError.NEGATIVE_NUMBER)
            value > MAX_ODOMETER_KM -> put(RepairField.ODOMETER_KM, RepairError.TOO_LARGE)
        }
    }
    if (cost.isNotBlank()) {
        when (readAmount(cost, costCurrency, locale)) {
            is AmountReading.Valid -> Unit
            AmountReading.TooLarge -> put(RepairField.COST, RepairError.AMOUNT_TOO_LARGE)
            AmountReading.Invalid -> put(RepairField.COST, RepairError.INVALID_AMOUNT)
        }
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [Repair]. Vorher muss [validate] leer sein.
 *
 * @param original die bearbeitete Reparatur oder `null` für eine neue Reparatur
 * @param vehicleId das Fahrzeug, zu dem die Reparatur gehört
 * @param locale dieselbe Sprache wie bei [validate]
 */
fun RepairInput.toRepair(original: Repair?, vehicleId: Long, locale: Locale): Repair = Repair(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    vehicleId = vehicleId,
    date = checkNotNull(date) { "Datum muss vor dem Speichern validiert sein" },
    description = description.trim(),
    odometerKm = parseOptionalInt(odometerKm),
    cost = parseMoney(cost, costCurrency, locale),
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt eine gespeicherte Reparatur in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun Repair.toInput(locale: Locale): RepairInput = RepairInput(
    date = date,
    description = description,
    odometerKm = odometerKm?.toString().orEmpty(),
    cost = cost.toInput(locale),
    costCurrency = cost?.currency ?: EUR,
)
