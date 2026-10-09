package app.restvolt.camperlog.ui.attachments

import android.net.Uri
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.ImportedAttachment
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime

/**
 * Ein vor dem ersten Speichern eines neuen Eintrags aufgenommenes Foto: Die Datei liegt bereits im
 * Anhangs-Ordner (`AttachmentFileStore`), aber noch ohne zugehörige [Attachment]-Zeile, weil der
 * Eintrag noch keine id hat. Serialisierbar, damit sie wie die übrigen Formulareingaben einen
 * Prozessabbruch über `SavedStateHandle` überstehen.
 */
@Serializable
data class PendingPhoto(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Serializable(with = LocalDateTimeSerializer::class) val takenAt: LocalDateTime? = null,
    val caption: String = "",
)

/** Übernimmt ein frisch importiertes Foto als [PendingPhoto]. */
fun ImportedAttachment.Photo.toPendingPhoto(): PendingPhoto = PendingPhoto(
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt,
)

/** Für die Vorschau im [PhotoAttachmentsSection]-Streifen: dieselbe Optik wie ein gespeicherter Anhang. */
internal fun PendingPhoto.asAttachment(index: Int, ownerType: AttachmentOwnerType): Attachment = Attachment(
    id = -(index + 1).toLong(),
    ownerType = ownerType,
    ownerId = 0,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt,
    caption = caption,
    createdAt = Instant.EPOCH,
)

/** Nach dem ersten Speichern: derselbe Inhalt als echte, an [ownerId] gebundene [Attachment]-Zeile. */
fun PendingPhoto.toAttachment(ownerType: AttachmentOwnerType, ownerId: Long, createdAt: Instant): Attachment = Attachment(
    ownerType = ownerType,
    ownerId = ownerId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt,
    caption = caption,
    createdAt = createdAt,
)

/**
 * Fähigkeit, Fotos schon vor dem ersten Speichern eines neuen Eintrags aufzunehmen; wird
 * [PhotoAttachmentsSection] für einen Eintrag ohne id (`ownerId == 0`) mitgegeben. `null` bedeutet,
 * dass diese Oberfläche das (noch) nicht unterstützt - dann zeigt der Streifen nur den
 * Speichern-zuerst-Hinweis.
 */
class PendingPhotosState(
    val photos: List<PendingPhoto>,
    val importing: Boolean,
    val importError: AttachmentImportError?,
    val onAdd: (Uri) -> Unit,
    val onRemove: (PendingPhoto) -> Unit,
    val onCaptionChange: (PendingPhoto, String) -> Unit,
    val onUseLocation: (PendingPhoto, Double, Double) -> Unit,
    val onDismissImportError: () -> Unit,
)
