package app.restvolt.camperlog.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Name des JSON-Eintrags einer ZIP-Sicherung. */
const val BACKUP_ZIP_JSON_ENTRY = "backup.json"

/**
 * Zu schreibende Sicherung: [json] ist immer gesetzt, [writeZip] nur, wenn Fotos und Dokumente mit
 * eingeschlossen werden sollen - dann ersetzt die ZIP-Datei die reine JSON-Datei, statt neben ihr zu
 * stehen (siehe [app.restvolt.camperlog.reminders.ReminderCheckRunner]). [writeZip] schreibt die
 * ZIP-Sicherung direkt in den übergebenen Strom (siehe [writeBackupZip]), statt sie vorher vollständig
 * im Speicher aufzubauen - bei bis zu 200 MB an Anhängen würde das auf knappen Geräten ein
 * `OutOfMemoryError` riskieren.
 */
class BackupPayload(val json: String, val writeZip: ((OutputStream) -> Unit)?)

/** Größte insgesamt aus einer ZIP-Sicherung entpackte Menge an Bytes, gegen eine Zip-Bombe. */
internal const val MAX_BACKUP_ZIP_TOTAL_BYTES = 500L * 1024 * 1024

private val ATTACHMENT_ZIP_ENTRY_PATTERN = Regex("""files/${ATTACHMENT_FILE_NAME_PATTERN.pattern}""")

/**
 * Geschätzte Größe einer ZIP-Sicherung zur Anzeige vor dem Export: der JSON-Text plus alle
 * eindeutigen Anhangsdateien (mehrfach verwendete Dateien, falls es die je gäbe, zählen nur einmal).
 */
fun backupZipSizeEstimate(json: String, attachments: List<BackupAttachment>): Long =
    json.toByteArray(Charsets.UTF_8).size.toLong() + attachments.distinctBy { it.attachment.fileName }.sumOf { it.attachment.sizeBytes }

/**
 * Schreibt [json] und, sofern [includeFiles], die Dateien der eindeutigen [attachments] als ZIP nach
 * [output]. [fileContent] liefert den Inhalt einer Datei oder `null`, wenn sie nicht (mehr) existiert;
 * ein fehlender Anhang lässt den Export nicht scheitern, sein Eintrag fehlt dann einfach in der ZIP-Datei.
 */
fun writeBackupZip(
    output: OutputStream,
    json: String,
    attachments: List<BackupAttachment>,
    includeFiles: Boolean,
    fileContent: (fileName: String) -> InputStream?,
) {
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry(BACKUP_ZIP_JSON_ENTRY))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        if (includeFiles) {
            val written = HashSet<String>()
            for (backupAttachment in attachments) {
                val fileName = backupAttachment.attachment.fileName
                if (!written.add(fileName)) continue
                fileContent(fileName)?.use { input ->
                    zip.putNextEntry(ZipEntry("files/$fileName"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }
}

/** Ergebnis von [readBackupZip]. */
sealed interface BackupZipReadResult {
    /**
     * [backup] ist bereits vollständig geprüft (wie [decodeBackup]); [stagedFiles] ordnet jedem
     * extrahierten und gegen seine Metadaten geprüften [app.restvolt.camperlog.domain.Attachment.fileName]
     * seine temporäre Datei zu - sie liegt noch nicht am endgültigen Ort.
     */
    data class Success(val backup: Backup, val stagedFiles: Map<String, File>) : BackupZipReadResult

    /** Wie [BackupReadResult.Failure]; Positionsangaben sind nur bei einem fehlerhaften `backup.json`-Inhalt gesetzt. */
    data class Failure(val failure: BackupReadResult.Failure) : BackupZipReadResult
}

/**
 * Liest eine ZIP-Sicherung aus [input] in [stagingDir] (muss existieren oder anlegbar sein). Nur die
 * Einträge [BACKUP_ZIP_JSON_ENTRY] und `files/<uuid>.<ext>` sind erlaubt; jeder andere Eintragsname
 * lässt den Import ohne jede Seitenwirkung scheitern (kein Pfad-Traversal, keine unbekannten
 * Einträge). Jede Datei wird einzeln bis zur für ihren MIME-Typ gültigen Obergrenze und insgesamt bis
 * [MAX_BACKUP_ZIP_TOTAL_BYTES] gelesen - gezählt an den tatsächlich gelesenen Bytes, nicht an der von
 * der ZIP-Datei behaupteten Größe, da sich sonst eine Zip-Bombe durchschmuggeln ließe. Nach dem
 * Entpacken wird jede Datei gegen die Metadaten in `backup.json` geprüft: ihr Name muss zu genau einem
 * [Backup.attachments]-Eintrag gehören, dessen Größe und per Inhalt erkannter MIME-Typ übereinstimmen
 * müssen. Scheitert irgendeine Prüfung, werden alle bereits geschriebenen Dateien in [stagingDir]
 * wieder gelöscht; das endgültige Verschieben an den Ziel-Ort ist Sache des Aufrufers, nach
 * erfolgreichem Datenbank-Import.
 */
fun readBackupZip(input: InputStream, stagingDir: File): BackupZipReadResult {
    stagingDir.mkdirs()
    val stagedFiles = mutableMapOf<String, File>()
    var backupJsonText: String? = null
    var totalBytes = 0L

    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == BACKUP_ZIP_JSON_ENTRY -> {
                        if (backupJsonText != null) return cleanupAndFail(stagingDir, stagedFiles, BackupError.NOT_A_BACKUP)
                        val bytes = readBounded(zip, MAX_BACKUP_BYTES) ?: return cleanupAndFail(stagingDir, stagedFiles, BackupError.TOO_LARGE)
                        totalBytes += bytes.size
                        if (totalBytes > MAX_BACKUP_ZIP_TOTAL_BYTES) return cleanupAndFail(stagingDir, stagedFiles, BackupError.TOO_LARGE)
                        backupJsonText = String(bytes, Charsets.UTF_8)
                    }
                    ATTACHMENT_ZIP_ENTRY_PATTERN.matches(name) -> {
                        val fileName = name.removePrefix("files/")
                        if (fileName in stagedFiles) return cleanupAndFail(stagingDir, stagedFiles, BackupError.INVALID_DATA)
                        val target = File(stagingDir, fileName)
                        val written = writeBounded(zip, target, MAX_SINGLE_ATTACHMENT_ZIP_BYTES)
                        if (written == null) {
                            target.delete()
                            return cleanupAndFail(stagingDir, stagedFiles, BackupError.TOO_LARGE)
                        }
                        totalBytes += written
                        if (totalBytes > MAX_BACKUP_ZIP_TOTAL_BYTES) return cleanupAndFail(stagingDir, stagedFiles, BackupError.TOO_LARGE)
                        stagedFiles[fileName] = target
                    }
                    else -> return cleanupAndFail(stagingDir, stagedFiles, BackupError.NOT_A_BACKUP)
                }
                zip.closeEntry()
            }
        }
    } catch (_: IOException) {
        return cleanupAndFail(stagingDir, stagedFiles, BackupError.NOT_A_BACKUP)
    } catch (_: IllegalArgumentException) {
        // ZipInputStream wirft das statt einer IOException für manche kaputten Einträge.
        return cleanupAndFail(stagingDir, stagedFiles, BackupError.NOT_A_BACKUP)
    }

    val text = backupJsonText ?: return cleanupAndFail(stagingDir, stagedFiles, BackupError.NOT_A_BACKUP)
    val decoded = decodeBackup(text)
    if (decoded is BackupReadResult.Failure) {
        cleanupStaged(stagedFiles)
        return BackupZipReadResult.Failure(decoded)
    }
    val backup = (decoded as BackupReadResult.Success).backup
    val expectedByFileName = backup.attachments.associateBy { it.attachment.fileName }

    for ((fileName, file) in stagedFiles) {
        val expected = expectedByFileName[fileName]?.attachment
            ?: return cleanupAndFail(stagingDir, stagedFiles, BackupError.INVALID_DATA)
        if (file.length() != expected.sizeBytes) return cleanupAndFail(stagingDir, stagedFiles, BackupError.INVALID_DATA)
        val header = file.inputStream().use { readHeader(it, SNIFF_HEADER_SIZE) }
        if (sniffAttachmentMimeType(header) != expected.mimeType) return cleanupAndFail(stagingDir, stagedFiles, BackupError.INVALID_DATA)
    }

    return BackupZipReadResult.Success(backup, stagedFiles)
}

