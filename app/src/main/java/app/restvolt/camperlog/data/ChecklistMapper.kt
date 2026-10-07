package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import java.time.Instant

internal fun ChecklistWithItems.toDomain(): Checklist = Checklist(
    id = checklist.id,
    uuid = checklist.uuid,
    vehicleId = checklist.vehicleId,
    tourId = checklist.tourId,
    title = checklist.title,
    items = items.sortedBy(ChecklistItemEntity::position).map { ChecklistItem(text = it.text, checked = it.checked) },
    createdAt = Instant.ofEpochMilli(checklist.createdAtMillis),
    updatedAt = Instant.ofEpochMilli(checklist.updatedAtMillis),
)

internal fun Checklist.toEntity(): ChecklistEntity = ChecklistEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    tourId = tourId,
    title = title,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun Checklist.toItemEntities(): List<ChecklistItemEntity> =
    items.mapIndexed { index, item -> ChecklistItemEntity(checklistId = id, position = index, text = item.text, checked = item.checked) }
