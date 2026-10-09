package app.restvolt.camperlog.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.sanitizeZipName
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.domain.isWebUrl
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val CSV_MIME = "text/csv"

/** MIME-Typ einer Sicherungsdatei. */
const val BACKUP_MIME = "application/json"

/** MIME-Typ einer ZIP-Sicherungsdatei (mit Fotos und Dokumenten). */
const val BACKUP_ZIP_MIME = "application/zip"

/** MIME-Typ der ZIP-Datei eines Tour-Exports. */
const val TOUR_EXPORT_ZIP_MIME = "application/zip"
internal const val UTF8_BOM = "\uFEFF"
private const val EXPORT_DIR = "exports"

/** So lange darf eine Export-Datei liegen bleiben, damit die Empfänger-App sie noch lesen kann. */
private const val EXPORT_MAX_AGE_MILLIS = 60 * 60 * 1000L

private val exportStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/**
 * Schreibt alle [tours] als CSV in den Cache-Ordner `exports/` und liefert eine teilbare Content-URI.
 * Kopfzeile und Werte folgen [vocabulary]. Die Datei beginnt mit einem UTF-8-BOM, damit
 * Tabellenprogramme Umlaute korrekt erkennen. Jeder Export bekommt einen eigenen Dateinamen; ältere
 * Exporte werden dabei aufgeräumt.
 */
suspend fun writeCsvExport(
    context: Context,
    tours: List<Tour>,
    stations: List<Station>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
    vocabulary: CsvVocabulary,
): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, "camperlog-touren-${LocalDateTime.now().format(exportStamp)}")
    file.writeText(UTF8_BOM + toursToCsv(tours, stations, vehicleNames, defaultVehicleName, vocabulary), Charsets.UTF_8)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Schreibt alle [stations] als eigene CSV in den Cache-Ordner `exports/` und liefert eine teilbare
 * Content-URI, wie [writeCsvExport] für Touren. [tourNames] löst [Station.tourId] in den Zielnamen auf.
 */
suspend fun writeStationsCsvExport(
    context: Context,
    stations: List<Station>,
    tourNames: Map<Long, String>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
    vocabulary: CsvVocabulary,
): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, "camperlog-stationen-${LocalDateTime.now().format(exportStamp)}")
    file.writeText(UTF8_BOM + stationsToCsv(stations, tourNames, vehicleNames, defaultVehicleName, vocabulary), Charsets.UTF_8)
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

/**
 * Schreibt die ZIP-Sicherung in den Cache-Ordner `exports/` und liefert eine teilbare Content-URI, wie
 * [writeBackupExport]. Ältere Exporte werden dabei ebenso aufgeräumt. [writeZip] schreibt direkt in den
 * Ausgabestrom der neu angelegten Datei, statt die ZIP-Sicherung vorher vollständig im Speicher
 * aufzubauen; schlägt das fehl, wird die unvollständige Datei gelöscht statt als Export angeboten.
 */
suspend fun writeBackupZipExport(context: Context, writeZip: (OutputStream) -> Unit): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, "camperlog-sicherung-${LocalDateTime.now().format(exportStamp)}", extension = "zip")
    try {
        file.outputStream().use(writeZip)
    } catch (e: IOException) {
        file.delete()
        throw e
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Schreibt die ZIP-Sicherung in die vom Nutzer gewählte Datei [target] und überschreibt deren Inhalt.
 * [writeZip] schreibt direkt in den Ausgabestrom, statt die ZIP-Sicherung vorher vollständig im
 * Speicher aufzubauen.
 *
 * @throws IOException wenn die Datei nicht geschrieben werden kann
 */
suspend fun writeBackupZipTo(context: Context, target: Uri, writeZip: (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
    val output = try {
        context.contentResolver.openOutputStream(target, "wt")
    } catch (e: SecurityException) {
        throw IOException(e)
    } ?: throw IOException("Kein Ausgabestrom für $target")
    output.use(writeZip)
}

/** Vorgeschlagener Dateiname für eine Sicherung, z. B. `camperlog-sicherung-2026-10-04.json`. */
fun backupFileName(date: LocalDate = LocalDate.now()): String = "camperlog-sicherung-$date.json"

/** Vorgeschlagener Dateiname für eine ZIP-Sicherung, z. B. `camperlog-sicherung-2026-10-04.zip`. */
fun backupZipFileName(date: LocalDate = LocalDate.now()): String = "camperlog-sicherung-$date.zip"

/**
 * Dateiname (ohne Endung) der ZIP-Datei eines Tour-Exports, z. B. `CamperLog Bodensee 2026-07-10`.
 * [nameHint] ist bevorzugt der Slug der Tour, sonst ihr Ziel (siehe Aufrufer).
 */
fun tourExportBaseName(nameHint: String, startDate: LocalDate): String =
    "CamperLog ${sanitizeZipName(nameHint, "Tour")} $startDate"

/**
 * Schreibt die ZIP-Datei eines Tour-Exports in den Cache-Ordner `exports/` und liefert eine
 * teilbare Content-URI, wie [writeBackupZipExport]. [baseName] (siehe [tourExportBaseName]) ergibt
 * zusammen mit der Endung den Dateinamen; ältere Exporte werden dabei ebenso aufgeräumt.
 */
suspend fun writeTourExportZipExport(context: Context, baseName: String, writeZip: (OutputStream) -> Unit): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    deleteOldExports(dir, System.currentTimeMillis())
    val file = uniqueFile(dir, baseName, extension = "zip")
    try {
        file.outputStream().use(writeZip)
    } catch (e: IOException) {
        file.delete()
        throw e
    }
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
 * Öffnet das Sharesheet für eine Stationen-CSV-Datei unter [uri].
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareStationsCsv(uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = CSV_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_stations_subject))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.export_stations_chooser))
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
 * Öffnet das Sharesheet für eine ZIP-Sicherungsdatei unter [uri].
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareBackupZip(uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = BACKUP_ZIP_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.backup_subject))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.export_chooser))
}

