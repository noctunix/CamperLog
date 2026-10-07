package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.ALL_COUNTRY_CODES
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.AmountReading
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LATITUDE_RANGE
import app.restvolt.camperlog.domain.LONGITUDE_RANGE
import app.restvolt.camperlog.domain.LegacyPitchFields
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.MAX_BATTERY_AH
import app.restvolt.camperlog.domain.MAX_DIMENSION_M
import app.restvolt.camperlog.domain.MAX_ELECTRICITY_COINS
import app.restvolt.camperlog.domain.MAX_ELECTRICITY_KWH
import app.restvolt.camperlog.domain.MAX_FERRY_BOOKING_REFERENCE_LENGTH
import app.restvolt.camperlog.domain.MAX_ODOMETER_KM
import app.restvolt.camperlog.domain.MAX_POWER_KW
import app.restvolt.camperlog.domain.MAX_PRICE_PER_KWH
import app.restvolt.camperlog.domain.MAX_SOLAR_WP
import app.restvolt.camperlog.domain.MAX_STATION_MAP_LINK_LENGTH
import app.restvolt.camperlog.domain.MAX_STATION_NAME_LENGTH
import app.restvolt.camperlog.domain.MAX_STATION_NOTES_LENGTH
import app.restvolt.camperlog.domain.MAX_STATION_PLACE_LENGTH
import app.restvolt.camperlog.domain.MAX_TANK_L
import app.restvolt.camperlog.domain.MAX_TIRE_PRESSURE_BAR
import app.restvolt.camperlog.domain.MAX_TOLL_PAYMENT_METHOD_LENGTH
import app.restvolt.camperlog.domain.MAX_WEIGHT_KG
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.RATE_FRACTION_DIGITS
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.domain.allowedServices
import app.restvolt.camperlog.domain.amountReading
import app.restvolt.camperlog.domain.fractionDigits
import app.restvolt.camperlog.domain.isValidPhone
import app.restvolt.camperlog.domain.isWebUrl
import app.restvolt.camperlog.domain.migrateLegacyElectricityFlatRate
import app.restvolt.camperlog.domain.migrateLegacyPitch
import kotlinx.serialization.KSerializer
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Currency
import java.util.UUID

/** Kennung im Feld `format`, an der eine CamperLog-Sicherung erkannt wird. */
const val BACKUP_FORMAT = "camperlog-backup"

/** Aktuelle Version des Sicherungsformats; ältere Versionen müssen lesbar bleiben. */
const val BACKUP_SCHEMA_VERSION = 6

/** Größte einlesbare Sicherungsdatei in Bytes. */
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

internal const val MAX_TOURS = 50_000
internal const val MAX_RATES = 500
internal const val MAX_VEHICLES = 100
internal const val MAX_REPAIRS_PER_VEHICLE = 5_000
internal const val MAX_LOG_ENTRIES_PER_VEHICLE = 50_000
internal const val MAX_STATIONS = 200_000
internal const val MAX_COSTS_PER_STATION = 100
internal const val MAX_DESTINATION_LENGTH = 500
internal const val MAX_NOTES_LENGTH = 20_000
internal const val MAX_LINK_LENGTH = 4_000
internal const val MAX_SOURCE_LENGTH = 500
internal const val MAX_VEHICLE_DOCUMENTS = 10_000
internal const val MAX_ATTACHMENTS = 200_000
internal const val MAX_CAPTION_LENGTH = 2_000

/** Wie `AttachmentFileStore.MAX_DOCUMENT_BYTES`; hier verdoppelt statt importiert, damit `backup/` ohne Android-Abhängigkeiten bleibt. */
private const val MAX_ATTACHMENT_SIZE_BYTES = 20L * 1024 * 1024
private const val MAX_ATTACHMENT_DIMENSION = 20_000

