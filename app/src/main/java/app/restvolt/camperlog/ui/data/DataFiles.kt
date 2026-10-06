package app.restvolt.camperlog.ui.data

import android.content.Context
import androidx.core.net.toUri
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.share.writeBackupExport
import app.restvolt.camperlog.share.writeBackupTo
import app.restvolt.camperlog.share.writeBackupZipExport
import app.restvolt.camperlog.share.writeBackupZipTo
import app.restvolt.camperlog.share.writeCsvExport
import app.restvolt.camperlog.share.writeStationsCsvExport
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Dateizugriffe des Daten-Screens. URIs werden als Strings übergeben, damit [DataViewModel]
 * ohne Android-Klassen testbar bleibt.
 */
interface DataFiles {
    /**
     * Schreibt [tours] und [stations] als CSV in den Export-Cache und liefert die teilbare URI.
     * [vehicleNames] löst [Tour.vehicleId] in einen Anzeigenamen auf; [defaultVehicleName] gilt
     * bei einem leeren oder fehlenden Namen.
     */
    suspend fun writeCsvExport(
        tours: List<Tour>,
        stations: List<Station>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String

    /**
     * Schreibt [stations] als eigene CSV in den Export-Cache und liefert die teilbare URI.
     * [tourNames] löst [Station.tourId] in den Zielnamen auf, [vehicleNames] und [defaultVehicleName]
     * wie bei [writeCsvExport].
     */
    suspend fun writeStationsCsvExport(
        stations: List<Station>,
        tourNames: Map<Long, String>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String

    /** Schreibt die Sicherung [json] in den Export-Cache und liefert die teilbare URI. */
    suspend fun writeBackupExport(json: String): String

    /**
     * Schreibt die Sicherung [json] in die vom Nutzer gewählte Datei [target].
     *
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    suspend fun writeBackup(target: String, json: String)

    /**
     * Wie [writeBackupExport], aber für eine ZIP-Sicherung samt Fotos und Dokumenten: [writeZip]
     * schreibt die ZIP-Sicherung direkt in den gelieferten Strom, statt sie vorher vollständig im
     * Speicher aufzubauen.
     */
    suspend fun writeBackupZipExport(writeZip: (OutputStream) -> Unit): String

    /** Wie [writeBackup], aber für eine ZIP-Sicherung samt Fotos und Dokumenten, wie [writeBackupZipExport] gestreamt. */
    suspend fun writeBackupZip(target: String, writeZip: (OutputStream) -> Unit)

    /**
     * Öffnet die vom Nutzer gewählte Datei [source]; `null`, wenn kein Strom geliefert wird.
     *
     * @throws IOException wenn der Zugriff verweigert wird
     */
    fun open(source: String): InputStream?

    /** Neuer, leerer Ordner für die Zwischenablage einer ZIP-Sicherung, während [readBackupZip][app.restvolt.camperlog.backup.readBackupZip] sie prüft. */
    fun newImportStagingDir(): File
}

/** [DataFiles] über den Application-Context, damit das ViewModel keine Activity festhält. */
class AndroidDataFiles(context: Context) : DataFiles {
    private val context = context.applicationContext

    override suspend fun writeCsvExport(
        tours: List<Tour>,
        stations: List<Station>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String = writeCsvExport(context, tours, stations, vehicleNames, defaultVehicleName).toString()

    override suspend fun writeStationsCsvExport(
        stations: List<Station>,
        tourNames: Map<Long, String>,
        vehicleNames: Map<Long, String>,
        defaultVehicleName: String,
    ): String = writeStationsCsvExport(context, stations, tourNames, vehicleNames, defaultVehicleName).toString()

    override suspend fun writeBackupExport(json: String): String = writeBackupExport(context, json).toString()

    override suspend fun writeBackup(target: String, json: String) = writeBackupTo(context, target.toUri(), json)

    override suspend fun writeBackupZipExport(writeZip: (OutputStream) -> Unit): String = writeBackupZipExport(context, writeZip).toString()

    override suspend fun writeBackupZip(target: String, writeZip: (OutputStream) -> Unit) = writeBackupZipTo(context, target.toUri(), writeZip)

    override fun open(source: String): InputStream? = try {
        context.contentResolver.openInputStream(source.toUri())
    } catch (e: SecurityException) {
        throw IOException(e)
    }

    override fun newImportStagingDir(): File =
        File(context.cacheDir, "import-staging/${UUID.randomUUID()}").apply { mkdirs() }
}
