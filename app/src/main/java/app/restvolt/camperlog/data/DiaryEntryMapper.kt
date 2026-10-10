package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.DiaryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun DiaryEntryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    uuid = uuid,
    tourId = tourId,
    date = LocalDate.parse(date),
    time = time?.let(LocalTime::parse),
    text = text,
    odometerKm = odometerKm,
    weather = weather,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun DiaryEntry.toEntity(): DiaryEntryEntity = DiaryEntryEntity(
    id = id,
    uuid = uuid,
    tourId = tourId,
    date = date.toString(),
    time = time?.toString(),
    text = text,
    odometerKm = odometerKm,
    weather = weather,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)
