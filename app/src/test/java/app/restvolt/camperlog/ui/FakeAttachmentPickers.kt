package app.restvolt.camperlog.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import app.restvolt.camperlog.ui.attachments.AttachmentPickers

/**
 * [AttachmentPickers]-Testdouble: jede Aktion liefert sofort eine feste Uri, ohne einen echten
 * System-Dialog zu öffnen - wie [app.restvolt.camperlog.domain.LocationProvider] für GPS in Tests.
 */
class FakeAttachmentPickers(
    private val cameraUri: Uri = "content://fake/camera.jpg".toUri(),
    private val galleryUri: Uri = "content://fake/gallery.jpg".toUri(),
    private val documentUri: Uri = "content://fake/document.pdf".toUri(),
) : AttachmentPickers {
    @Composable
    override fun rememberCameraLauncher(onPicked: (Uri) -> Unit): () -> Unit = { onPicked(cameraUri) }

    @Composable
    override fun rememberGalleryLauncher(onPicked: (Uri) -> Unit): () -> Unit = { onPicked(galleryUri) }

    @Composable
    override fun rememberDocumentLauncher(onPicked: (Uri) -> Unit): () -> Unit = { onPicked(documentUri) }
}
