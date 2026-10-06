package app.restvolt.camperlog.ui

import android.net.Uri
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.AttachmentImportResult
import java.io.File
import java.time.Instant

/** Dateiablage für UI-Tests ohne echten Import; der Import-Pfad selbst ist in AttachmentFileStoreTest geprüft. */
class FakeAttachmentFileStore(private val directory: File = File(System.getProperty("java.io.tmpdir"), "camperlog-test-attachments")) : AttachmentFileStore {
    val deleted = mutableListOf<String>()

    override suspend fun importPhoto(source: Uri): AttachmentImportResult = AttachmentImportResult.Failure(AttachmentImportError.UNSUPPORTED_TYPE)

    override suspend fun importDocument(source: Uri): AttachmentImportResult = AttachmentImportResult.Failure(AttachmentImportError.UNSUPPORTED_TYPE)

    override fun file(fileName: String): File = File(directory, fileName)

    override suspend fun delete(fileName: String) {
        deleted += fileName
    }

    override suspend fun sweepOrphanFiles(knownFileNames: Set<String>, now: Instant) = Unit
}
