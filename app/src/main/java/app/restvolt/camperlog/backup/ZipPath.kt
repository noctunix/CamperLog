package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Tour
import java.time.format.DateTimeFormatter

/*
 * Baut menschenlesbare ZIP-Pfade für die Anhangsdateien einer Sicherung (siehe docs/ATTACHMENTS.md,
 * Abschnitt "Backup"): feste Ordnernamen immer Englisch, nutzereigene Namen (Tour, Station, Fahrzeug,
 * Dokumenttitel) wie eingegeben, nur sanitisiert. Lesende Seite (Import) traut diesen Pfaden nicht -
 * sie validiert sie nur strukturell (siehe [isValidZipPath]) und bildet sie sonst 1:1 über
 * `backup.json` auf Anhänge ab, siehe `readBackupZip`.
 */

internal const val ZIP_PHOTOS_SEGMENT = "Photos"
internal const val ZIP_DOCUMENTS_SEGMENT = "Documents"
internal const val ZIP_NO_TOUR_SEGMENT = "No tour"
internal const val ZIP_REPAIRS_SEGMENT = "Repairs"
internal const val ZIP_LOGBOOK_SEGMENT = "Logbook"

internal const val MAX_ZIP_PATH_SEGMENT_LENGTH = 80
private const val MAX_ZIP_PATH_SEGMENTS = 10
private const val MAX_ZIP_PATH_LENGTH = 1000

private val ZIP_PATH_FORBIDDEN_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

private val ZIP_TOUR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")

/** Feste, englische Kurzbeschreibung eines Bordbuch-Eintrags für seinen Dateinamen im ZIP. */
private val ZIP_LOG_TYPE_LABEL = mapOf(
    LogType.CASSETTE_EMPTIED to "Cassette emptied",
    LogType.GREY_WATER_EMPTIED to "Grey water emptied",
    LogType.DIESEL_HEATER_RUN to "Diesel heater run",
    LogType.GAS_HEATER_RUN to "Gas heater run",
    LogType.GAS_BOTTLE_SWAPPED to "Gas bottle swapped",
)

/**
 * Entfernt in Dateisystempfaden verbotene Zeichen und Steuerzeichen aus [raw], trimmt Leerraum und
 * Punkte am Rand; liefert [fallback], wenn danach nichts übrig bleibt.
 */
internal fun sanitizeZipName(raw: String, fallback: String): String {
    val cleaned = raw.filterNot { it in ZIP_PATH_FORBIDDEN_CHARS || it.code < 0x20 }.trim().trim('.', ' ').trim()
    return cleaned.ifEmpty { fallback }
}

/** Kappt [text] auf höchstens [MAX_ZIP_PATH_SEGMENT_LENGTH] Zeichen. */
private fun capZipSegment(text: String): String =
    if (text.length <= MAX_ZIP_PATH_SEGMENT_LENGTH) text else text.take(MAX_ZIP_PATH_SEGMENT_LENGTH).trimEnd()

/**
 * Ob [path] ein sicherer, relativer ZIP-Pfad ist: kein führendes `/`, keine `.`/`..`-Teilstücke, keine
 * in Dateisystempfaden verbotenen oder Steuerzeichen, begrenzte Segmentlänge und -anzahl. Reine
 * Formprüfung ohne Bezug zu `backup.json` - die eigentliche Zuordnung zu einem Anhang übernimmt
 * `readBackupZip`.
 */
internal fun isValidZipPath(path: String): Boolean {
    if (path.isEmpty() || path.length > MAX_ZIP_PATH_LENGTH || path.startsWith("/")) return false
    val segments = path.split("/")
    if (segments.size > MAX_ZIP_PATH_SEGMENTS) return false
    return segments.all { it.isValidZipPathSegment() }
}

private fun String.isValidZipPathSegment(): Boolean =
    isNotEmpty() && this != "." && this != ".." && length <= MAX_ZIP_PATH_SEGMENT_LENGTH &&
        none { it.code < 0x20 } && none { it in ZIP_PATH_FORBIDDEN_CHARS }

/** Ordner- und Dateinamensteil eines Anhangs vor der Eindeutigmachung innerhalb seines Ordners. */
private data class ZipPathCandidate(val attachmentUuid: String, val folder: List<String>, val stem: String, val extension: String)

/**
 * Berechnet für jeden Anhang von [backup] seinen ZIP-Pfad (Schlüssel: [app.restvolt.camperlog.domain.Attachment.uuid]).
 * Fotos (Stationen, Reparaturen, Bordbuch) liegen unter `Photos/…`, Fahrzeugdokumente unter
 * `Documents/<Fahrzeug>/…`; Namen werden je Ordner mit Suffix " 2", " 3" … eindeutig gemacht
 * (Großschreibung dabei ignoriert, damit auf einem case-insensitiven Dateisystem keine zwei
 * Einträge kollidieren). Setzt voraus, dass jede in [backup] referenzierte Station, Tour, Reparatur,
 * Bordbuch-Eintrag und jedes Fahrzeugdokument tatsächlich in [backup] enthalten ist - beim Export der
 * Fall, da [backup] aus denselben Repositorys gebaut wird (siehe `buildBackup`).
 */
