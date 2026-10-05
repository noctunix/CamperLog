package app.restvolt.camperlog.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.isWebUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val CSV_MIME = "text/csv"

/** MIME-Typ einer Sicherungsdatei. */
const val BACKUP_MIME = "application/json"
private const val UTF8_BOM = "\uFEFF"
private const val EXPORT_DIR = "exports"

/** So lange darf eine Export-Datei liegen bleiben, damit die Empfänger-App sie noch lesen kann. */
private const val EXPORT_MAX_AGE_MILLIS = 60 * 60 * 1000L

private val exportStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/**
 * Schreibt alle [tours] als CSV in den Cache-Ordner `exports/` und liefert eine teilbare Content-URI.
 * Die Datei beginnt mit einem UTF-8-BOM, damit Tabellenprogramme Umlaute korrekt erkennen.
 * Jeder Export bekommt einen eigenen Dateinamen; ältere Exporte werden dabei aufgeräumt.
 */
suspend fun writeCsvExport(context: Context, tours: List<Tour>, vehicleNames: Map<Long, String>, defaultVehicleName: String): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        deleteOldExports(dir, System.currentTimeMillis())
        val file = uniqueFile(dir, "camperlog-touren-${LocalDateTime.now().format(exportStamp)}")
        file.writeText(UTF8_BOM + toursToCsv(tours, vehicleNames, defaultVehicleName), Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

/**
 * Schreibt die Sicherung [json] in den Cache-Ordner `exports/` und liefert eine teilbare Content-URI.
 * Ältere Exporte werden dabei wie bei [writeCsvExport] aufgeräumt.
 */
suspend fun writeBackupExport(context: Context, json: String): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, "camperlog-sicherung-${LocalDateTime.now().format(exportStamp)}", extension = "json")
    file.writeText(json, Charsets.UTF_8)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Schreibt die Sicherung [json] in die vom Nutzer gewählte Datei [target] und überschreibt deren Inhalt.
 *
 * @throws IOException wenn die Datei nicht geschrieben werden kann
 */
suspend fun writeBackupTo(context: Context, target: Uri, json: String) = withContext(Dispatchers.IO) {
    val output = try {
        context.contentResolver.openOutputStream(target, "wt")
    } catch (e: SecurityException) {
        throw IOException(e)
    } ?: throw IOException("Kein Ausgabestrom für $target")
    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
}

/** Vorgeschlagener Dateiname für eine Sicherung, z. B. `camperlog-sicherung-2026-10-04.json`. */
fun backupFileName(date: LocalDate = LocalDate.now()): String = "camperlog-sicherung-$date.json"

/** Löscht Export-Dateien, die älter als eine Stunde sind; für den App-Start gedacht. */
suspend fun cleanUpExports(context: Context) = withContext(Dispatchers.IO) {
    deleteOldExports(File(context.cacheDir, EXPORT_DIR), System.currentTimeMillis())
}

/** Löscht Dateien in [dir], die vor mehr als [EXPORT_MAX_AGE_MILLIS] zuletzt geändert wurden. */
internal fun deleteOldExports(dir: File, now: Long) {
    dir.listFiles()
        ?.filter { now - it.lastModified() > EXPORT_MAX_AGE_MILLIS }
        ?.forEach(File::delete)
}

/** Liefert eine noch nicht existierende Datei `base.csv`, `base-2.csv`, … in [dir]. */
internal fun uniqueFile(dir: File, base: String, extension: String = "csv"): File =
    generateSequence(1) { it + 1 }
        .map { n -> File(dir, if (n == 1) "$base.$extension" else "$base-$n.$extension") }
        .first { !it.exists() }

/**
 * Öffnet das Sharesheet für eine CSV-Datei unter [uri].
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareCsv(uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = CSV_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.export_chooser))
}

/**
 * Öffnet das Sharesheet für eine Sicherungsdatei unter [uri].
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareBackup(uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = BACKUP_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.backup_subject))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.export_chooser))
}

/**
 * Öffnet das Sharesheet mit einer Textzusammenfassung von [tour].
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareTour(tour: Tour): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, tour.destination))
        putExtra(Intent.EXTRA_TEXT, tourShareText(resources, tour))
    }
    return startChooser(send, getString(R.string.detail_share))
}

private fun Context.startChooser(send: Intent, title: String): Boolean =
    tryStart(Intent.createChooser(send, title))

/**
 * Öffnet den Kartenlink der Tour oder sucht das Ziel in einer Karten-App.
 * Der gespeicherte Link wird nur verwendet, wenn er eine http(s)-URL ist.
 *
 * @return `false`, wenn keine App den Link öffnen kann
 */
fun Context.openInMaps(tour: Tour): Boolean = mapIntents(tour).any(::tryStart)

/** Kandidaten zum Öffnen von [tour] in einer Karten-App, in absteigender Priorität. */
internal fun mapIntents(tour: Tour): List<Intent> = buildList {
    val query = Uri.encode(tour.destination)
    tour.mapLink?.takeIf(::isWebUrl)?.let { add(browsable(it.toUri())) }
    add(Intent(Intent.ACTION_VIEW, "geo:0,0?q=$query".toUri()))
    add(browsable("https://www.google.com/maps/search/?api=1&query=$query".toUri()))
}

private fun browsable(uri: Uri) = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

/** Startet [intent]; fehlende Ziel-App oder verweigerte Berechtigung ergeben `false` statt Absturz. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
