package app.restvolt.camperlog.ui.attachments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.AttachmentImportResult
import app.restvolt.camperlog.data.ImportedAttachment
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Zustand des Anhangs-Streifens eines Eintrags. */
data class AttachmentsUiState(
    val photos: List<Attachment> = emptyList(),
    val importing: Boolean = false,
    val importError: AttachmentImportError? = null,
    /** Zuletzt gelöschter Anhang, solange die Rückgängig-Snackbar noch aussteht. */
    val lastDeleted: Attachment? = null,
)

/**
 * Hält die Anhänge eines Eintrags ([ownerType]/[ownerId]) aktuell und kapselt Import, Löschen mit
 * Rückgängig, Standortübernahme und Bildunterschrift. Eine Instanz gehört immer zu einem bereits
 * gespeicherten Eintrag; ein noch nicht gespeicherter Eintrag zeigt keinen Hinzufügen-Button an
 * (siehe [PhotoAttachmentsSection]), sodass [ownerId] hier nie `0` ist.
 */
class AttachmentsViewModel(
    private val repository: AttachmentRepository,
    private val fileStore: AttachmentFileStore,
    private val ownerType: AttachmentOwnerType,
    private val ownerId: Long,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private data class Transient(
        val importing: Boolean = false,
        val importError: AttachmentImportError? = null,
        val lastDeleted: Attachment? = null,
    )

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<AttachmentsUiState> = combine(repository.observeForOwner(ownerType, ownerId), transient) { photos, t ->
        AttachmentsUiState(photos, t.importing, t.importError, t.lastDeleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttachmentsUiState())

    /** Importiert ein mit der Kamera aufgenommenes oder aus der Galerie gewähltes Foto. */
    fun importPhoto(source: Uri) {
        transient.update { it.copy(importing = true, importError = null) }
        viewModelScope.launch {
            when (val result = fileStore.importPhoto(source)) {
                is AttachmentImportResult.Success -> {
                    val photo = result.attachment as ImportedAttachment.Photo
                    repository.add(
                        Attachment(
                            ownerType = ownerType,
                            ownerId = ownerId,
                            fileName = photo.fileName,
                            mimeType = photo.mimeType,
                            sizeBytes = photo.sizeBytes,
                            width = photo.width,
                            height = photo.height,
                            latitude = photo.latitude,
                            longitude = photo.longitude,
                            takenAt = photo.takenAt,
                            createdAt = now(),
                        ),
                    )
                    transient.update { it.copy(importing = false) }
                }
                is AttachmentImportResult.Failure -> transient.update { it.copy(importing = false, importError = result.error) }
            }
        }
    }

    /** Importiert ein mit `OpenDocument` gewähltes Dokument (PDF oder Bild), z. B. für ein Fahrzeugdokument. */
    fun importDocument(source: Uri) {
        transient.update { it.copy(importing = true, importError = null) }
        viewModelScope.launch {
            when (val result = fileStore.importDocument(source)) {
                is AttachmentImportResult.Success -> {
                    val document = result.attachment as ImportedAttachment.Document
                    repository.add(
                        Attachment(
                            ownerType = ownerType,
                            ownerId = ownerId,
                            fileName = document.fileName,
                            mimeType = document.mimeType,
                            sizeBytes = document.sizeBytes,
                            createdAt = now(),
                        ),
                    )
                    transient.update { it.copy(importing = false) }
                }
                is AttachmentImportResult.Failure -> transient.update { it.copy(importing = false, importError = result.error) }
            }
        }
    }

    /** Die Fehlermeldung zu [AttachmentsUiState.importError] wurde angezeigt. */
    fun dismissImportError() = transient.update { it.copy(importError = null) }

    /** Löscht [attachment]; die Datei bleibt bis zum nächsten Aufräumlauf liegen, siehe [undoDelete]. */
    fun delete(attachment: Attachment) {
        viewModelScope.launch {
            repository.delete(attachment.id)
            transient.update { it.copy(lastDeleted = attachment) }
        }
    }

    /** Macht das letzte [delete] rückgängig (Snackbar-Aktion). */
    fun undoDelete() {
        val deleted = transient.value.lastDeleted ?: return
        viewModelScope.launch {
            repository.restore(deleted)
            transient.update { it.copy(lastDeleted = null) }
        }
    }

    /** Die Rückgängig-Snackbar zu [AttachmentsUiState.lastDeleted] ist abgelaufen oder wurde quittiert. */
    fun deleteHandled() = transient.update { it.copy(lastDeleted = null) }

    /** "Standort der Station übernehmen" im Betrachter. */
    fun useLocation(attachment: Attachment, latitude: Double, longitude: Double) {
        viewModelScope.launch { repository.setLocation(attachment.id, latitude, longitude) }
    }

    /** Bildunterschrift bearbeiten. */
    fun updateCaption(attachment: Attachment, caption: String) {
        viewModelScope.launch { repository.updateCaption(attachment.id, caption) }
    }

    /** Die Datei zu [attachment], z. B. zum Anzeigen, Teilen oder Öffnen. */
    fun file(attachment: Attachment) = fileStore.file(attachment.fileName)
}
