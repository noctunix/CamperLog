package app.restvolt.camperlog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import app.restvolt.camperlog.domain.LATITUDE_RANGE
import app.restvolt.camperlog.domain.LONGITUDE_RANGE
import app.restvolt.camperlog.domain.MAX_DOCUMENT_BYTES
import app.restvolt.camperlog.domain.extensionFor
import app.restvolt.camperlog.domain.readSniffHeader
import app.restvolt.camperlog.domain.sniffMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Unterordner von `filesDir`, in dem alle Anhangsdateien liegen; siehe `res/xml/file_paths.xml`. */
const val ATTACHMENTS_DIR = "attachments"

/** Lange Kante, auf die ein importiertes Foto herunterskaliert wird. */
internal const val MAX_PHOTO_DIMENSION = 2048

/** JPEG-Qualität der Neukodierung importierter Fotos. */
internal const val PHOTO_JPEG_QUALITY = 85

/** Eine erfolgreich importierte Anhangsdatei, bereit für [app.restvolt.camperlog.domain.Attachment]. */
sealed interface ImportedAttachment {
    val fileName: String
    val mimeType: String
    val sizeBytes: Long

    /**
     * Foto nach Herunterskalieren und Anwenden der EXIF-Drehung auf die Pixel; die Datei behält nur die
     * GPS- und Aufnahmezeit-EXIF-Tags des Originals, alles andere entfällt (siehe [applyExifOrientation]
     * und [copyLocationAndCaptureTimeExif]). [latitude]/[longitude]/[takenAt] sind aus denselben Tags gelesen.
     */
    data class Photo(
        override val fileName: String,
        override val mimeType: String,
        override val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val takenAt: LocalDateTime? = null,
    ) : ImportedAttachment

    /** Dokument, unverändert übernommen. */
    data class Document(override val fileName: String, override val mimeType: String, override val sizeBytes: Long) : ImportedAttachment
}

/** Warum eine Datei nicht importiert werden konnte. */
enum class AttachmentImportError {
    /** Die Quelle lässt sich nicht (mehr) öffnen. */
    UNREADABLE,

    /** Weder als Bild noch als PDF erkannt (geprüft über die ersten Bytes, nicht über Dateiendung oder angegebenen MIME-Typ). */
    UNSUPPORTED_TYPE,

    /** Größer als [MAX_DOCUMENT_BYTES] (nur Dokumente; Fotos werden stattdessen herunterskaliert). */
    TOO_LARGE,

    /** Die ersten Bytes sahen nach einem unterstützten Bildformat aus, aber die Bilddaten selbst waren nicht decodierbar. */
    DECODE_FAILED,
}

/** Ergebnis von [AttachmentFileStore.importPhoto]/[AttachmentFileStore.importDocument]. */
sealed interface AttachmentImportResult {
    data class Success(val attachment: ImportedAttachment) : AttachmentImportResult
    data class Failure(val error: AttachmentImportError) : AttachmentImportResult
}

/**
 * Import- und Dateiverwaltung für Anhänge in `filesDir/attachments/`. Die Zuordnung zu einem Eintrag
 * (Station, Reparatur, Bordbuch, Fahrzeugdokument) ist Sache von
 * [app.restvolt.camperlog.domain.AttachmentRepository]; dieser Typ kennt nur Dateien.
 *
 * Der MIME-Typ wird nie aus der angegebenen Dateiendung oder dem von `ContentResolver` gemeldeten Typ
 * übernommen, sondern immer über die ersten Bytes der Datei bestimmt ([sniffMimeType]): eine falsch
 * benannte oder mit einem irreführenden MIME-Typ geteilte Datei soll nicht als etwas anderes importiert
 * werden, als sie tatsächlich ist.
 */
interface AttachmentFileStore {
    /**
     * Importiert [source] als Foto: herunterskaliert auf höchstens [MAX_PHOTO_DIMENSION] px lange
     * Kante, EXIF-Drehung auf die Pixel angewendet, als JPEG ohne EXIF-Daten neu kodiert.
     */
    suspend fun importPhoto(source: Uri): AttachmentImportResult

    /** Importiert [source] unverändert als Dokument (PDF oder Bild), bis [MAX_DOCUMENT_BYTES]. */
    suspend fun importDocument(source: Uri): AttachmentImportResult

    /** Die Datei zu [fileName], relativ zum Anhangs-Ordner. */
    fun file(fileName: String): File

    /**
     * Schreibt [latitude]/[longitude] als GPS-EXIF in die Datei [fileName] (für
     * [app.restvolt.camperlog.domain.AttachmentRepository.setLocation]); ein No-op, wenn die Datei
     * nicht existiert.
     */
    suspend fun writeLocation(fileName: String, latitude: Double, longitude: Double)

