package app.restvolt.camperlog.ui.data

import android.content.Context
import androidx.core.net.toUri
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.share.writeBackupExport
import app.restvolt.camperlog.share.writeBackupTo
import app.restvolt.camperlog.share.writeCsvExport
import java.io.IOException
import java.io.InputStream

/**
 * Dateizugriffe des Daten-Screens. URIs werden als Strings übergeben, damit [DataViewModel]
 * ohne Android-Klassen testbar bleibt.
 */
interface DataFiles {
    /** Schreibt [tours] als CSV in den Export-Cache und liefert die teilbare URI. */
    suspend fun writeCsvExport(tours: List<Tour>): String

    /** Schreibt die Sicherung [json] in den Export-Cache und liefert die teilbare URI. */
    suspend fun writeBackupExport(json: String): String

    /**
     * Schreibt die Sicherung [json] in die vom Nutzer gewählte Datei [target].
     *
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    suspend fun writeBackup(target: String, json: String)

    /**
     * Öffnet die vom Nutzer gewählte Datei [source]; `null`, wenn kein Strom geliefert wird.
     *
     * @throws IOException wenn der Zugriff verweigert wird
     */
    fun open(source: String): InputStream?
}

/** [DataFiles] über den Application-Context, damit das ViewModel keine Activity festhält. */
class AndroidDataFiles(context: Context) : DataFiles {
    private val context = context.applicationContext

    override suspend fun writeCsvExport(tours: List<Tour>): String = writeCsvExport(context, tours).toString()

    override suspend fun writeBackupExport(json: String): String = writeBackupExport(context, json).toString()

    override suspend fun writeBackup(target: String, json: String) = writeBackupTo(context, target.toUri(), json)

    override fun open(source: String): InputStream? = try {
        context.contentResolver.openInputStream(source.toUri())
    } catch (e: SecurityException) {
        throw IOException(e)
    }
}
