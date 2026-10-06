package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.VehicleDocument
import java.time.Instant
import java.time.LocalDate

internal fun VehicleDocumentEntity.toDomain(): VehicleDocument = VehicleDocument(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    kind = DocumentKind.valueOf(kind),
    title = title,
    expiryDate = expiryDate?.let(LocalDate::parse),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun VehicleDocument.toEntity(): VehicleDocumentEntity = VehicleDocumentEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    kind = kind.name,
    title = title,
    expiryDate = expiryDate?.toString(),
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)
