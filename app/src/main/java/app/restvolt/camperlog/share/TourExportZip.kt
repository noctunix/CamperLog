package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.Tour
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Schreibt die ZIP-Datei des Tour-Exports direkt nach [output], ohne sie vorher vollständig im
 * Speicher aufzubauen (wie [writeBackupZipExport]): `Tour.html` ([tourHtml]), `Tour.md`
 * ([tourMarkdown]), `Tour.gpx` ([tourGpx], nur mit Koordinaten), `Stops.csv` ([stationsToCsv]) und
 * die Fotos der [stations] unter `Photos/` (siehe [buildTourPhotoPaths]). [photosByStation] sind
 * die Fotoanhänge je Station, [photoContent] liefert ihren Dateiinhalt über den internen Dateinamen
 * ([Attachment.fileName]) oder `null`, wenn die Datei fehlt - das lässt den Export nicht scheitern,
 * ihr Eintrag fehlt dann einfach in der ZIP-Datei.
 */
fun writeTourExportZip(
    output: OutputStream,
    res: Resources,
    tour: Tour,
    stations: List<Station>,
    countries: Set<String>,
    photosByStation: Map<Long, List<Attachment>>,
    tourNames: Map<Long, String>,
    vehicleNames: Map<Long, String>,
    defaultVehicleName: String,
    vocabulary: CsvVocabulary,
    photoContent: (fileName: String) -> InputStream?,
) {
    val photoPaths = buildTourPhotoPaths(stations, photosByStation)
    ZipOutputStream(output).use { zip ->
        zip.writeTextEntry("Tour.html") { tourHtml(res, tour, stations, countries, photoPaths) }
        zip.writeTextEntry("Tour.md") { tourMarkdown(res, tour, stations, countries, photoPaths) }
        tourGpx(res, stations)?.let { gpx -> zip.writeTextEntry("Tour.gpx") { gpx } }
        zip.writeTextEntry("Stops.csv") { UTF8_BOM + stationsToCsv(stations, tourNames, vehicleNames, defaultVehicleName, vocabulary) }
        for (photos in photoPaths.values) {
            for (photo in photos) {
                photoContent(photo.attachment.fileName)?.use { input ->
                    zip.putNextEntry(ZipEntry(photo.zipPath))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }
}

private fun ZipOutputStream.writeTextEntry(name: String, content: () -> String) {
    putNextEntry(ZipEntry(name))
    write(content().toByteArray(Charsets.UTF_8))
    closeEntry()
}
