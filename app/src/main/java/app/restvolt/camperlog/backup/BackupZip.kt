package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.MAX_DOCUMENT_BYTES
import app.restvolt.camperlog.domain.readSniffHeader
import app.restvolt.camperlog.domain.sniffMimeType
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
 * Zu schreibende Sicherung: [json] ist immer gesetzt, [writeZip] nur mit eingeschlossenen Fotos und
 * Dokumenten (ersetzt dann die reine JSON-Datei). [writeZip] schreibt direkt in den übergebenen Strom
 * (siehe [writeBackupZip]) statt vorher vollständig im Speicher - bei bis zu 200 MB an Anhängen würde
 * das auf knappen Geräten ein `OutOfMemoryError` riskieren.
 */
class BackupPayload(val json: String, val writeZip: ((OutputStream) -> Unit)?)

/** Größte insgesamt aus einer ZIP-Sicherung entpackte Menge an Bytes, gegen eine Zip-Bombe. */
internal const val MAX_BACKUP_ZIP_TOTAL_BYTES = 500L * 1024 * 1024

/**
 * Geschätzte Größe einer ZIP-Sicherung zur Anzeige vor dem Export: der JSON-Text plus alle
 * eindeutigen Anhangsdateien (mehrfach verwendete Dateien, falls es die je gäbe, zählen nur einmal).
 */
fun backupZipSizeEstimate(json: String, attachments: List<BackupAttachment>): Long =
    json.toByteArray(Charsets.UTF_8).size.toLong() + attachments.distinctBy { it.attachment.fileName }.sumOf { it.attachment.sizeBytes }

/**
 * Schreibt [json] und, sofern [includeFiles], die Dateien der eindeutigen [attachments] als ZIP nach
 * [output], jede unter ihrem menschenlesbaren [BackupAttachment.zipPath]. [fileContent] liefert den
 * Dateiinhalt über den internen [app.restvolt.camperlog.domain.Attachment.fileName] (nicht den
 * ZIP-Pfad) oder `null`, wenn die Datei fehlt - das lässt den Export nicht scheitern, ihr Eintrag
 * fehlt dann einfach in der ZIP-Datei.
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
                if (!written.add(backupAttachment.zipPath)) continue
                fileContent(backupAttachment.attachment.fileName)?.use { input ->
                    zip.putNextEntry(ZipEntry(backupAttachment.zipPath))
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
 * Liest eine ZIP-Sicherung aus [input] in [stagingDir] (muss existieren oder anlegbar sein). Nur
 * [BACKUP_ZIP_JSON_ENTRY] und strukturell sichere, relative Pfade ([isValidZipPath]) werden unter
 * einem synthetischen Namen zwischengelagert - der Eintragsname selbst ist bis zum Abgleich mit
 * `backup.json` nicht vertrauenswürdig, jeder andere Eintrag lässt den Import ohne Seitenwirkung
 * scheitern. Jede Datei ist einzeln und insgesamt (bis [MAX_BACKUP_ZIP_TOTAL_BYTES]) nach tatsächlich
 * gelesenen statt behaupteten Bytes begrenzt, gegen eine Zip-Bombe. Nach dem Entpacken validiert
 * `backup.json` auch die [BackupAttachment.zipPath]; jede zwischengelagerte Datei muss zu genau einem
 * davon passen und in Größe und per Inhalt erkanntem MIME-Typ zu ihren Metadaten, erst dann wandert
 * sie unter ihrem internen [app.restvolt.camperlog.domain.Attachment.fileName] ins Ergebnis. Scheitert
 * eine Prüfung, werden alle Dateien in [stagingDir] gelöscht; das endgültige Verschieben an den
 * Ziel-Ort ist Sache des Aufrufers, nach erfolgreichem Datenbank-Import.
 */
fun readBackupZip(input: InputStream, stagingDir: File): BackupZipReadResult {
    stagingDir.mkdirs()
    val rawStaged = mutableMapOf<String, File>()
    var backupJsonText: String? = null
    var totalBytes = 0L
    var nextTempFileIndex = 0

    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == BACKUP_ZIP_JSON_ENTRY -> {
                        if (backupJsonText != null) return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
                        val bytes = readBounded(zip, MAX_BACKUP_BYTES) ?: return cleanupAndFail(stagingDir, rawStaged, BackupError.TOO_LARGE)
                        totalBytes += bytes.size
                        if (totalBytes > MAX_BACKUP_ZIP_TOTAL_BYTES) return cleanupAndFail(stagingDir, rawStaged, BackupError.TOO_LARGE)
                        backupJsonText = String(bytes, Charsets.UTF_8)
                    }
                    isValidZipPath(name) -> {
                        if (name in rawStaged) return cleanupAndFail(stagingDir, rawStaged, BackupError.INVALID_DATA)
                        val target = File(stagingDir, "entry-${nextTempFileIndex++}")
                        val written = writeBounded(zip, target, MAX_DOCUMENT_BYTES)
                        if (written == null) {
                            target.delete()
                            return cleanupAndFail(stagingDir, rawStaged, BackupError.TOO_LARGE)
                        }
                        totalBytes += written
                        if (totalBytes > MAX_BACKUP_ZIP_TOTAL_BYTES) return cleanupAndFail(stagingDir, rawStaged, BackupError.TOO_LARGE)
                        rawStaged[name] = target
                    }
                    else -> return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
                }
                zip.closeEntry()
            }
        }
    } catch (_: IOException) {
        return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
    } catch (_: IllegalArgumentException) {
        // ZipInputStream wirft das statt einer IOException für manche kaputten Einträge.
        return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
    }

    val text = backupJsonText ?: return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
    val decoded = decodeBackup(text)
    if (decoded is BackupReadResult.Failure) {
        cleanupStaged(rawStaged)
        return BackupZipReadResult.Failure(decoded)
    }
    val backup = (decoded as BackupReadResult.Success).backup
    val expectedByZipPath = backup.attachments.associateBy { it.zipPath }

    for (name in rawStaged.keys) {
        if (name !in expectedByZipPath) return cleanupAndFail(stagingDir, rawStaged, BackupError.INVALID_DATA)
    }

    val stagedFiles = mutableMapOf<String, File>()
    for ((name, tempFile) in rawStaged) {
        val expected = expectedByZipPath.getValue(name).attachment
        if (tempFile.length() != expected.sizeBytes) return cleanupAndFail(stagingDir, rawStaged, BackupError.INVALID_DATA)
        val header = tempFile.inputStream().use(::readSniffHeader)
        if (sniffMimeType(header) != expected.mimeType) return cleanupAndFail(stagingDir, rawStaged, BackupError.INVALID_DATA)
        val finalTarget = File(stagingDir, expected.fileName)
        if (!tempFile.renameTo(finalTarget)) return cleanupAndFail(stagingDir, rawStaged, BackupError.NOT_A_BACKUP)
        stagedFiles[expected.fileName] = finalTarget
    }

    return BackupZipReadResult.Success(backup, stagedFiles)
}

private fun cleanupAndFail(stagingDir: File, rawStaged: Map<String, File>, error: BackupError): BackupZipReadResult.Failure {
    cleanupStaged(rawStaged)
    stagingDir.listFiles()?.forEach { it.delete() }
    return BackupZipReadResult.Failure(BackupReadResult.Failure(error))
}

private fun cleanupStaged(rawStaged: Map<String, File>) {
    rawStaged.values.forEach { it.delete() }
}

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
