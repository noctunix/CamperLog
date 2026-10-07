package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.ChecklistTemplate
import java.time.Instant

internal fun ChecklistTemplateWithItems.toDomain(): ChecklistTemplate = ChecklistTemplate(
    id = template.id,
    uuid = template.uuid,
    name = template.name,
    items = items.sortedBy(ChecklistTemplateItemEntity::position).map(ChecklistTemplateItemEntity::text),
    createdAt = Instant.ofEpochMilli(template.createdAtMillis),
    updatedAt = Instant.ofEpochMilli(template.updatedAtMillis),
)

internal fun ChecklistTemplate.toEntity(): ChecklistTemplateEntity = ChecklistTemplateEntity(
    id = id,
    uuid = uuid,
    name = name,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun ChecklistTemplate.toItemEntities(): List<ChecklistTemplateItemEntity> =
    items.mapIndexed { index, text -> ChecklistTemplateItemEntity(templateId = id, position = index, text = text) }
