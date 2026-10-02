package app.restvolt.camperlog.domain

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Unvalidierte Eingaben des Tour-Formulars. Zahlen und Beträge liegen als Text vor. */
data class TourInput(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val destination: String = "",
    val tourType: TourType = TourType.WEEKEND,
    val travelDays: String = "",
    val overnightStays: String = "",
    val distanceKm: String = "",
    val cost: String = "",
    val pitchAssigned: Boolean = false,
    val electricityFlatRate: ElectricityFlatRate = ElectricityFlatRate.NOT_USED,
    val lteQuality: LteQuality = LteQuality.GOOD,
    val pitchSlope: PitchSlope = PitchSlope.LEVEL,
    val levelingBlocksUsed: Boolean = false,
    val notes: String = "",
    val mapLink: String = "",
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
    MORE_NIGHTS_THAN_DAYS,
    NOT_A_WEB_LINK,
}

/**
 * Prüft alle fachlichen Regeln einer Tour-Eingabe.
 *
 * @param locale bestimmt, wie mehrdeutige Beträge wie `1.234` gelesen werden
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun TourInput.validate(locale: Locale): Map<TourField, TourError> = buildMap {
    if (startDate == null) put(TourField.START_DATE, TourError.REQUIRED)
    when {
        endDate == null -> put(TourField.END_DATE, TourError.REQUIRED)
        startDate != null && endDate < startDate -> put(TourField.END_DATE, TourError.END_BEFORE_START)
    }
    if (destination.isBlank()) put(TourField.DESTINATION, TourError.REQUIRED)
    countError(travelDays)?.let { put(TourField.TRAVEL_DAYS, it) }
    countError(overnightStays)?.let { put(TourField.OVERNIGHT_STAYS, it) }
    countError(distanceKm)?.let { put(TourField.DISTANCE_KM, it) }
    if (parseCost(cost, locale) == null) put(TourField.COST, TourError.INVALID_AMOUNT)
    val days = parseCount(travelDays)
    val nights = parseCount(overnightStays)
    if (days != null && nights != null && nights > days) {
        put(TourField.OVERNIGHT_STAYS, TourError.MORE_NIGHTS_THAN_DAYS)
    }
    if (mapLink.isNotBlank() && !isWebUrl(mapLink.trim())) {
        put(TourField.MAP_LINK, TourError.NOT_A_WEB_LINK)
    }
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [Tour]. Vorher muss [validate] leer sein.
 *
 * @param original die bearbeitete Tour oder `null` für eine neue Tour
 * @param locale dieselbe Sprache wie bei [validate]
 * @return Tour mit id und Zeitstempeln von [original]
 */
fun TourInput.toTour(original: Tour?, locale: Locale): Tour = Tour(
    id = original?.id ?: 0,
    startDate = checkNotNull(startDate),
    endDate = checkNotNull(endDate),
    destination = destination.trim(),
    tourType = tourType,
    travelDays = checkNotNull(parseCount(travelDays)),
    overnightStays = checkNotNull(parseCount(overnightStays)),
    distanceKm = checkNotNull(parseCount(distanceKm)),
    costCents = checkNotNull(parseCost(cost, locale)),
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate,
    lteQuality = lteQuality,
    pitchSlope = pitchSlope,
    levelingBlocksUsed = levelingBlocksUsed,
    notes = notes.trim(),
    mapLink = mapLink.trim().ifEmpty { null },
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt eine gespeicherte Tour in editierbare Formulardaten im Zahlenformat von [locale] um. */
fun Tour.toInput(locale: Locale): TourInput = TourInput(
    startDate = startDate,
    endDate = endDate,
    destination = destination,
    tourType = tourType,
    travelDays = travelDays.toString(),
    overnightStays = overnightStays.toString(),
    distanceKm = distanceKm.toString(),
    cost = amountToInput(costCents, EUR, locale),
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate,
    lteQuality = lteQuality,
    pitchSlope = pitchSlope,
    levelingBlocksUsed = levelingBlocksUsed,
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

private fun parseCost(text: String, locale: Locale): Long? =
    if (text.isBlank()) 0 else parseAmount(text, EUR, locale)

private fun countError(text: String): TourError? = when {
    parseCount(text) != null -> null
    text.trim().toIntOrNull() != null -> TourError.NEGATIVE_NUMBER
    else -> TourError.INVALID_NUMBER
}