/** Dateiendung je unterstütztem MIME-Typ eines Anhangs; ebenfalls dupliziert statt aus `AttachmentFileStore` importiert. */
internal val ATTACHMENT_EXTENSION_BY_MIME_TYPE = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "application/pdf" to "pdf",
)
internal val ATTACHMENT_FILE_NAME_PATTERN = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.[a-z]+""")
private val MAX_RATE = BigDecimal("1000000000")
private const val MAX_WEATHER_TEMPERATURE_DECI_C = 1_000
private const val MAX_WEATHER_CODE = 99
private const val MAX_WEATHER_WIND_KMH = 500
private const val MAX_WEATHER_WIND_DIRECTION_DEG = 359

/** Höchstwerte der Fahrzeugfelder in der gespeicherten Einheit, abgeleitet von den Formulargrenzen. */
private val MAX_LENGTH_CM = (MAX_DIMENSION_M * 100).toInt()
private val MAX_TIRE_PRESSURE_MBAR = (MAX_TIRE_PRESSURE_BAR * 1000).toInt()
private val MAX_TANK_DL = (MAX_TANK_L * 10).toInt()

/**
 * Plausibler Datumsbereich. Extreme Werte würden sonst später beim Umrechnen in Epoch-Millis,
 * in Zeitzonen oder im DatePicker Ausnahmen werfen.
 */
private val MIN_DATE = LocalDate.of(1900, 1, 1)
private val MAX_DATE = LocalDate.of(2199, 12, 31)
private val MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z")
private val MAX_INSTANT = Instant.parse("2199-12-31T00:00:00Z")

/**
 * Vollständige Sicherung: alle [tours], alle [rates], die [mainCurrency] und alle [vehicles] samt
 * ihren Reparaturen und Bordbuch-Einträgen. Datenbank-ids sind nicht enthalten; alle Einträge
 * werden über ihre UUID wiedererkannt. [tourVehicleUuid] ordnet einer Tour-UUID die UUID ihres
 * Fahrzeugs zu; fehlt eine Tour hier, gehört sie keinem bestimmten Fahrzeug (Formatversion 1) und
 * bekommt beim Import das aktuelle Fahrzeug zugewiesen. [currentVehicleUuid] nennt das beim Export
 * aktuelle Fahrzeug. [stations] enthält sowohl aus dem Sicherungsformat gelesene als auch aus den
 * alten Stellplatz-Feldern einer Tour vor Formatversion 3 abgeleitete Stationen; [stationVehicleUuid]
 * ordnet einer Stations-UUID die UUID ihres Fahrzeugs zu (fehlt bei einer aus einer Tour abgeleiteten
 * Station: ihr Fahrzeug ist das der Tour), [stationTourUuid] die UUID ihrer Tour, falls sie zu einer
 * gehört. [logEntryStationUuid] ordnet einer Bordbuch-Eintrags-UUID die UUID ihrer verknüpften
 * Station zu; fehlt ein Eintrag hier, ist er nicht verknüpft. [documents] enthält alle
 * Fahrzeugdokumente, [attachments] alle Anhänge (Fotos und Dokumentdateien) zu Stationen,
 * Reparaturen, Bordbuch-Einträgen und Fahrzeugdokumenten (Formatversion 6); beides fehlt in älteren
 * Sicherungen. Eine Sicherung ohne Dateien (JSON-only-Export) enthält [attachments] trotzdem als
 * Metadaten, nur ohne die zugehörigen Dateien im ZIP.
 */
data class Backup(
    val exportedAt: Instant,
    val mainCurrency: Currency,
    val rates: List<ExchangeRate>,
    val tours: List<Tour>,
    val tourVehicleUuid: Map<String, String> = emptyMap(),
    val vehicles: List<BackupVehicle> = emptyList(),
    val currentVehicleUuid: String? = null,
    val stations: List<Station> = emptyList(),
    val stationVehicleUuid: Map<String, String> = emptyMap(),
    val stationTourUuid: Map<String, String> = emptyMap(),
    val logEntryStationUuid: Map<String, String> = emptyMap(),
    val documents: List<BackupVehicleDocument> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList(),
)

/** Ein Fahrzeug einer Sicherung mit seinen Reparaturen und Bordbuch-Einträgen. */
data class BackupVehicle(
    val vehicle: Vehicle,
    val repairs: List<Repair>,
    val logEntries: List<LogEntry>,
)

/** Ein Fahrzeugdokument einer Sicherung mit [vehicleUuid], der UUID seines Fahrzeugs. */
data class BackupVehicleDocument(val document: VehicleDocument, val vehicleUuid: String)

/**
 * Ein Anhang einer Sicherung mit [ownerUuid], der UUID des Eintrags, zu dem er gehört (je
 * [Attachment.ownerType] eine Station, Reparatur, Bordbuch-Eintrag oder ein Fahrzeugdokument), statt
 * seiner lokalen Datenbank-id, und [zipPath], seinem menschenlesbaren Pfad in der ZIP-Sicherung
 * (siehe `buildAttachmentZipPaths`; bei einer frisch gebauten [Backup] zunächst leer und erst von
 * `buildBackup` befüllt).
 */
data class BackupAttachment(val attachment: Attachment, val ownerUuid: String, val zipPath: String = "")

/**
 * Ein beim Lesen der Bordbuch-Einträge gefundener Stationsbezug, dessen Existenz erst geprüft werden
 * kann, nachdem Touren und Stationen vollständig gelesen sind (siehe [BackupDto.toBackup]).
 */
private data class PendingLogEntryStationLink(val vehicleNumber: Int, val logEntryNumber: Int, val entryUuid: String, val stationUuid: String)

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

    /**
     * Fundstelle einer fehlerhaften Tour, eines fehlerhaften Fahrzeugs oder dessen Reparatur bzw.
     * Bordbuch-Eintrags, jeweils als 1-basierte Position in der Datei, sofern bekannt.
     */
    data class Failure(
        val error: BackupError,
        val tourNumber: Int? = null,
        val vehicleNumber: Int? = null,
        val repairNumber: Int? = null,
        val logEntryNumber: Int? = null,
        val stationNumber: Int? = null,
        val documentNumber: Int? = null,
        val attachmentNumber: Int? = null,
    ) : BackupReadResult
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
        tours = backup.tours.map { tour ->
            json.encodeToJsonElement(TourDto.serializer(), tour.toDto(backup.tourVehicleUuid[tour.uuid]))
        },
        vehicles = backup.vehicles.map { json.encodeToJsonElement(VehicleDto.serializer(), it.toDto(backup.logEntryStationUuid)) },
        currentVehicle = backup.currentVehicleUuid,
        stations = backup.stations.map { station ->
            val dto = station.toDto(
                vehicleUuid = checkNotNull(backup.stationVehicleUuid[station.uuid]) { "Station ohne Fahrzeug-UUID: ${station.uuid}" },
                tourUuid = backup.stationTourUuid[station.uuid],
            )
            json.encodeToJsonElement(StationDto.serializer(), dto)
        },
        vehicleDocuments = backup.documents.map { json.encodeToJsonElement(VehicleDocumentDto.serializer(), it.toDto()) },
        attachments = backup.attachments.map { json.encodeToJsonElement(AttachmentDto.serializer(), it.toDto()) },
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
 * Es gibt kein teilweises Ergebnis: Ein einziger fehlerhafter Eintrag lässt das Lesen scheitern.
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
    if (tours.size > MAX_TOURS || exchangeRates.size > MAX_RATES || vehicles.size > MAX_VEHICLES || stations.size > MAX_STATIONS) {
        return invalid
    }
    val exportedAt = parseInstant(exportedAt) ?: return invalid
    val mainCurrency = parseCurrency(mainCurrency) ?: return invalid

    val rates = exchangeRates.map { it.toRate() ?: return invalid }
    if (rates.map { it.currency }.distinct().size != rates.size) return invalid

    val seenVehicleUuids = HashSet<String>()
    val seenRepairUuids = HashSet<String>()
    val seenLogEntryUuids = HashSet<String>()
    val pendingLogEntryStationLinks = ArrayList<PendingLogEntryStationLink>()
    val backupVehicles = vehicles.mapIndexed { index, element ->
        val vehicleNumber = index + 1
        val dto = decodeJson(VehicleDto.serializer(), element) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber)
        if (dto.repairs.size > MAX_REPAIRS_PER_VEHICLE || dto.logEntries.size > MAX_LOG_ENTRIES_PER_VEHICLE) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber)
        }
        val vehicle = dto.toVehicle() ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber)
        if (!seenVehicleUuids.add(vehicle.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber)

        val repairs = dto.repairs.mapIndexed { repairIndex, repairElement ->
            val repairNumber = repairIndex + 1
            val repairDto = decodeJson(RepairDto.serializer(), repairElement)
                ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, repairNumber = repairNumber)
            val repair = repairDto.toRepair()
                ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, repairNumber = repairNumber)
            if (!seenRepairUuids.add(repair.uuid)) {
                return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, repairNumber = repairNumber)
            }
            repair
        }
        val logEntries = dto.logEntries.mapIndexed { logIndex, logElement ->
            val logEntryNumber = logIndex + 1
            val logDto = decodeJson(LogEntryDto.serializer(), logElement)
                ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, logEntryNumber = logEntryNumber)
            val entry = logDto.toLogEntry()
                ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, logEntryNumber = logEntryNumber)
            if (!seenLogEntryUuids.add(entry.uuid)) {
                return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, logEntryNumber = logEntryNumber)
            }
            logDto.stationUuid?.let { candidate ->
                val normalized = parseUuid(candidate)
                    ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = vehicleNumber, logEntryNumber = logEntryNumber)
                pendingLogEntryStationLinks += PendingLogEntryStationLink(vehicleNumber, logEntryNumber, entry.uuid, normalized)
            }
            entry
        }
        BackupVehicle(vehicle, repairs, logEntries)
    }

    val currentVehicleUuid = currentVehicle?.let { candidate ->
        val normalized = parseUuid(candidate) ?: return invalid
        if (backupVehicles.none { it.vehicle.uuid == normalized }) return invalid
        normalized
    }

    val seenTourUuids = HashSet<String>()
    val seenStationUuids = HashSet<String>()
    val tourVehicleUuid = HashMap<String, String>()
    val legacyStations = ArrayList<Station>()
    val legacyStationTourUuid = HashMap<String, String>()
    val tours = tours.mapIndexed { index, element ->
        val tourNumber = index + 1
        val dto = decodeJson(TourDto.serializer(), element) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
        val tour = dto.toTour() ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
        if (!seenTourUuids.add(tour.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
        dto.vehicleUuid?.let { candidate ->
            val normalized = parseUuid(candidate) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
            if (backupVehicles.none { it.vehicle.uuid == normalized }) {
                return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
            }
            tourVehicleUuid[tour.uuid] = normalized
        }
        if (dto.hasAnyLegacyPitchField()) {
            val pitch = dto.legacyPitch()
            val stationUuid = UUID.nameUUIDFromBytes("camperlog-legacy-pitch:${tour.uuid}".toByteArray()).toString()
            val migration = migrateLegacyPitch(
                uuid = stationUuid,
                vehicleId = 0,
                tourId = null,
                startDate = tour.startDate,
                destination = tour.destination,
                overnightStays = tour.overnightStays,
                pitch = pitch,
                createdAt = tour.createdAt,
                updatedAt = tour.updatedAt,
            )
            migration.overnightStation?.let { station ->
                if (!seenStationUuids.add(station.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = tourNumber)
                legacyStations += station
                legacyStationTourUuid[station.uuid] = tour.uuid
            }
            // Die Notiz-Zeile eines Tagestrips mit abweichenden Werten braucht
            // lokalisierte Texte und bleibt dem Room-Import vorbehalten (siehe CamperLogDatabase).
        }
        tour
    }

    val stationVehicleUuid = HashMap<String, String>()
    val stationTourUuid = HashMap<String, String>(legacyStationTourUuid)
    val decodedStations = stations.mapIndexed { index, element ->
        val stationNumber = index + 1
        val dto = decodeJson(StationDto.serializer(), element)
            ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
        val station = dto.toStation() ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
        if (!seenStationUuids.add(station.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
        val vehicleUuid = parseUuid(dto.vehicleUuid) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
        if (backupVehicles.none { it.vehicle.uuid == vehicleUuid }) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
        }
        stationVehicleUuid[station.uuid] = vehicleUuid
        dto.tourUuid?.let { candidate ->
            val normalized = parseUuid(candidate) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
            if (normalized !in seenTourUuids) return BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = stationNumber)
            stationTourUuid[station.uuid] = normalized
        }
        station
    }

    val allStations = legacyStations + decodedStations
    val logEntryStationUuid = HashMap<String, String>()
    for (pending in pendingLogEntryStationLinks) {
        if (pending.stationUuid !in seenStationUuids) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = pending.vehicleNumber, logEntryNumber = pending.logEntryNumber)
        }
        logEntryStationUuid[pending.entryUuid] = pending.stationUuid
    }

    if (vehicleDocuments.size > MAX_VEHICLE_DOCUMENTS || attachments.size > MAX_ATTACHMENTS) return invalid
    val seenDocumentUuids = HashSet<String>()
    val documents = vehicleDocuments.mapIndexed { index, element ->
        val documentNumber = index + 1
        val dto = decodeJson(VehicleDocumentDto.serializer(), element)
            ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = documentNumber)
        val document = dto.toVehicleDocument() ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = documentNumber)
        if (!seenDocumentUuids.add(document.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = documentNumber)
        val vehicleUuid = parseUuid(dto.vehicleUuid) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = documentNumber)
        if (backupVehicles.none { it.vehicle.uuid == vehicleUuid }) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = documentNumber)
        }
        BackupVehicleDocument(document, vehicleUuid)
    }

    val seenAttachmentUuids = HashSet<String>()
    val seenZipPaths = HashSet<String>()
    val seenZipPathsLowercase = HashSet<String>()
    val backupAttachments = attachments.mapIndexed { index, element ->
        val attachmentNumber = index + 1
        val dto = decodeJson(AttachmentDto.serializer(), element)
            ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        val ownerType = enumOrNull<AttachmentOwnerType>(dto.ownerType) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        val attachment = dto.toAttachment(ownerType) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        if (!seenAttachmentUuids.add(attachment.uuid)) return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        // Jeder ZIP-Pfad darf nur einmal vorkommen, auch nur in Groß-/Kleinschreibung verschieden -
        // sonst würden zwei Anhänge auf einem case-insensitiven Dateisystem beim Entpacken kollidieren.
        if (dto.zipPath == BACKUP_ZIP_JSON_ENTRY || !isValidZipPath(dto.zipPath)) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        }
        if (!seenZipPaths.add(dto.zipPath) || !seenZipPathsLowercase.add(dto.zipPath.lowercase())) {
            return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        }
        val ownerUuid = parseUuid(dto.ownerUuid) ?: return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        val ownerKnown = when (ownerType) {
            AttachmentOwnerType.STATION -> ownerUuid in seenStationUuids
            AttachmentOwnerType.REPAIR -> ownerUuid in seenRepairUuids
            AttachmentOwnerType.LOG_ENTRY -> ownerUuid in seenLogEntryUuids
            AttachmentOwnerType.VEHICLE_DOCUMENT -> ownerUuid in seenDocumentUuids
        }
        if (!ownerKnown) return BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = attachmentNumber)
        BackupAttachment(attachment, ownerUuid, dto.zipPath)
    }

    return BackupReadResult.Success(
        Backup(
            exportedAt = exportedAt,
            mainCurrency = mainCurrency,
            rates = rates,
            tours = tours,
            tourVehicleUuid = tourVehicleUuid,
            vehicles = backupVehicles,
            currentVehicleUuid = currentVehicleUuid,
            stations = allStations,
            stationVehicleUuid = stationVehicleUuid,
            stationTourUuid = stationTourUuid,
            logEntryStationUuid = logEntryStationUuid,
            documents = documents,
            attachments = backupAttachments,
        ),
    )
}

/** Ob mindestens eines der fünf alten Stellplatz-Felder gesetzt ist (Sicherung vor Formatversion 3). */
private fun TourDto.hasAnyLegacyPitchField(): Boolean =
    listOf(pitchAssigned, electricityFlatRate, lteQuality, pitchSlope, levelingBlocksUsed).any { it != null }

/** Jedes der fünf alten Stellplatz-Felder einzeln; ein fehlendes oder unbekanntes Feld wird `null` statt die ganze Tour zu verwerfen. */
private fun TourDto.legacyPitch(): LegacyPitchFields = LegacyPitchFields(
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate?.let { enumOrNull<ElectricityFlatRate>(it) },
    lteQuality = lteQuality?.let { enumOrNull<LteQuality>(it) },
    pitchSlope = pitchSlope?.let { enumOrNull<PitchSlope>(it) },
    levelingBlocksUsed = levelingBlocksUsed,
)

private fun <T> decodeJson(serializer: KSerializer<T>, element: JsonElement): T? = try {
    json.decodeFromJsonElement(serializer, element)
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
        notes = notes,
        mapLink = link,
        createdAt = parseInstant(createdAt) ?: return null,
        updatedAt = parseInstant(updatedAt) ?: return null,
    )
}

private fun VehicleDto.toVehicle(): Vehicle? {
    val uuid = parseUuid(uuid) ?: return null
    if (
        listOf(
            name, licensePlate, manufacturer, model, vin, insurer, insurancePolicyNumber, tireSize,
            breakdownProvider, breakdownMembershipNumber, travelProtectionProvider, travelProtectionContractNumber,
        ).any { it.length > MAX_DESTINATION_LENGTH }
    ) {
        return null
    }
    if (listOf(breakdownPhone, travelProtectionPhone, insurerClaimsPhone).any { it.isNotEmpty() && !isValidPhone(it) }) return null
    if (notes.length > MAX_NOTES_LENGTH) return null
    val firstRegistration = firstRegistration?.let { parseDate(it) ?: return null }
    val purchaseDate = purchaseDate?.let { parseDate(it) ?: return null }
    val saleDate = saleDate?.let { parseDate(it) ?: return null }
    val nextInspectionDate = nextInspectionDate?.let { parseDate(it) ?: return null }
    val nextGasCheckDate = nextGasCheckDate?.let { parseDate(it) ?: return null }
    val nextLeakTestDate = nextLeakTestDate?.let { parseDate(it) ?: return null }
    val lastOilChangeDate = lastOilChangeDate?.let { parseDate(it) ?: return null }
    val purchasePriceMoney = purchasePrice?.let { it.toMoney() ?: return null }
    val salePriceMoney = salePrice?.let { it.toMoney() ?: return null }
    val insurancePremium = insurancePremiumPerYear?.let { it.toMoney() ?: return null }
    val vehicleTax = vehicleTaxPerYear?.let { it.toMoney() ?: return null }
    if (!lengthCm.inBounds(MAX_LENGTH_CM) || !widthCm.inBounds(MAX_LENGTH_CM) || !heightCm.inBounds(MAX_LENGTH_CM)) return null
    if (!grossWeightKg.inBounds(MAX_WEIGHT_KG) || !powerKw.inBounds(MAX_POWER_KW)) return null
    if (!measuredEmptyWeightKg.inBounds(MAX_WEIGHT_KG)) return null
    if (!tirePressureFrontMbar.inBounds(MAX_TIRE_PRESSURE_MBAR) || !tirePressureRearMbar.inBounds(MAX_TIRE_PRESSURE_MBAR)) return null
    val tanksInBounds = listOf(fuelTankDl, adBlueTankDl, freshWaterTankDl, greyWaterTankDl, boilerDl, cassetteDl)
        .all { it.inBounds(MAX_TANK_DL) }
    if (!tanksInBounds) return null
    if (!batteryCapacityAh.inBounds(MAX_BATTERY_AH) || !solarPowerWp.inBounds(MAX_SOLAR_WP)) return null
    if (!purchaseOdometerKm.inBounds(MAX_ODOMETER_KM) || !lastOilChangeOdometerKm.inBounds(MAX_ODOMETER_KM)) return null
    val createdAtValue = parseInstant(createdAt) ?: return null
    val updatedAtValue = parseInstant(updatedAt) ?: return null
    return Vehicle(
        uuid = uuid,
        name = name,
        licensePlate = licensePlate,
        manufacturer = manufacturer,
        model = model,
        vin = vin,
        firstRegistration = firstRegistration,
        notes = notes,
        purchaseDate = purchaseDate,
        purchasePrice = purchasePriceMoney,
        purchaseOdometerKm = purchaseOdometerKm,
        saleDate = saleDate,
        salePrice = salePriceMoney,
        insurer = insurer,
        insurancePolicyNumber = insurancePolicyNumber,
        insurancePremiumPerYear = insurancePremium,
        vehicleTaxPerYear = vehicleTax,
        lengthCm = lengthCm,
        widthCm = widthCm,
        heightCm = heightCm,
        grossWeightKg = grossWeightKg,
        measuredEmptyWeightKg = measuredEmptyWeightKg,
        breakdownProvider = breakdownProvider,
        breakdownMembershipNumber = breakdownMembershipNumber,
        breakdownPhone = breakdownPhone,
        travelProtectionProvider = travelProtectionProvider,
        travelProtectionContractNumber = travelProtectionContractNumber,
        travelProtectionPhone = travelProtectionPhone,
        insurerClaimsPhone = insurerClaimsPhone,
        powerKw = powerKw,
        tireSize = tireSize,
        tirePressureFrontMbar = tirePressureFrontMbar,
        tirePressureRearMbar = tirePressureRearMbar,
        fuelTankDl = fuelTankDl,
        adBlueTankDl = adBlueTankDl,
        freshWaterTankDl = freshWaterTankDl,
        greyWaterTankDl = greyWaterTankDl,
        boilerDl = boilerDl,
        cassetteDl = cassetteDl,
        batteryCapacityAh = batteryCapacityAh,
        solarPowerWp = solarPowerWp,
        nextInspectionDate = nextInspectionDate,
        nextGasCheckDate = nextGasCheckDate,
        nextLeakTestDate = nextLeakTestDate,
        lastOilChangeDate = lastOilChangeDate,
        lastOilChangeOdometerKm = lastOilChangeOdometerKm,
        createdAt = createdAtValue,
        updatedAt = updatedAtValue,
    )
}

private fun RepairDto.toRepair(): Repair? {
    val uuid = parseUuid(uuid) ?: return null
    val date = parseDate(date) ?: return null
    val description = description.trim().takeIf { it.isNotEmpty() && it.length <= MAX_DESTINATION_LENGTH } ?: return null
    if (!odometerKm.inBounds(MAX_ODOMETER_KM)) return null
    val money = cost?.let { it.toMoney() ?: return null }
    return Repair(
        uuid = uuid,
        vehicleId = 0,
        date = date,
        description = description,
        odometerKm = odometerKm,
        cost = money,
        createdAt = parseInstant(createdAt) ?: return null,
        updatedAt = parseInstant(updatedAt) ?: return null,
    )
}

private fun LogEntryDto.toLogEntry(): LogEntry? {
    val uuid = parseUuid(uuid) ?: return null
    val date = parseDate(date) ?: return null
    val type = enumOrNull<LogType>(type) ?: return null
    return LogEntry(uuid = uuid, vehicleId = 0, type = type, date = date, createdAt = parseInstant(createdAt) ?: return null)
}

private fun Int?.inBounds(max: Int): Boolean = this == null || this in 0..max

private fun CostDto.toMoney(): Money? {
    val currency = parseCurrency(currency) ?: return null
    val value = parseDecimal(amount, currency.fractionDigits) ?: return null
    val reading = amountReading(value.movePointRight(currency.fractionDigits)) as? AmountReading.Valid ?: return null
    return Money(reading.minor, currency)
}

private fun Money.toCostDto() = CostDto(currency.currencyCode, BigDecimal.valueOf(minor, currency.fractionDigits).toPlainString())

private fun ExchangeRate.toDto() = RateDto(
    currency = currency.currencyCode,
    perEuro = perEuro.stripTrailingZeros().toPlainString(),
    date = date.toString(),
    source = source,
)

private fun Tour.toDto(vehicleUuid: String?) = TourDto(
    uuid = uuid,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    destination = destination,
    tourType = tourType.name,
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    costs = costs.map { it.toCostDto() },
    notes = notes,
    mapLink = mapLink,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    vehicleUuid = vehicleUuid,
)

private fun BackupVehicle.toDto(logEntryStationUuid: Map<String, String>) = vehicle.let { v ->
    VehicleDto(
        uuid = v.uuid,
        name = v.name,
        licensePlate = v.licensePlate,
        manufacturer = v.manufacturer,
        model = v.model,
        vin = v.vin,
        firstRegistration = v.firstRegistration?.toString(),
        notes = v.notes,
        purchaseDate = v.purchaseDate?.toString(),
        purchasePrice = v.purchasePrice?.toCostDto(),
        purchaseOdometerKm = v.purchaseOdometerKm,
        saleDate = v.saleDate?.toString(),
        salePrice = v.salePrice?.toCostDto(),
        insurer = v.insurer,
        insurancePolicyNumber = v.insurancePolicyNumber,
        insurancePremiumPerYear = v.insurancePremiumPerYear?.toCostDto(),
        vehicleTaxPerYear = v.vehicleTaxPerYear?.toCostDto(),
        lengthCm = v.lengthCm,
        widthCm = v.widthCm,
        heightCm = v.heightCm,
        grossWeightKg = v.grossWeightKg,
        measuredEmptyWeightKg = v.measuredEmptyWeightKg,
        breakdownProvider = v.breakdownProvider,
        breakdownMembershipNumber = v.breakdownMembershipNumber,
        breakdownPhone = v.breakdownPhone,
        travelProtectionProvider = v.travelProtectionProvider,
        travelProtectionContractNumber = v.travelProtectionContractNumber,
        travelProtectionPhone = v.travelProtectionPhone,
        insurerClaimsPhone = v.insurerClaimsPhone,
        powerKw = v.powerKw,
        tireSize = v.tireSize,
        tirePressureFrontMbar = v.tirePressureFrontMbar,
        tirePressureRearMbar = v.tirePressureRearMbar,
        fuelTankDl = v.fuelTankDl,
        adBlueTankDl = v.adBlueTankDl,
        freshWaterTankDl = v.freshWaterTankDl,
        greyWaterTankDl = v.greyWaterTankDl,
        boilerDl = v.boilerDl,
        cassetteDl = v.cassetteDl,
        batteryCapacityAh = v.batteryCapacityAh,
        solarPowerWp = v.solarPowerWp,
        nextInspectionDate = v.nextInspectionDate?.toString(),
        nextGasCheckDate = v.nextGasCheckDate?.toString(),
        nextLeakTestDate = v.nextLeakTestDate?.toString(),
        lastOilChangeDate = v.lastOilChangeDate?.toString(),
        lastOilChangeOdometerKm = v.lastOilChangeOdometerKm,
        createdAt = v.createdAt.toString(),
        updatedAt = v.updatedAt.toString(),
        repairs = repairs.map { json.encodeToJsonElement(RepairDto.serializer(), it.toDto()) },
        logEntries = logEntries.map { json.encodeToJsonElement(LogEntryDto.serializer(), it.toDto(logEntryStationUuid[it.uuid])) },
    )
}

private fun Repair.toDto() = RepairDto(
    uuid = uuid,
    date = date.toString(),
    description = description,
    odometerKm = odometerKm,
    cost = cost?.toCostDto(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun LogEntry.toDto(stationUuid: String?) =
    LogEntryDto(uuid = uuid, type = type.name, date = date.toString(), createdAt = createdAt.toString(), stationUuid = stationUuid)

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

private fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text) }.getOrNull()

/** Wie [parseDate], aber für den EXIF-Aufnahmezeitpunkt eines Fotos ([AttachmentDto.takenAt]). */
private fun parseLocalDateTime(text: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(text) }.getOrNull()?.takeIf { it.toLocalDate() in MIN_DATE..MAX_DATE }

private fun StationDto.toStation(): Station? {
    val uuid = parseUuid(uuid) ?: return null
    val stationType = enumOrNull<StationType>(type) ?: return null
    val stationDate = parseDate(date) ?: return null
    val stationTime = time?.let { parseTime(it) ?: return null }
    if (name.length > MAX_STATION_NAME_LENGTH || place.length > MAX_STATION_PLACE_LENGTH || notes.length > MAX_STATION_NOTES_LENGTH) {
        return null
    }
    if ((latitude == null) != (longitude == null)) return null
    if (latitude != null && longitude != null && (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE)) return null
    val source = coordinateSource?.let { enumOrNull<CoordinateSource>(it) ?: return null }
    if (accuracyM != null && (accuracyM < 0 || latitude == null)) return null
    val link = mapLink?.trim()?.ifEmpty { null }
    if (link != null && (link.length > MAX_STATION_MAP_LINK_LENGTH || !isWebUrl(link) || latitude != null)) return null
    if (nights != null && (nights < 1 || stationType != StationType.OVERNIGHT)) return null
    val kind = siteKind?.let { if (stationType != StationType.OVERNIGHT) return null else enumOrNull<SiteKind>(it) ?: return null }
    val assigned = pitchAssigned?.also { if (stationType != StationType.OVERNIGHT) return null }
    val lte = lteQuality?.let {
        if (stationType != StationType.OVERNIGHT) return null
        enumOrNull<LteQuality>(it) ?: return null
    }
    val slope = pitchSlope?.let {
        if (stationType != StationType.OVERNIGHT) return null
        enumOrNull<PitchSlope>(it) ?: return null
    }
    val blocks = levelingBlocksUsed?.also { if (stationType != StationType.OVERNIGHT) return null }

    val billing = when {
        electricityBilling != null -> {
            if (stationType != StationType.OVERNIGHT) return null
            enumOrNull<ElectricityBilling>(electricityBilling) ?: return null
        }
        electricityFlatRate != null -> {
            if (stationType != StationType.OVERNIGHT) return null
            // Unbekannter Wert der alten Strompauschale (vor Formatversion 5) wird wie ein fehlender behandelt.
            migrateLegacyElectricityFlatRate(enumOrNull<ElectricityFlatRate>(electricityFlatRate))
        }
        else -> null
    }
    val electricityCurrencyValue = electricityCurrency?.let {
        if (billing == null) return null
        parseCurrency(it) ?: return null
    }
    if (billing != null && electricityCurrencyValue == null) return null
    val flatAmountMoney = electricityFlatAmount?.let {
        if (billing == null) return null
        it.toMoneyWith(checkNotNull(electricityCurrencyValue)) ?: return null
    }
    val baseFeeMoney = electricityBaseFee?.let {
        if (billing == null) return null
        it.toMoneyWith(checkNotNull(electricityCurrencyValue)) ?: return null
    }
    val pricePerKwhValue = electricityPricePerKwh?.let {
        if (billing == null) return null
        parseKwh(it, MAX_PRICE_PER_KWH) ?: return null
    }
    val coinPriceMoney = electricityCoinPrice?.let {
        if (billing == null) return null
        it.toMoneyWith(checkNotNull(electricityCurrencyValue)) ?: return null
    }
    val coinsUsedValue = electricityCoinsUsed?.also {
        if (billing == null || it < 0 || it > MAX_ELECTRICITY_COINS) return null
    }
    val kwhPerCoinValue = electricityKwhPerCoin?.let {
        if (billing == null) return null
        parseKwh(it, MAX_ELECTRICITY_KWH) ?: return null
    }
    val meterStartValue = electricityMeterStart?.let {
        if (billing == null) return null
        parseKwh(it, MAX_ELECTRICITY_KWH) ?: return null
    }
    val meterEndValue = electricityMeterEnd?.let {
        if (billing == null) return null
        parseKwh(it, MAX_ELECTRICITY_KWH) ?: return null
    }
    val kwhUsedValue = electricityKwhUsed?.let {
        if (billing == null) return null
        parseKwh(it, MAX_ELECTRICITY_KWH) ?: return null
    }

    if (stationType != StationType.TOLL) {
        if (tollKind != null || tollPaymentMethod.isNotEmpty() || tollCountry != null || tollValidFrom != null || tollValidUntil != null) {
            return null
        }
    }
    val tollKindValue = tollKind?.let { enumOrNull<TollKind>(it) ?: return null }
    if (tollPaymentMethod.length > MAX_TOLL_PAYMENT_METHOD_LENGTH) return null
    val tollCountryValue = tollCountry?.let { if (it in ALL_COUNTRY_CODES) it else return null }
    val tollValidFromValue = tollValidFrom?.let { parseDate(it) ?: return null }
    val tollValidUntilValue = tollValidUntil?.let {
        val value = parseDate(it) ?: return null
        if (tollValidFromValue != null && value < tollValidFromValue) return null
        value
    }

    if (stationType != StationType.FERRY && ferryBookingReference.isNotEmpty()) return null
    if (ferryBookingReference.length > MAX_FERRY_BOOKING_REFERENCE_LENGTH) return null

    if (costs.size > MAX_COSTS_PER_STATION) return null
    val stationCosts = costs.map { it.toStationCost() ?: return null }
    if (stationCosts.map { it.category to it.amount.currency }.distinct().size != stationCosts.size) return null

    val allowed = stationType.allowedServices
    val stationServices = services.map { enumOrNull<StationService>(it) ?: return null }.toSet()
    if (!allowed.containsAll(stationServices)) return null
    val weatherSnapshot = weather?.let { it.toWeather() ?: return null }
    return Station(
        uuid = uuid,
        vehicleId = 0,
        tourId = null,
        type = stationType,
        date = stationDate,
        time = stationTime,
        name = name,
        place = place,
        latitude = latitude,
        longitude = longitude,
        coordinateSource = source,
        accuracyM = accuracyM,
        mapLink = link,
        notes = notes,
        nights = nights,
        siteKind = kind,
        pitchAssigned = assigned,
        lteQuality = lte,
        pitchSlope = slope,
        levelingBlocksUsed = blocks,
        electricityBilling = billing,
        electricityCurrency = electricityCurrencyValue,
        electricityFlatAmount = flatAmountMoney,
        electricityBaseFee = baseFeeMoney,
        electricityPricePerKwh = pricePerKwhValue,
        electricityCoinPrice = coinPriceMoney,
        electricityCoinsUsed = coinsUsedValue,
        electricityKwhPerCoin = kwhPerCoinValue,
        electricityMeterStart = meterStartValue,
        electricityMeterEnd = meterEndValue,
        electricityKwhUsed = kwhUsedValue,
        tollKind = tollKindValue,
        tollPaymentMethod = tollPaymentMethod,
        tollCountry = tollCountryValue,
        tollValidFrom = tollValidFromValue,
        tollValidUntil = tollValidUntilValue,
        ferryBookingReference = ferryBookingReference,
        costs = stationCosts,
        services = stationServices,
        weather = weatherSnapshot,
        favorite = favorite,
        createdAt = parseInstant(createdAt) ?: return null,
        updatedAt = parseInstant(updatedAt) ?: return null,
    )
}

/** Dezimalzahl mit höchstens 6 Nachkommastellen, nicht negativ und höchstens [max]. */
private fun parseKwh(text: String, max: Double): BigDecimal? {
    val value = parseDecimal(text, maxFractionDigits = 6) ?: return null
    return value.takeIf { it.signum() >= 0 && it <= BigDecimal.valueOf(max) }
}

/** Wie [CostDto.toMoney], aber mit extern vorgegebener [currency] statt einem eigenen Feld dafür. */
private fun String.toMoneyWith(currency: Currency): Money? {
    val value = parseDecimal(this, currency.fractionDigits) ?: return null
    val reading = amountReading(value.movePointRight(currency.fractionDigits)) as? AmountReading.Valid ?: return null
    return Money(reading.minor, currency)
}

private fun StationCostDto.toStationCost(): StationCost? {
    val categoryValue = enumOrNull<CostCategory>(category) ?: return null
    val currencyValue = parseCurrency(currency) ?: return null
    val money = amount.toMoneyWith(currencyValue) ?: return null
    if (note.length > MAX_NOTES_LENGTH) return null
    return StationCost(categoryValue, money, note)
}

private fun VehicleDocumentDto.toVehicleDocument(): VehicleDocument? {
    val uuid = parseUuid(uuid) ?: return null
    val kindValue = enumOrNull<DocumentKind>(kind) ?: return null
    val titleValue = title.trim().takeIf { it.length <= MAX_DESTINATION_LENGTH } ?: return null
    val expiry = expiryDate?.let { parseDate(it) ?: return null }
    return VehicleDocument(
        uuid = uuid,
        vehicleId = 0,
        kind = kindValue,
        title = titleValue,
        expiryDate = expiry,
        createdAt = parseInstant(createdAt) ?: return null,
        updatedAt = parseInstant(updatedAt) ?: return null,
    )
}

private fun BackupVehicleDocument.toDto() = VehicleDocumentDto(
    uuid = document.uuid,
    vehicleUuid = vehicleUuid,
    kind = document.kind.name,
    title = document.title,
    expiryDate = document.expiryDate?.toString(),
    createdAt = document.createdAt.toString(),
    updatedAt = document.updatedAt.toString(),
)

/** Prüft Dateiname, MIME-Typ, Abmessungen und Koordinaten eines Anhangs; `null` bei jeder Unstimmigkeit, nicht nur offensichtlich falschen Werten. */
private fun AttachmentDto.toAttachment(ownerType: AttachmentOwnerType): Attachment? {
    val uuid = parseUuid(uuid) ?: return null
    val extension = ATTACHMENT_EXTENSION_BY_MIME_TYPE[mimeType] ?: return null
    if (!ATTACHMENT_FILE_NAME_PATTERN.matches(fileName) || !fileName.endsWith(".$extension")) return null
    if (sizeBytes < 0 || sizeBytes > MAX_ATTACHMENT_SIZE_BYTES) return null
    if (caption.length > MAX_CAPTION_LENGTH) return null
    val isPhoto = mimeType.startsWith("image/")
    if (!isPhoto && (width != null || height != null)) return null
    if (isPhoto && (width == null || height == null)) return null
    if (width != null && width !in 1..MAX_ATTACHMENT_DIMENSION) return null
    if (height != null && height !in 1..MAX_ATTACHMENT_DIMENSION) return null
    if ((latitude == null) != (longitude == null)) return null
    if (latitude != null && longitude != null && (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE)) return null
    val takenAtValue = takenAt?.let { parseLocalDateTime(it) ?: return null }
    return Attachment(
        uuid = uuid,
        ownerType = ownerType,
        ownerId = 0,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        latitude = latitude,
        longitude = longitude,
        takenAt = takenAtValue,
        caption = caption,
        createdAt = parseInstant(createdAt) ?: return null,
    )
}

private fun BackupAttachment.toDto() = AttachmentDto(
    uuid = attachment.uuid,
    ownerType = attachment.ownerType.name,
    ownerUuid = ownerUuid,
    fileName = attachment.fileName,
    mimeType = attachment.mimeType,
    sizeBytes = attachment.sizeBytes,
    width = attachment.width,
    height = attachment.height,
    latitude = attachment.latitude,
    longitude = attachment.longitude,
    takenAt = attachment.takenAt?.toString(),
    caption = attachment.caption,
    createdAt = attachment.createdAt.toString(),
    zipPath = zipPath,
)

private fun WeatherDto.toWeather(): WeatherSnapshot? {
    if (temperatureDeciC !in -MAX_WEATHER_TEMPERATURE_DECI_C..MAX_WEATHER_TEMPERATURE_DECI_C) return null
    if (weatherCode < 0 || weatherCode > MAX_WEATHER_CODE) return null
    if (windKmh < 0 || windKmh > MAX_WEATHER_WIND_KMH) return null
    if (gustKmh != null && (gustKmh < 0 || gustKmh > MAX_WEATHER_WIND_KMH)) return null
    if (windDirectionDeg != null && (windDirectionDeg < 0 || windDirectionDeg > MAX_WEATHER_WIND_DIRECTION_DEG)) return null
    return WeatherSnapshot(
        temperatureDeciC = temperatureDeciC,
        weatherCode = weatherCode,
        windKmh = windKmh,
        gustKmh = gustKmh,
        windDirectionDeg = windDirectionDeg,
        observedAt = parseInstant(observedAt) ?: return null,
    )
}

private fun Station.toDto(vehicleUuid: String, tourUuid: String?) = StationDto(
    uuid = uuid,
    type = type.name,
    date = date.toString(),
    time = time?.toString(),
    name = name,
    place = place,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource?.name,
    accuracyM = accuracyM,
    mapLink = mapLink,
    notes = notes,
    nights = nights,
    siteKind = siteKind?.name,
    pitchAssigned = pitchAssigned,
    lteQuality = lteQuality?.name,
    pitchSlope = pitchSlope?.name,
    levelingBlocksUsed = levelingBlocksUsed,
    electricityFlatRate = null,
    electricityBilling = electricityBilling?.name,
    electricityCurrency = electricityCurrency?.currencyCode,
    electricityFlatAmount = electricityFlatAmount?.toCostDto()?.amount,
    electricityBaseFee = electricityBaseFee?.toCostDto()?.amount,
    electricityPricePerKwh = electricityPricePerKwh?.stripTrailingZeros()?.toPlainString(),
    electricityCoinPrice = electricityCoinPrice?.toCostDto()?.amount,
    electricityCoinsUsed = electricityCoinsUsed,
    electricityKwhPerCoin = electricityKwhPerCoin?.stripTrailingZeros()?.toPlainString(),
    electricityMeterStart = electricityMeterStart?.stripTrailingZeros()?.toPlainString(),
    electricityMeterEnd = electricityMeterEnd?.stripTrailingZeros()?.toPlainString(),
    electricityKwhUsed = electricityKwhUsed?.stripTrailingZeros()?.toPlainString(),
    tollKind = tollKind?.name,
    tollPaymentMethod = tollPaymentMethod,
    tollCountry = tollCountry,
    tollValidFrom = tollValidFrom?.toString(),
    tollValidUntil = tollValidUntil?.toString(),
    ferryBookingReference = ferryBookingReference,
    costs = costs.map { it.toDto() },
    services = services.map { it.name },
    weather = weather?.toDto(),
    favorite = favorite,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    vehicleUuid = vehicleUuid,
    tourUuid = tourUuid,
)

private fun StationCost.toDto() = StationCostDto(category.name, amount.currency.currencyCode, amount.toCostDto().amount, note)

private fun WeatherSnapshot.toDto() = WeatherDto(
    temperatureDeciC = temperatureDeciC,
    weatherCode = weatherCode,
    windKmh = windKmh,
    gustKmh = gustKmh,
    windDirectionDeg = windDirectionDeg,
    observedAt = observedAt.toString(),
)
