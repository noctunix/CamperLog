package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import java.time.Instant
import java.time.LocalDate

internal fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    type = LogType.valueOf(type),
    date = LocalDate.parse(date),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    stationId = stationId,
)

internal fun LogEntry.toEntity(): LogEntryEntity = LogEntryEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    type = type.name,
    date = date.toString(),
    createdAtMillis = createdAt.toEpochMilli(),
    stationId = stationId,
)
