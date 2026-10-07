package app.restvolt.camperlog.ui.detail

import android.content.Context
import app.restvolt.camperlog.share.writeTourExportZipExport
import java.io.OutputStream

/** Dateizugriff des Tour-Exports, wie [app.restvolt.camperlog.ui.data.DataFiles] für den Daten-Screen. */
interface TourExportFiles {
    /** Schreibt die Export-ZIP mit Namen [baseName] in den Export-Cache und liefert die teilbare URI. */
    suspend fun writeTourExportZip(baseName: String, writeZip: (OutputStream) -> Unit): String
}

/** [TourExportFiles] über den Application-Context, damit das ViewModel keine Activity festhält. */
class AndroidTourExportFiles(context: Context) : TourExportFiles {
    private val context = context.applicationContext

    override suspend fun writeTourExportZip(baseName: String, writeZip: (OutputStream) -> Unit): String =
        writeTourExportZipExport(context, baseName, writeZip).toString()
}
