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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val CSV_MIME = "text/csv"
private const val UTF8_BOM = "\uFEFF"

/**
 * Schreibt alle [tours] als CSV in den Cache-Ordner `exports/` und liefert eine teilbare Content-URI.
 * Die Datei beginnt mit einem UTF-8-BOM, damit Tabellenprogramme Umlaute korrekt erkennen.
 */
suspend fun writeCsvExport(context: Context, tours: List<Tour>): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach(File::delete)
    val file = File(dir, "camperlog-touren-${LocalDate.now()}.csv")
    file.writeText(UTF8_BOM + toursToCsv(tours), Charsets.UTF_8)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

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

private fun Context.startChooser(send: Intent, title: String): Boolean = try {
    startActivity(Intent.createChooser(send, title))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Öffnet den Kartenlink der Tour oder sucht das Ziel in einer Karten-App.
 *
 * @return `false`, wenn keine App den Link öffnen kann
 */
fun Context.openInMaps(tour: Tour): Boolean {
    val uris = buildList {
        tour.mapLink?.let { add(it.toUri()) }
        val query = Uri.encode(tour.destination)
        add("geo:0,0?q=$query".toUri())
        add("https://www.google.com/maps/search/?api=1&query=$query".toUri())
    }
    return uris.any { uri ->
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
