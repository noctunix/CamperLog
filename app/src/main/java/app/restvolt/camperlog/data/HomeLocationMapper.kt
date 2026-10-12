package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.HomeLocation
import java.time.Instant
import java.time.LocalDate

internal fun HomeLocationEntity.toDomain(): HomeLocation = HomeLocation(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource?.let(CoordinateSource::valueOf),
    validFrom = validFrom?.let(LocalDate::parse),
    validTo = validTo?.let(LocalDate::parse),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun HomeLocation.toEntity(): HomeLocationEntity = HomeLocationEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource?.name,
    validFrom = validFrom?.toString(),
    validTo = validTo?.toString(),
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)
