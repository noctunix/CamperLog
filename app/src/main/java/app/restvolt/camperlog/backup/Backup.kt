package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.RATE_FRACTION_DIGITS
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.fractionDigits
import app.restvolt.camperlog.domain.isWebUrl
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

/** Kennung im Feld `format`, an der eine CamperLog-Sicherung erkannt wird. */
const val BACKUP_FORMAT = "camperlog-backup"

/** Aktuelle Version des Sicherungsformats; ältere Versionen müssen lesbar bleiben. */
const val BACKUP_SCHEMA_VERSION = 1

/** Größte einlesbare Sicherungsdatei in Bytes. */
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

internal const val MAX_TOURS = 50_000
internal const val MAX_RATES = 500
internal const val MAX_DESTINATION_LENGTH = 500
internal const val MAX_NOTES_LENGTH = 20_000
internal const val MAX_LINK_LENGTH = 4_000
internal const val MAX_SOURCE_LENGTH = 500
private val MAX_AMOUNT = BigDecimal("1000000000000")
private val MAX_RATE = BigDecimal("1000000000")

/**
 * Plausibler Datumsbereich. Extreme Werte würden sonst später beim Umrechnen in Epoch-Millis,
 * in Zeitzonen oder im DatePicker Ausnahmen werfen.
 */
private val MIN_DATE = LocalDate.of(1900, 1, 1)
private val MAX_DATE = LocalDate.of(2199, 12, 31)
private val MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z")
private val MAX_INSTANT = Instant.parse("2199-12-31T00:00:00Z")

/**
 * Vollständige Sicherung: alle [tours], alle [rates] und die [mainCurrency].
 * Datenbank-ids sind nicht enthalten; Touren werden über [Tour.uuid] wiedererkannt.
 */
data class Backup(
    val exportedAt: Instant,
    val mainCurrency: Currency,
    val rates: List<ExchangeRate>,
    val tours: List<Tour>,
)

/** Warum eine Datei nicht als Sicherung gelesen werden konnte. */
enum class BackupError {
    /** Größer als [MAX_BACKUP_BYTES]. */
    TOO_LARGE,

    /** Kein JSON oder keine CamperLog-Sicherung. */
    NOT_A_BACKUP,

    /** Von einer neueren App-Version mit unbekanntem Format erstellt. */
    NEWER_VERSION,

    /** Formal eine Sicherung, aber mit fehlenden oder ungültigen Werten. */
    INVALID_DATA,
}

/** Ergebnis von [readBackup]/[decodeBackup]. */
sealed interface BackupReadResult {
    data class Success(val backup: Backup) : BackupReadResult

    /** [tourNumber] ist die 1-basierte Position einer fehlerhaften Tour in der Datei, sofern bekannt. */
    data class Failure(val error: BackupError, val tourNumber: Int? = null) : BackupReadResult
}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
}

/** Schreibt [backup] als JSON im Format [BACKUP_SCHEMA_VERSION]. */
fun encodeBackup(backup: Backup): String = json.encodeToString(
    BackupDto.serializer(),
    BackupDto(
        format = BACKUP_FORMAT,
        schemaVersion = BACKUP_SCHEMA_VERSION,
        exportedAt = backup.exportedAt.toString(),
        mainCurrency = backup.mainCurrency.currencyCode,
        exchangeRates = backup.rates.sortedBy { it.currency.currencyCode }.map { it.toDto() },
        tours = backup.tours.map { json.encodeToJsonElement(TourDto.serializer(), it.toDto()) },
    ),
)

/**
 * Liest höchstens [MAX_BACKUP_BYTES] aus [input] als UTF-8 und prüft den Inhalt mit [decodeBackup].
 * Der Stream wird nicht geschlossen.
 */
