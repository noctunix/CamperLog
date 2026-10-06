package app.restvolt.camperlog.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Höchstlängen der Textfelder einer Station, wie bei Touren auch von der Sicherung wiederverwendet. */
const val MAX_STATION_NAME_LENGTH = 500
const val MAX_STATION_PLACE_LENGTH = 500
const val MAX_STATION_NOTES_LENGTH = 20_000
const val MAX_STATION_MAP_LINK_LENGTH = 4_000

/** WGS84-Wertebereiche gültiger Koordinaten. */
val LATITUDE_RANGE = -90.0..90.0
val LONGITUDE_RANGE = -180.0..180.0

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
    val electricityFlatRate: ElectricityFlatRate? = null,
    val lteQuality: LteQuality? = null,
    val pitchSlope: PitchSlope? = null,
    val levelingBlocksUsed: Boolean? = null,
    val services: Set<StationService> = emptySet(),
    val favorite: Boolean = false,
    /** Rohtext des Felds "Koordinaten oder Kartenlink" (5.1); nur fürs Formular, nicht Teil der Station. */
    val locationText: String = "",
    val weather: WeatherSnapshot? = null,
)

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class StationField { DATE, COORDINATES, NIGHTS, NAME, PLACE, NOTES, MAP_LINK, SERVICES }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class StationError {
    REQUIRED,
    INVALID_NUMBER,
    TOO_SMALL,
    COORDINATES_INCOMPLETE,
    COORDINATES_OUT_OF_RANGE,
    TOO_LONG,
    NOT_A_WEB_LINK,
    FUTURE_DATE,
}

/**
 * Prüft alle fachlichen Regeln einer Stations-Eingabe. Felder, die nicht zu [StationInput.type]
 * gehören (z. B. Stellplatz-Details bei einer Nicht-Übernachtung), werden nicht geprüft, sondern in
 * [toStation] stillschweigend verworfen, falls der Typ zuvor gewechselt wurde.
 *
 * @param today Bezugsdatum für die Zukunftsprüfung der Ver-/Entsorgungs-Häkchen (4, Regel 5)
 * @return Fehlergrund je fehlerhaftem Feld; leer, wenn die Eingabe gültig ist
 */
fun StationInput.validate(today: LocalDate = LocalDate.now()): Map<StationField, StationError> = buildMap {
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
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [Station]. Vorher muss [validate] leer sein.
 * Typspezifische Felder und nicht erlaubte [StationService]-Werte werden hier anhand von
 * [StationInput.type] verworfen (siehe 6.5: ein Typwechsel lässt nicht passende Werte fallen).
 * [mapLink] wird nur übernommen, wenn keine Koordinaten gesetzt sind.
 *
 * @param original die bearbeitete Station oder `null` für eine neue Station
 */
fun StationInput.toStation(original: Station?): Station {
    val isOvernight = type == StationType.OVERNIGHT
    val hasCoordinates = latitude != null && longitude != null
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
        electricityFlatRate = electricityFlatRate.takeIf { isOvernight },
        lteQuality = lteQuality.takeIf { isOvernight },
        pitchSlope = pitchSlope.takeIf { isOvernight },
        levelingBlocksUsed = levelingBlocksUsed.takeIf { isOvernight },
        services = services.intersect(type.allowedServices),
        weather = weather,
        favorite = favorite && isOvernight,
        createdAt = original?.createdAt ?: Instant.EPOCH,
        updatedAt = original?.updatedAt ?: Instant.EPOCH,
    )
}

/** Wandelt eine gespeicherte Station in editierbare Formulardaten um. */
fun Station.toInput(): StationInput = StationInput(
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
    electricityFlatRate = electricityFlatRate,
    lteQuality = lteQuality,
    pitchSlope = pitchSlope,
    levelingBlocksUsed = levelingBlocksUsed,
    services = services,
    favorite = favorite,
    locationText = mapLink ?: if (latitude != null && longitude != null) "$latitude, $longitude" else "",
    weather = weather,
)
