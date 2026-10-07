package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.DiaryEntry
import java.time.Instant
import java.time.LocalDate

internal fun DiaryEntryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    uuid = uuid,
    tourId = tourId,
    date = LocalDate.parse(date),
    text = text,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun DiaryEntry.toEntity(): DiaryEntryEntity = DiaryEntryEntity(
    id = id,
    uuid = uuid,
    tourId = tourId,
    date = date.toString(),
    text = text,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)
