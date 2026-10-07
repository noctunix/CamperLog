package app.restvolt.camperlog.ui

import android.net.Uri
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.AttachmentImportResult
import app.restvolt.camperlog.data.ImportedAttachment
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Dateiablage für UI-Tests ohne echten Import; der Import-Pfad selbst ist in AttachmentFileStoreTest
 * geprüft. [nextPhotoResult]/[nextDocumentResult] steuern das Ergebnis des nächsten Imports und werden
 * von einer Lambda erzeugt, damit jeder Aufruf einen eigenen Dateinamen bekommt; Vorgabe ist Erfolg.
 */
class FakeAttachmentFileStore(private val directory: File = File(System.getProperty("java.io.tmpdir"), "camperlog-test-attachments")) : AttachmentFileStore {
    val deleted = mutableListOf<String>()
    val locationsWritten = mutableListOf<Triple<String, Double, Double>>()

    var nextPhotoResult: () -> AttachmentImportResult = {
        AttachmentImportResult.Success(
            ImportedAttachment.Photo(fileName = "${UUID.randomUUID()}.jpg", mimeType = "image/jpeg", sizeBytes = 1024, width = 100, height = 80),
        )
    }
    var nextDocumentResult: () -> AttachmentImportResult = {
        AttachmentImportResult.Success(
            ImportedAttachment.Document(fileName = "${UUID.randomUUID()}.pdf", mimeType = "application/pdf", sizeBytes = 2048),
        )
    }

    override suspend fun importPhoto(source: Uri): AttachmentImportResult = nextPhotoResult()

    override suspend fun importDocument(source: Uri): AttachmentImportResult = nextDocumentResult()

    override fun file(fileName: String): File = File(directory, fileName)

    override suspend fun writeLocation(fileName: String, latitude: Double, longitude: Double) {
        locationsWritten += Triple(fileName, latitude, longitude)
    }

    override suspend fun delete(fileName: String) {
        deleted += fileName
    }

    override suspend fun sweepOrphanFiles(knownFileNames: Set<String>, now: Instant) = Unit
}
