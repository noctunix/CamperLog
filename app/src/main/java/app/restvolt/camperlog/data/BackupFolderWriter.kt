package app.restvolt.camperlog.data

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val BACKUP_FOLDER_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
private const val BACKUP_MIME_TYPE = "application/json"

/**
 * Schreibzugriff auf den vom Nutzer gewählten Sicherungsordner (6.11: "Backup folder", SAF
 * `OPEN_DOCUMENT_TREE`). URIs werden als Strings übergeben, damit [app.restvolt.camperlog.ui.data.DataViewModel]
 * ohne Android-Klassen testbar bleibt.
 */
interface BackupFolderWriter {
    /** Ob [folderUri] noch existiert und beschreibbar ist, z. B. weil die Berechtigung noch gilt. */
    fun isAccessible(folderUri: String): Boolean

    /** Anzeigename von [folderUri], oder `null`, wenn er nicht (mehr) aufgelöst werden kann. */
    fun folderDisplayName(folderUri: String): String?

    /**
     * Schreibt [json] als neue, zeitgestempelte Datei in [folderUri] und liefert ihren Dateinamen,
     * oder `null`, wenn der Ordner nicht mehr zugreifbar ist oder das Schreiben fehlschlägt.
     */
    suspend fun writeTimestampedBackup(folderUri: String, json: String): String?
}

/** [BackupFolderWriter] über `DocumentFile` auf einem SAF-Tree-URI, ohne zusätzliche Berechtigung. */
class AndroidBackupFolderWriter(context: Context) : BackupFolderWriter {
    private val context = context.applicationContext

    override fun isAccessible(folderUri: String): Boolean = folder(folderUri)?.canWrite() == true

    override fun folderDisplayName(folderUri: String): String? = folder(folderUri)?.name

    override suspend fun writeTimestampedBackup(folderUri: String, json: String): String? = withContext(Dispatchers.IO) {
        val dir = folder(folderUri)?.takeIf { it.canWrite() } ?: return@withContext null
        val name = "camperlog-sicherung-${LocalDateTime.now().format(BACKUP_FOLDER_STAMP)}.json"
        val file = dir.createFile(BACKUP_MIME_TYPE, name) ?: return@withContext null
        try {
            val output = context.contentResolver.openOutputStream(file.uri) ?: return@withContext null
            output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        } catch (_: IOException) {
            return@withContext null
        } catch (_: SecurityException) {
            return@withContext null
        }
        file.name ?: name
    }

    private fun folder(folderUri: String): DocumentFile? =
        try {
            DocumentFile.fromTreeUri(context, folderUri.toUri())?.takeIf { it.exists() && it.isDirectory }
        } catch (_: SecurityException) {
            null
        }
}
