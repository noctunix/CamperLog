package de.hannes.camperlog.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.isWebUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val CSV_MIME = "text/csv"
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
suspend fun writeCsvExport(context: Context, tours: List<Tour>): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, "camperlog-touren-${LocalDateTime.now().format(exportStamp)}")
    file.writeText(UTF8_BOM + toursToCsv(tours), Charsets.UTF_8)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

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
internal fun uniqueFile(dir: File, base: String): File =
    generateSequence(1) { it + 1 }
        .map { n -> File(dir, if (n == 1) "$base.csv" else "$base-$n.csv") }
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