/**
 * Öffnet das Sharesheet für die ZIP-Datei eines Tour-Exports unter [uri]; [destination] füllt den
 * Betreff wie beim Textteilen ([tourShareText]).
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareTourExportZip(uri: Uri, destination: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = TOUR_EXPORT_ZIP_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, destination))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.detail_export_tour))
}

/**
 * Öffnet einen Anhang ([file], relativ zu `filesDir/attachments/`) in einer passenden App, mit
 * Lesezugriff über den FileProvider statt über Teilen von `filesDir` selbst.
 *
 * @return `false`, wenn keine App [mimeType] öffnen kann
 */
fun Context.openAttachment(file: File, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return tryStart(intent)
}

/**
 * Öffnet das Sharesheet für einen Anhang ([file], relativ zu `filesDir/attachments/`) mit Lesezugriff
 * über den FileProvider.
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareAttachment(file: File, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(send, getString(R.string.export_chooser))
}

/**
 * Öffnet das Sharesheet mit einer Textzusammenfassung von [tour]; [stations] und [countries]
 * fließen in die Kosten-, Stationen- und Länderangaben der Zusammenfassung ein (siehe [tourShareText]).
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareTour(tour: Tour, stations: List<Station>, countries: Set<String>): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, tour.destination))
        putExtra(Intent.EXTRA_TEXT, tourShareText(resources, tour, stations, countries))
    }
    return startChooser(send, getString(R.string.detail_share))
}

private fun Context.startChooser(send: Intent, title: String): Boolean =
    tryStart(Intent.createChooser(send, title))

/**
 * Öffnet das Sharesheet mit den Koordinaten eines einmaligen GPS-Fixes: Klartext mit
 * Koordinaten und einem `geo:`-Link.
 *
 * @return `false`, wenn kein Sharesheet geöffnet werden konnte
 */
fun Context.shareLocation(latitude: Double, longitude: Double, locale: Locale): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, locationShareText(latitude, longitude, locale))
    }
    return startChooser(send, getString(R.string.where_am_i_share_chooser))
}

/** Klartext für [shareLocation] bzw. zum Kopieren: lesbare Koordinaten plus `geo:`-Link. */
fun locationShareText(latitude: Double, longitude: Double, locale: Locale): String =
    "${formatCoordinates(latitude, longitude, locale)}\ngeo:$latitude,$longitude"

/** Öffnet die App-Info-Einstellungen von CamperLog (dauerhaft abgelehnte Standortberechtigung). */
fun Context.openAppDetailsSettings(): Boolean =
    tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

/** Öffnet die Standorteinstellungen des Geräts (Standort ist ausgeschaltet). */
fun Context.openLocationSourceSettings(): Boolean = tryStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

/** Öffnet die Benachrichtigungseinstellungen von CamperLog (abgelehnte POST_NOTIFICATIONS-Berechtigung). */
fun Context.openNotificationSettings(): Boolean =
    tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

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

/**
 * Öffnet eine Station in einer Karten-App: mit Koordinaten direkt dorthin, sonst über ihren
 * gespeicherten Kartenlink, sonst über eine Suche nach Name oder Ort.
 *
 * @return `false`, wenn keine App den Link öffnen kann
 */
fun Context.openInMaps(station: Station): Boolean = mapIntents(station).any(::tryStart)

/** Kandidaten zum Öffnen von [station] in einer Karten-App, in absteigender Priorität. */
internal fun mapIntents(station: Station): List<Intent> = buildList {
    val label = station.name.ifBlank { station.place }
    if (station.latitude != null && station.longitude != null) {
        val query = "${station.latitude},${station.longitude}" + if (label.isNotBlank()) "(${Uri.encode(label)})" else ""
        add(Intent(Intent.ACTION_VIEW, "geo:${station.latitude},${station.longitude}?q=$query".toUri()))
    }
    station.mapLink?.takeIf(::isWebUrl)?.let { add(browsable(it.toUri())) }
    if (label.isNotBlank()) {
        add(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(label)}".toUri()))
        add(browsable("https://www.google.com/maps/search/?api=1&query=${Uri.encode(label)}".toUri()))
    }
}

private fun browsable(uri: Uri) = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

/** Startet [intent]; fehlende Ziel-App oder verweigerte Berechtigung ergeben `false` statt Absturz. */
internal fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Kopiert [text] mit der Bezeichnung [label] in die Zwischenablage. */
internal fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
