package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** Unvalidierte Eingaben des Tour-Formulars. Zahlen und Beträge liegen als Text vor. */
@Serializable
data class TourInput(
    /** 0 bedeutet beim Speichern „aktuelles Fahrzeug" (das Repository löst das auf). */
    val vehicleId: Long = 0,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate? = null,
    val destination: String = "",
    val tourType: TourType = TourType.WEEKEND,
    val travelDays: String = "",
    val overnightStays: String = "",
    val distanceKm: String = "",
    val costs: List<CostInput> = listOf(CostInput()),
    val notes: String = "",
    val mapLink: String = "",
)

/** Eine Kostenzeile des Formulars: Betrag als Text in [currency]. */
@Serializable
data class CostInput(
    val amount: String = "",
    @Serializable(with = CurrencySerializer::class) val currency: Currency = EUR,
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class TourField { START_DATE, END_DATE, DESTINATION, TRAVEL_DAYS, OVERNIGHT_STAYS, DISTANCE_KM, COST, MAP_LINK }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class TourError {
    REQUIRED,
    END_BEFORE_START,
    NEGATIVE_NUMBER,
    INVALID_NUMBER,
    INVALID_AMOUNT,
    AMOUNT_TOO_LARGE,
    MORE_NIGHTS_THAN_DAYS,
    NOT_A_WEB_LINK,
}

/**
 * Prüft alle fachlichen Regeln einer Tour-Eingabe.
 *
 * @param locale bestimmt, wie mehrdeutige Beträge wie `1.234` gelesen werden
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun TourInput.validate(locale: Locale): Map<TourField, TourError> = validation(locale).errors

internal data class TourValidation(
    val errors: Map<TourField, TourError>,
    val costErrors: Map<Int, TourError>,
)

internal fun TourInput.validation(locale: Locale): TourValidation {
    val costErrors = costErrors(locale)
    val errors = buildMap {
        if (startDate == null) put(TourField.START_DATE, TourError.REQUIRED)
        if (startDate != null && endDate != null && endDate < startDate) {
            put(TourField.END_DATE, TourError.END_BEFORE_START)
        }
        if (destination.isBlank()) put(TourField.DESTINATION, TourError.REQUIRED)
        countError(travelDays)?.let { put(TourField.TRAVEL_DAYS, it) }
        countError(overnightStays)?.let { put(TourField.OVERNIGHT_STAYS, it) }
        countError(distanceKm)?.let { put(TourField.DISTANCE_KM, it) }
        costErrors.values.firstOrNull()?.let { put(TourField.COST, it) }
        val days = parseCount(travelDays)
        val nights = parseCount(overnightStays)
        if (days != null && nights != null && nights > days) {
            put(TourField.OVERNIGHT_STAYS, TourError.MORE_NIGHTS_THAN_DAYS)
        }
        if (mapLink.isNotBlank() && !isWebUrl(mapLink.trim())) {
            put(TourField.MAP_LINK, TourError.NOT_A_WEB_LINK)
        }
    }
    return TourValidation(errors, costErrors)
}

/** Fehler je Kostenzeile, Schlüssel ist der Index in [TourInput.costs]; leere Zeilen sind gültig. */
fun TourInput.costErrors(locale: Locale): Map<Int, TourError> = buildMap {
    costs.forEachIndexed { index, cost ->
        if (cost.amount.isBlank()) return@forEachIndexed
        when (readAmount(cost.amount, cost.currency, locale)) {
            is AmountReading.Valid -> Unit
            AmountReading.TooLarge -> put(index, TourError.AMOUNT_TOO_LARGE)
            AmountReading.Invalid -> put(index, TourError.INVALID_AMOUNT)
        }
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [Tour]. Vorher muss [validate] leer sein.
 *
 * @param original die bearbeitete Tour oder `null` für eine neue Tour
 * @param locale dieselbe Sprache wie bei [validate]
 * @return Tour mit id und Zeitstempeln von [original]; leere Kostenzeilen und Beträge von 0 fallen
 *   weg, doppelte Währungen werden addiert
 */
fun TourInput.toTour(original: Tour?, locale: Locale): Tour = Tour(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    vehicleId = vehicleId,
    startDate = checkNotNull(startDate),
    endDate = endDate,
    destination = destination.trim(),
    tourType = tourType,
    travelDays = checkNotNull(parseCount(travelDays)),
    overnightStays = checkNotNull(parseCount(overnightStays)),
    distanceKm = checkNotNull(parseCount(distanceKm)),
    costs = costs
        .map { Money(checkNotNull(parseCost(it.amount, it.currency, locale)), it.currency) }
        .groupBy(Money::currency)
        .map { (currency, amounts) -> Money(amounts.sumMinor(), currency) }
        .filter { it.minor != 0L },
    notes = notes.trim(),
    mapLink = mapLink.trim().ifEmpty { null },
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/**
 * Wandelt eine gespeicherte Tour in editierbare Formulardaten im Zahlenformat von [locale] um.
 * Ohne Kosten gibt es eine leere Zeile in Euro.
 */
fun Tour.toInput(locale: Locale): TourInput = TourInput(
    vehicleId = vehicleId,
    startDate = startDate,
    endDate = endDate,
    destination = destination,
    tourType = tourType,
    travelDays = travelDays.toString(),
    overnightStays = overnightStays.toString(),
    distanceKm = distanceKm.toString(),
    costs = costs
        .map { CostInput(amountToInput(it.minor, it.currency, locale), it.currency) }
        .ifEmpty { listOf(CostInput()) },
    notes = notes,
    mapLink = mapLink.orEmpty(),
)

/** Anzahl Reisetage inklusive An- und Abreisetag. */
fun travelDaysBetween(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end) + 1

/** Prüft, ob [link] eine absolute http- oder https-URL mit Host ist. */
fun isWebUrl(link: String): Boolean {
    val uri = runCatching { URI(link) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}

private fun parseCount(text: String): Int? = if (text.isBlank()) 0 else text.trim().toIntOrNull()?.takeIf { it >= 0 }

private fun parseCost(text: String, currency: Currency, locale: Locale): Long? =
    if (text.isBlank()) 0 else parseAmount(text, currency, locale)

private fun countError(text: String): TourError? = when {
    parseCount(text) != null -> null
    text.trim().toIntOrNull() != null -> TourError.NEGATIVE_NUMBER
    else -> TourError.INVALID_NUMBER
}
