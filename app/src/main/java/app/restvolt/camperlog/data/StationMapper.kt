package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun StationEntity.toDomain(): Station = Station(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    tourId = tourId,
    type = StationType.valueOf(type),
    date = LocalDate.parse(date),
    time = time?.let(LocalTime::parse),
    name = name,
    place = place,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource?.let(CoordinateSource::valueOf),
    accuracyM = accuracyM,
    mapLink = mapLink,
    notes = notes,
    nights = nights,
    siteKind = siteKind?.let(SiteKind::valueOf),
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate?.let(ElectricityFlatRate::valueOf),
    lteQuality = lteQuality?.let(LteQuality::valueOf),
    pitchSlope = pitchSlope?.let(PitchSlope::valueOf),
    levelingBlocksUsed = levelingBlocksUsed,
    services = decodeServices(services),
    weather = weatherTemperatureDeciC?.let {
        WeatherSnapshot(
            temperatureDeciC = it,
            weatherCode = checkNotNull(weatherCode),
            windKmh = checkNotNull(weatherWindKmh),
            gustKmh = weatherGustKmh,
            windDirectionDeg = weatherWindDirectionDeg,
            observedAt = Instant.ofEpochMilli(checkNotNull(weatherObservedAtMillis)),
        )
    },
    favorite = favorite,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun Station.toEntity(): StationEntity = StationEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    tourId = tourId,
    type = type.name,
    date = date.toString(),
    time = time?.toString(),
    name = name,
    place = place,
    latitude = latitude,
    longitude = longitude,
    coordinateSource = coordinateSource?.name,
    accuracyM = accuracyM,
    mapLink = mapLink,
    notes = notes,
    nights = nights,
    siteKind = siteKind?.name,
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate?.name,
    lteQuality = lteQuality?.name,
    pitchSlope = pitchSlope?.name,
    levelingBlocksUsed = levelingBlocksUsed,
    services = encodeServices(services),
    weatherTemperatureDeciC = weather?.temperatureDeciC,
    weatherCode = weather?.weatherCode,
    weatherWindKmh = weather?.windKmh,
    weatherGustKmh = weather?.gustKmh,
    weatherWindDirectionDeg = weather?.windDirectionDeg,
    weatherObservedAtMillis = weather?.observedAt?.toEpochMilli(),
    favorite = favorite,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

private fun encodeServices(services: Set<StationService>): String = services.joinToString(",") { it.name }

private fun decodeServices(text: String): Set<StationService> =
    text.split(",").mapNotNullTo(LinkedHashSet()) { name -> name.takeIf(String::isNotBlank)?.let(StationService::valueOf) }