fun readBackup(input: InputStream): BackupReadResult = try {
    val buffer = BoundedBuffer()
    val chunk = ByteArray(64 * 1024)
    var tooLarge = false
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        if (buffer.size() + read > MAX_BACKUP_BYTES) {
            tooLarge = true
            break
        }
        buffer.write(chunk, 0, read)
    }
    if (tooLarge) BackupReadResult.Failure(BackupError.TOO_LARGE) else decodeUtf8(buffer.bytes())
} catch (_: OutOfMemoryError) {
    // Ein bösartig verschachteltes Dokument kann trotz Größenlimit sehr viele JSON-Knoten erzeugen.
    // Die angelegten Objekte sind danach nicht mehr erreichbar, die App kann also weiterlaufen.
    BackupReadResult.Failure(BackupError.TOO_LARGE)
}

private fun decodeUtf8(bytes: ByteBuffer): BackupReadResult {
    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(bytes)
            .toString()
    } catch (_: CharacterCodingException) {
        return BackupReadResult.Failure(BackupError.NOT_A_BACKUP)
    }
    return decodeBackup(text.removePrefix("\uFEFF"))
}

/** Gibt den internen Puffer ohne Kopie frei, damit große Dateien nur einmal im Speicher liegen. */
private class BoundedBuffer : ByteArrayOutputStream() {
    fun bytes(): ByteBuffer = ByteBuffer.wrap(buf, 0, count)
}

/**
 * Prüft [text] vollständig und liefert die Sicherung nur, wenn alle Werte gültig sind.
 * Es gibt kein teilweises Ergebnis: Eine einzige fehlerhafte Tour lässt das Lesen scheitern.
 */
fun decodeBackup(text: String): BackupReadResult {
    // Jedes Zeichen braucht in UTF-8 mindestens ein Byte; die Byte-Grenze prüft readBackup genauer.
    if (text.length > MAX_BACKUP_BYTES) return BackupReadResult.Failure(BackupError.TOO_LARGE)
    val root = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return BackupReadResult.Failure(BackupError.NOT_A_BACKUP)

    val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (format != BACKUP_FORMAT) return BackupReadResult.Failure(BackupError.NOT_A_BACKUP)
    val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: return BackupReadResult.Failure(BackupError.INVALID_DATA)
    when {
        version > BACKUP_SCHEMA_VERSION -> return BackupReadResult.Failure(BackupError.NEWER_VERSION)
        version < 1 -> return BackupReadResult.Failure(BackupError.INVALID_DATA)
    }

    val dto = try {
        json.decodeFromJsonElement(BackupDto.serializer(), root)
    } catch (_: SerializationException) {
        return BackupReadResult.Failure(BackupError.INVALID_DATA)
    } catch (_: IllegalArgumentException) {
        return BackupReadResult.Failure(BackupError.INVALID_DATA)
    }
    return dto.toBackup()
}

private fun BackupDto.toBackup(): BackupReadResult {
    val invalid = BackupReadResult.Failure(BackupError.INVALID_DATA)
    if (tours.size > MAX_TOURS || exchangeRates.size > MAX_RATES) return invalid
    val exportedAt = parseInstant(exportedAt) ?: return invalid
    val mainCurrency = parseCurrency(mainCurrency) ?: return invalid

    val rates = exchangeRates.map { it.toRate() ?: return invalid }
    if (rates.map { it.currency }.distinct().size != rates.size) return invalid

    val seenUuids = HashSet<String>()
    val tours = tours.mapIndexed { index, element ->
        val tour = decodeTour(element)
        if (tour == null || !seenUuids.add(tour.uuid)) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = index + 1)
        }
        tour
    }
    return BackupReadResult.Success(Backup(exportedAt, mainCurrency, rates, tours))
}