private fun cleanupAndFail(stagingDir: File, stagedFiles: Map<String, File>, error: BackupError): BackupZipReadResult.Failure {
    cleanupStaged(stagedFiles)
    stagingDir.listFiles()?.forEach { it.delete() }
    return BackupZipReadResult.Failure(BackupReadResult.Failure(error))
}

private fun cleanupStaged(stagedFiles: Map<String, File>) {
    stagedFiles.values.forEach { it.delete() }
}

/** Größte einzelne Anhangsdatei in einer ZIP-Sicherung; wie `AttachmentFileStore.MAX_DOCUMENT_BYTES`, siehe dort. */
private const val MAX_SINGLE_ATTACHMENT_ZIP_BYTES = 20L * 1024 * 1024

private const val SNIFF_HEADER_SIZE = 16

/** Liest höchstens [maxBytes] aus [input]; `null`, wenn mehr Daten folgen. */
private fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
    val buffer = ByteArray(64 * 1024)
    val out = ByteArrayOutputStream()
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (out.size() + read > maxBytes) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** Schreibt [input] nach [target]; `null` (und bricht ab), sobald mehr als [maxBytes] gelesen wurden - gezählt, nicht behauptet. */
private fun writeBounded(input: InputStream, target: File, maxBytes: Long): Long? {
    target.outputStream().use { out ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return total
    }
}

private fun readHeader(input: InputStream, maxLength: Int): ByteArray {
    val buffer = ByteArray(maxLength)
    var total = 0
    while (total < maxLength) {
        val read = input.read(buffer, total, maxLength - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val WEBP_RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP_TAG_MAGIC = "WEBP".toByteArray(Charsets.US_ASCII)
private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)

private fun ByteArray.startsWith(magic: ByteArray, offset: Int = 0): Boolean =
    size >= offset + magic.size && magic.indices.all { this[offset + it] == magic[it] }

/**
 * Erkennt den MIME-Typ einer Anhangsdatei an ihren ersten Bytes, wie `AttachmentFileStore.sniffMimeType`
 * beim Import - hier dupliziert statt importiert, damit `backup/` ohne Android-Abhängigkeiten bleibt.
 */
private fun sniffAttachmentMimeType(header: ByteArray): String? = when {
    header.startsWith(JPEG_MAGIC) -> "image/jpeg"
    header.startsWith(PNG_MAGIC) -> "image/png"
    header.startsWith(WEBP_RIFF_MAGIC) && header.startsWith(WEBP_TAG_MAGIC, offset = 8) -> "image/webp"
    header.startsWith(PDF_MAGIC) -> "application/pdf"
    else -> null
}