    /** Löscht die Datei zu [fileName], falls noch vorhanden. */
    suspend fun delete(fileName: String)

    /**
     * Löscht Dateien im Anhangs-Ordner, die nicht in [knownFileNames] stehen und seit mindestens
     * einem Tag vor [now] zuletzt geändert wurden - ein frisch importierter, noch nicht gespeicherter
     * Anhang (z. B. während der Undo-Zeit nach dem Löschen) bleibt dadurch unangetastet.
     */
    suspend fun sweepOrphanFiles(knownFileNames: Set<String>, now: Instant)
}

/** [AttachmentFileStore] über den Application-Context, damit er keine Activity festhält. */
class AndroidAttachmentFileStore(context: Context) : AttachmentFileStore {
    private val context = context.applicationContext

    private val dir: File by lazy { File(this.context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() } }

    override fun file(fileName: String): File = File(dir, fileName)

    override suspend fun importPhoto(source: Uri): AttachmentImportResult = withContext(Dispatchers.IO) {
        val header = readHeader(source) ?: return@withContext unreadable()
        val sniffed = sniffMimeType(header)?.takeIf { it.startsWith("image/") } ?: return@withContext unsupportedType()

        val bounds = decodeBounds(source) ?: return@withContext decodeFailed()
        val sampleSize = calculateInSampleSize(bounds.first, bounds.second, MAX_PHOTO_DIMENSION)
        var bitmap = decodeSampled(source, sampleSize) ?: return@withContext decodeFailed()
        val sourceExif = readSourceExif(source)
        bitmap = applyExifOrientation(bitmap, sourceExif.orientation)
        bitmap = downscaleIfNeeded(bitmap, MAX_PHOTO_DIMENSION)

        val fileName = "${UUID.randomUUID()}.jpg"
        val target = file(fileName)
        try {
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out) }
            copyLocationAndCaptureTimeExif(target, sourceExif)
        } finally {
            bitmap.recycle()
        }
        AttachmentImportResult.Success(
            ImportedAttachment.Photo(
                fileName = fileName,
                mimeType = "image/jpeg",
                sizeBytes = target.length(),
                width = bitmap.width,
                height = bitmap.height,
                latitude = sourceExif.latitude,
                longitude = sourceExif.longitude,
                takenAt = sourceExif.takenAt,
            ),
        )
    }

    override suspend fun importDocument(source: Uri): AttachmentImportResult = withContext(Dispatchers.IO) {
        val header = readHeader(source) ?: return@withContext unreadable()
        val sniffed = sniffMimeType(header) ?: return@withContext unsupportedType()
        val fileName = "${UUID.randomUUID()}${extensionFor(sniffed)}"
        val target = file(fileName)
        val copied = try {
            copyCapped(source, target, MAX_DOCUMENT_BYTES)
        } catch (_: IOException) {
            target.delete()
            return@withContext unreadable()
        }
        if (copied == null) {
            target.delete()
            return@withContext AttachmentImportResult.Failure(AttachmentImportError.TOO_LARGE)
        }
        AttachmentImportResult.Success(ImportedAttachment.Document(fileName, sniffed, copied))
    }

    override suspend fun writeLocation(fileName: String, latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        val target = file(fileName)
        if (!target.exists()) return@withContext
        val exif = ExifInterface(target)
        exif.setLatLong(latitude, longitude)
        exif.saveAttributes()
    }

    override suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        file(fileName).delete()
        Unit
    }

    override suspend fun sweepOrphanFiles(knownFileNames: Set<String>, now: Instant) = withContext(Dispatchers.IO) {
        val cutoffMillis = now.minus(1, ChronoUnit.DAYS).toEpochMilli()
        dir.listFiles()
            ?.filter { it.name !in knownFileNames && it.lastModified() <= cutoffMillis }
            ?.forEach { it.delete() }
        Unit
    }

    private fun readHeader(source: Uri): ByteArray? = openStream(source)?.use(::readSniffHeader)?.takeIf { it.isNotEmpty() }

    /**
     * Breite/Höhe ohne vollständige Pixel-Decodierung, siehe `BitmapFactory.Options.inJustDecodeBounds`.
     * Fängt auch Ausnahmen ab: Ein als Bild erkannter Dateianfang (siehe [sniffMimeType]) garantiert
     * nicht, dass der Decoder mit dem Rest der Datei zurechtkommt.
     */
    private fun decodeBounds(source: Uri): Pair<Int, Int>? = runCatching {
        openStream(source)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            options.outWidth.takeIf { it > 0 }?.let { it to options.outHeight }
        }
    }.getOrNull()

    private fun decodeSampled(source: Uri, sampleSize: Int): Bitmap? = runCatching {
        openStream(source)?.use { stream ->
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    /** Aus dem Original gelesene EXIF-Angaben, die der Import ins neue JPEG übernimmt (siehe [copyLocationAndCaptureTimeExif]). */
    private fun readSourceExif(source: Uri): SourceExif {
        val exif = openStream(source)?.use { stream -> runCatching { ExifInterface(stream) }.getOrNull() }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        val latLong = exif?.latLong?.takeIf { it[0] in LATITUDE_RANGE && it[1] in LONGITUDE_RANGE }
        val takenAt = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::parseExifDateTime)
        val copiedTags = COPIED_EXIF_TAGS.mapNotNull { tag -> exif?.getAttribute(tag)?.let { tag to it } }.toMap()
        return SourceExif(orientation, latLong?.get(0), latLong?.get(1), takenAt, copiedTags)
    }

    /** Kopiert [source] nach [target]; liefert `null` (und bricht ab), sobald mehr als [maxBytes] gelesen wurden. */
    private fun copyCapped(source: Uri, target: File, maxBytes: Long): Long? {
        val input = openStream(source) ?: return null
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                return total
            }
        }
    }

    private fun openStream(source: Uri): InputStream? = try {
        context.contentResolver.openInputStream(source)
    } catch (_: SecurityException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun unreadable() = AttachmentImportResult.Failure(AttachmentImportError.UNREADABLE)
    private fun unsupportedType() = AttachmentImportResult.Failure(AttachmentImportError.UNSUPPORTED_TYPE)
    private fun decodeFailed() = AttachmentImportResult.Failure(AttachmentImportError.DECODE_FAILED)
}

