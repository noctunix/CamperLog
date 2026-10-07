package app.restvolt.camperlog.share

import app.restvolt.camperlog.backup.MAX_ZIP_PATH_SEGMENT_LENGTH
import app.restvolt.camperlog.backup.ZIP_PHOTOS_SEGMENT
import app.restvolt.camperlog.backup.sanitizeZipName
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.Station
import java.net.URLEncoder

/** Ein Fotoanhang einer Station mit seinem Pfad in der Export-ZIP (siehe [buildTourPhotoPaths]). */
data class TourExportPhoto(val attachment: Attachment, val zipPath: String) {
    /** [zipPath] mit prozentkodierten Segmenten; Leerzeichen brechen sonst Markdown-Bildlinks. */
    val relativeUrl: String
        get() = zipPath.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}

/**
 * Berechnet für jede Station ihre Fotos mit eindeutigem Pfad unter `Photos/`, in der Reihenfolge von
 * [photosByStation]. Benennung wie bei der ZIP-Sicherung ([app.restvolt.camperlog.backup.buildAttachmentZipPaths]):
 * `<Datum> <Stationsname> n.jpg`, mit Suffix eindeutig gemacht, falls nötig (Großschreibung dabei
 * ignoriert, damit auf einem case-insensitiven Dateisystem keine zwei Einträge kollidieren).
 */
fun buildTourPhotoPaths(stations: List<Station>, photosByStation: Map<Long, List<Attachment>>): Map<Long, List<TourExportPhoto>> {
    val usedNames = mutableSetOf<String>()
    return stations.associate { station ->
        val photos = photosByStation[station.id].orEmpty()
        val stem = capSegment("${station.date} ${sanitizeZipName(station.name, "Stop")}")
        station.id to photos.mapIndexed { index, attachment ->
            val fileName = uniqueFileName("$stem ${index + 1}", usedNames)
            TourExportPhoto(attachment, "$ZIP_PHOTOS_SEGMENT/$fileName")
        }
    }
}

private fun uniqueFileName(stem: String, usedNames: MutableSet<String>): String {
    var suffix = 1
    var fileName: String
    while (true) {
        fileName = if (suffix == 1) "$stem.jpg" else "$stem $suffix.jpg"
        if (usedNames.add(fileName.lowercase())) return fileName
        suffix++
    }
}

private fun capSegment(text: String): String =
    if (text.length <= MAX_ZIP_PATH_SEGMENT_LENGTH) text else text.take(MAX_ZIP_PATH_SEGMENT_LENGTH).trimEnd()