internal fun buildAttachmentZipPaths(backup: Backup): Map<String, String> {
    val stationByUuid = backup.stations.associateBy { it.uuid }
    val tourByUuid = backup.tours.associateBy { it.uuid }
    val vehicleByUuid = backup.vehicles.associate { it.vehicle.uuid to it.vehicle }
    val repairByUuid = HashMap<String, Repair>()
    val repairVehicleUuid = HashMap<String, String>()
    val logEntryByUuid = HashMap<String, LogEntry>()
    for (backupVehicle in backup.vehicles) {
        for (repair in backupVehicle.repairs) {
            repairByUuid[repair.uuid] = repair
            repairVehicleUuid[repair.uuid] = backupVehicle.vehicle.uuid
        }
        for (entry in backupVehicle.logEntries) logEntryByUuid[entry.uuid] = entry
    }
    val documentByUuid = backup.documents.associate { it.document.uuid to it.document }
    val documentVehicleUuid = backup.documents.associate { it.document.uuid to it.vehicleUuid }

    val photoIndex = HashMap<String, Int>()
    fun nextPhotoIndex(ownerUuid: String): Int {
        val next = (photoIndex[ownerUuid] ?: 0) + 1
        photoIndex[ownerUuid] = next
        return next
    }

    val candidates = backup.attachments.map { backupAttachment ->
        val attachment = backupAttachment.attachment
        val ownerUuid = backupAttachment.ownerUuid
        when (attachment.ownerType) {
            AttachmentOwnerType.STATION -> {
                val station = stationByUuid.getValue(ownerUuid)
                val tourUuid = backup.stationTourUuid[ownerUuid]
                val folder = if (tourUuid != null) {
                    listOf(ZIP_PHOTOS_SEGMENT, tourSegment(tourByUuid.getValue(tourUuid)))
                } else {
                    listOf(ZIP_PHOTOS_SEGMENT, ZIP_NO_TOUR_SEGMENT)
                }
                val n = nextPhotoIndex(ownerUuid)
                val stem = "${station.date} ${sanitizeZipName(station.name, "Stop")} $n"
                ZipPathCandidate(attachment.uuid, folder, stem, "jpg")
            }
            AttachmentOwnerType.REPAIR -> {
                val repair = repairByUuid.getValue(ownerUuid)
                val vehicleName = vehicleByUuid[repairVehicleUuid[ownerUuid]]?.name.orEmpty()
                val folder = listOf(ZIP_PHOTOS_SEGMENT, ZIP_REPAIRS_SEGMENT, sanitizeZipName(vehicleName, "Vehicle"))
                val n = nextPhotoIndex(ownerUuid)
                val stem = "${repair.date} ${sanitizeZipName(repair.description, "Repair")} $n"
                ZipPathCandidate(attachment.uuid, folder, stem, "jpg")
            }
            AttachmentOwnerType.LOG_ENTRY -> {
                val entry = logEntryByUuid.getValue(ownerUuid)
                val folder = listOf(ZIP_PHOTOS_SEGMENT, ZIP_LOGBOOK_SEGMENT)
                val n = nextPhotoIndex(ownerUuid)
                val stem = "${entry.date} ${ZIP_LOG_TYPE_LABEL.getValue(entry.type)} $n"
                ZipPathCandidate(attachment.uuid, folder, stem, "jpg")
            }
            AttachmentOwnerType.VEHICLE_DOCUMENT -> {
                val document = documentByUuid.getValue(ownerUuid)
                val vehicleName = vehicleByUuid[documentVehicleUuid[ownerUuid]]?.name.orEmpty()
                val folder = listOf(ZIP_DOCUMENTS_SEGMENT, sanitizeZipName(vehicleName, "Vehicle"))
                val stem = sanitizeZipName(document.title, "Document")
                ZipPathCandidate(attachment.uuid, folder, stem, ATTACHMENT_EXTENSION_BY_MIME_TYPE.getValue(attachment.mimeType))
            }
        }
    }

    val usedNamesByFolder = HashMap<String, MutableSet<String>>()
    val result = LinkedHashMap<String, String>()
    for (candidate in candidates) {
        val folder = candidate.folder.map(::capZipSegment)
        val folderKey = folder.joinToString("/") { it.lowercase() }
        val used = usedNamesByFolder.getOrPut(folderKey) { mutableSetOf() }
        val stem = capZipSegment(candidate.stem)
        var suffix = 1
        var fileName: String
        while (true) {
            fileName = if (suffix == 1) "$stem.${candidate.extension}" else "$stem $suffix.${candidate.extension}"
            if (used.add(fileName.lowercase())) break
            suffix++
        }
        result[candidate.attachmentUuid] = (folder + fileName).joinToString("/")
    }
    return result
}

private fun tourSegment(tour: Tour): String =
    "${tour.startDate.format(ZIP_TOUR_MONTH_FORMAT)} ${sanitizeZipName(tour.destination, "Tour")}"