private fun decodeTour(element: JsonElement): Tour? = try {
    json.decodeFromJsonElement(TourDto.serializer(), element).toTour()
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun RateDto.toRate(): ExchangeRate? {
    val currency = parseCurrency(currency)?.takeIf { it != EUR } ?: return null
    val perEuro = parseDecimal(perEuro, RATE_FRACTION_DIGITS)?.takeIf { it.signum() > 0 && it <= MAX_RATE } ?: return null
    val date = parseDate(date) ?: return null
    if (source.length > MAX_SOURCE_LENGTH) return null
    return ExchangeRate(currency, perEuro, date, source)
}

private fun TourDto.toTour(): Tour? {
    val uuid = parseUuid(uuid) ?: return null
    val start = parseDate(startDate) ?: return null
    val end = parseDate(endDate)?.takeIf { it >= start } ?: return null
    val destination = destination.trim().takeIf { it.isNotEmpty() && it.length <= MAX_DESTINATION_LENGTH } ?: return null
    if (travelDays < 0 || overnightStays < 0 || distanceKm < 0 || overnightStays > travelDays) return null
    if (notes.length > MAX_NOTES_LENGTH) return null
    val link = mapLink?.trim()?.ifEmpty { null }
    if (link != null && (link.length > MAX_LINK_LENGTH || !isWebUrl(link))) return null
    val money = costs.map { it.toMoney() ?: return null }
    if (money.map { it.currency }.distinct().size != money.size) return null
    return Tour(
        uuid = uuid,
        startDate = start,
        endDate = end,
        destination = destination,
        tourType = enumOrNull<TourType>(tourType) ?: return null,
        travelDays = travelDays,
        overnightStays = overnightStays,
        distanceKm = distanceKm,
        costs = money,
        pitchAssigned = pitchAssigned,
        electricityFlatRate = enumOrNull<ElectricityFlatRate>(electricityFlatRate) ?: return null,
        lteQuality = enumOrNull<LteQuality>(lteQuality) ?: return null,
        pitchSlope = enumOrNull<PitchSlope>(pitchSlope) ?: return null,
        levelingBlocksUsed = levelingBlocksUsed,
        notes = notes,
        mapLink = link,
        createdAt = parseInstant(createdAt) ?: return null,
        updatedAt = parseInstant(updatedAt) ?: return null,
    )
}

private fun CostDto.toMoney(): Money? {
    val currency = parseCurrency(currency) ?: return null
    val value = parseDecimal(amount, currency.fractionDigits)?.takeIf { it <= MAX_AMOUNT } ?: return null
    return Money(value.movePointRight(currency.fractionDigits).longValueExact(), currency)
}

private fun ExchangeRate.toDto() = RateDto(
    currency = currency.currencyCode,
    perEuro = perEuro.stripTrailingZeros().toPlainString(),
    date = date.toString(),
    source = source,
)

private fun Tour.toDto() = TourDto(
    uuid = uuid,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    destination = destination,
    tourType = tourType.name,
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    costs = costs.map {
        CostDto(it.currency.currencyCode, BigDecimal.valueOf(it.minor, it.currency.fractionDigits).toPlainString())
    },
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate.name,
    lteQuality = lteQuality.name,
    pitchSlope = pitchSlope.name,
    levelingBlocksUsed = levelingBlocksUsed,
    notes = notes,
    mapLink = mapLink,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private val DECIMAL = Regex("""\d{1,20}(\.\d{1,20})?""")
private val UUID_PATTERN = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

/** Nicht negative Dezimalzahl mit Punkt und höchstens [maxFractionDigits] signifikanten Nachkommastellen. */
private fun parseDecimal(text: String, maxFractionDigits: Int): BigDecimal? {
    if (!DECIMAL.matches(text)) return null
    val value = BigDecimal(text)
    val scale = value.stripTrailingZeros().scale()
    return value.takeIf { scale <= maxFractionDigits }
}

private fun parseUuid(text: String): String? =
    text.takeIf { UUID_PATTERN.matches(it) }?.let { UUID.fromString(it).toString() }

private fun parseCurrency(code: String): Currency? = ALL_CURRENCIES.firstOrNull { it.currencyCode == code }

private fun parseDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text) }.getOrNull()?.takeIf { it in MIN_DATE..MAX_DATE }

private fun parseInstant(text: String): Instant? =
    runCatching { Instant.parse(text) }.getOrNull()?.takeIf { it in MIN_INSTANT..MAX_INSTANT }

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