/**
 * Verschiebt nach einem erfolgreichen ZIP-Import die in [stagedFiles] (Dateiname zu temporärer Datei)
 * zwischengelagerten Dateien in [fileStore]. Aufgerufen erst, nachdem der Datenbank-Import ohne Fehler
 * durchgelaufen ist (siehe `DataViewModel.startImport`); bei einem Fehler löscht der Aufrufer die
 * Zwischenablage stattdessen ungenutzt. Eine Datei, die sich weder verschieben noch kopieren lässt
 * (etwa bei vollem Speicher), bricht die übrigen nicht ab; eine halb geschriebene Kopie wird entfernt.
 *
 * @return Anzahl der Dateien, die nicht übernommen werden konnten; ihre Anhänge stehen ohne Datei in
 *   der Datenbank, der Aufrufer meldet das dem Benutzer
 */
suspend fun commitStagedAttachmentFiles(fileStore: AttachmentFileStore, stagedFiles: Map<String, File>): Int = withContext(Dispatchers.IO) {
    var failed = 0
    for ((fileName, staged) in stagedFiles) {
        val target = fileStore.file(fileName)
        if (staged.renameTo(target)) continue
        try {
            staged.copyTo(target, overwrite = true)
            staged.delete()
        } catch (_: IOException) {
            target.delete()
            failed++
        }
    }
    failed
}

/** `inSampleSize` für `BitmapFactory.Options`, sodass die lange Kante nach dem Sampling mindestens [maxDimension] erreicht. */
internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    val longEdge = maxOf(width, height)
    while (longEdge / (sampleSize * 2) >= maxDimension) sampleSize *= 2
    return sampleSize
}

/** Wendet die EXIF-Drehung/-Spiegelung [orientation] auf die Pixel von [bitmap] an; recycelt [bitmap], falls dafür eine Kopie nötig ist. */
internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

/** Skaliert [bitmap] herunter, falls seine lange Kante über [maxDimension] liegt; recycelt [bitmap], falls dafür eine Kopie nötig ist. */
internal fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longEdge
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = bitmap.scale(width, height)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

/** Aus einem Original-Foto gelesene Angaben, die [copyLocationAndCaptureTimeExif] unverändert in die neue Datei überträgt. */
private data class SourceExif(
    val orientation: Int,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: LocalDateTime?,
    val copiedTags: Map<String, String>,
)

/** GPS- und Aufnahmezeit-Tags, die der Import unverändert vom Original übernimmt; alle anderen EXIF-Daten entfallen. */
private val COPIED_EXIF_TAGS = listOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_DATETIME_ORIGINAL,
)

private val EXIF_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

private fun parseExifDateTime(text: String): LocalDateTime? = try {
    LocalDateTime.parse(text, EXIF_DATETIME_FORMAT)
} catch (_: DateTimeParseException) {
    null
}

/** Schreibt [exif].[SourceExif.copiedTags] in [target] und setzt die Orientation explizit auf normal; alles andere EXIF bleibt weg. */
private fun copyLocationAndCaptureTimeExif(target: File, exif: SourceExif) {
    val targetExif = ExifInterface(target)
    for ((tag, value) in exif.copiedTags) targetExif.setAttribute(tag, value)
    targetExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    targetExif.saveAttributes()
}
