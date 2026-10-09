package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.WeatherSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

internal fun StationWithCosts.toDomain(): Station = station.toDomain(
    costs.sortedBy(StationCostEntity::position).map {
        StationCost(CostCategory.valueOf(it.category), Money(it.amountMinor, Currency.getInstance(it.currency)), it.note)
    },
)

private fun StationEntity.toDomain(costs: List<StationCost>): Station {
    val currency = electricityCurrency?.let(Currency::getInstance)
    return Station(
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
        lteQuality = lteQuality?.let(LteQuality::valueOf),
        pitchSlope = pitchSlope?.let(PitchSlope::valueOf),
        levelingBlocksUsed = levelingBlocksUsed,
        electricityBilling = electricityBilling?.let(ElectricityBilling::valueOf),
        electricityCurrency = currency,
        electricityFlatAmount = electricityFlatAmountMinor?.let { Money(it, checkNotNull(currency)) },
        electricityBaseFee = electricityBaseFeeMinor?.let { Money(it, checkNotNull(currency)) },
        electricityPricePerKwh = electricityPricePerKwh?.let(::BigDecimal),
        electricityCoinPrice = electricityCoinPriceMinor?.let { Money(it, checkNotNull(currency)) },
        electricityCoinsUsed = electricityCoinsUsed,
        electricityKwhPerCoin = electricityKwhPerCoin?.let(::BigDecimal),
        electricityMeterStart = electricityMeterStart?.let(::BigDecimal),
        electricityMeterEnd = electricityMeterEnd?.let(::BigDecimal),
        electricityKwhUsed = electricityKwhUsed?.let(::BigDecimal),
        tollKind = tollKind?.let(TollKind::valueOf),
        tollPaymentMethod = tollPaymentMethod,
        tollCountry = tollCountry,
        tollValidFrom = tollValidFrom?.let(LocalDate::parse),
        tollValidUntil = tollValidUntil?.let(LocalDate::parse),
        ferryBookingReference = ferryBookingReference,
        costs = costs,
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
        rating = rating,
        odometerKm = odometerKm,
        manualTemperatureDeciC = manualTemperatureDeciC,
        link = link,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtMillis),
    )
}

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
    lteQuality = lteQuality?.name,
    pitchSlope = pitchSlope?.name,
    levelingBlocksUsed = levelingBlocksUsed,
    electricityBilling = electricityBilling?.name,
    electricityCurrency = electricityCurrency?.currencyCode,
    electricityFlatAmountMinor = electricityFlatAmount?.minor,
    electricityBaseFeeMinor = electricityBaseFee?.minor,
    electricityPricePerKwh = electricityPricePerKwh?.toPlainString(),
    electricityCoinPriceMinor = electricityCoinPrice?.minor,
    electricityCoinsUsed = electricityCoinsUsed,
    electricityKwhPerCoin = electricityKwhPerCoin?.toPlainString(),
    electricityMeterStart = electricityMeterStart?.toPlainString(),
    electricityMeterEnd = electricityMeterEnd?.toPlainString(),
    electricityKwhUsed = electricityKwhUsed?.toPlainString(),
    tollKind = tollKind?.name,
    tollPaymentMethod = tollPaymentMethod,
    tollCountry = tollCountry,
    tollValidFrom = tollValidFrom?.toString(),
    tollValidUntil = tollValidUntil?.toString(),
    ferryBookingReference = ferryBookingReference,
    services = encodeServices(services),
    weatherTemperatureDeciC = weather?.temperatureDeciC,
    weatherCode = weather?.weatherCode,
    weatherWindKmh = weather?.windKmh,
    weatherGustKmh = weather?.gustKmh,
    weatherWindDirectionDeg = weather?.windDirectionDeg,
    weatherObservedAtMillis = weather?.observedAt?.toEpochMilli(),
    favorite = favorite,
    rating = rating,
    odometerKm = odometerKm,
    manualTemperatureDeciC = manualTemperatureDeciC,
    link = link,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun Station.toCostEntities(): List<StationCostEntity> =
    costs.mapIndexed { index, cost -> StationCostEntity(id, cost.category.name, cost.amount.currency.currencyCode, cost.amount.minor, cost.note, index) }

private fun encodeServices(services: Set<StationService>): String = services.joinToString(",") { it.name }

private fun decodeServices(text: String): Set<StationService> =
    text.split(",").mapNotNullTo(LinkedHashSet()) { name -> name.takeIf(String::isNotBlank)?.let(StationService::valueOf) }
