package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import java.time.Instant
import java.time.LocalDateTime

internal fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    uuid = uuid,
    ownerType = AttachmentOwnerType.valueOf(ownerType),
    ownerId = ownerId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt?.let(LocalDateTime::parse),
    caption = caption,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)

internal fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    uuid = uuid,
    ownerType = ownerType.name,
    ownerId = ownerId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt?.toString(),
    caption = caption,
    createdAtMillis = createdAt.toEpochMilli(),
)
