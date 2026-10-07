package app.restvolt.camperlog.ui.attachments

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Unterordner von `cacheDir`, in dem Kamerafotos vor dem Import kurz liegen; siehe `res/xml/file_paths.xml`. */
internal const val CAMERA_CAPTURE_DIR = "camera"

/**
 * OS-Dialoge zum Auswählen eines Fotos oder Dokuments, hinter einer Schnittstelle, damit
 * Compose-Tests sie durch ein sofort auflösendes Testdouble ersetzen können, ohne einen echten
 * System-Dialog zu simulieren (wie [app.restvolt.camperlog.domain.LocationProvider] für GPS).
 */
interface AttachmentPickers {
    /** Liefert eine Funktion, die die Systemkamera öffnet; ihr Ergebnis landet in einer Cache-Datei und geht an [onPicked]. */
    @Composable
    fun rememberCameraLauncher(onPicked: (Uri) -> Unit): () -> Unit

    /** Liefert eine Funktion, die den Photo Picker öffnet (keine Berechtigung nötig); das gewählte Bild geht an [onPicked]. */
    @Composable
    fun rememberGalleryLauncher(onPicked: (Uri) -> Unit): () -> Unit

    /** Liefert eine Funktion, die `OpenDocument` für PDF/Bild öffnet; die gewählte Datei geht an [onPicked]. */
    @Composable
    fun rememberDocumentLauncher(onPicked: (Uri) -> Unit): () -> Unit
}

/** Echte Anbindung über `ActivityResultContracts`; keine der drei Aktionen braucht eine Laufzeitberechtigung. */
object AndroidAttachmentPickers : AttachmentPickers {

    @Composable
    override fun rememberCameraLauncher(onPicked: (Uri) -> Unit): () -> Unit {
        val context = LocalContext.current
        val pendingUri = remember { mutableStateOf<Uri?>(null) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingUri.value
            if (success && uri != null) onPicked(uri)
        }
        return {
            val uri = newCameraCaptureUri(context)
            pendingUri.value = uri
            launcher.launch(uri)
        }
    }

    @Composable
    override fun rememberGalleryLauncher(onPicked: (Uri) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
        return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    @Composable
    override fun rememberDocumentLauncher(onPicked: (Uri) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
        return { launcher.launch(arrayOf("application/pdf", "image/*")) }
    }
}

/** Neue, leere Cache-Datei für eine Kameraaufnahme, als teilbare FileProvider-Uri. */
internal fun newCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, CAMERA_CAPTURE_DIR).apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
