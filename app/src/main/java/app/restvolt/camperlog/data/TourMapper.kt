package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.YearTotals
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

internal fun TourWithCosts.toDomain(): Tour = tour.toDomain(
    costs.sortedBy(TourCostEntity::position).map { Money(it.amountMinor, Currency.getInstance(it.currency)) },
)

private fun TourEntity.toDomain(costs: List<Money>): Tour = Tour(
    id = id,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    destination = destination,
    tourType = TourType.valueOf(tourType),
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    costs = costs,
    pitchAssigned = pitchAssigned,
    electricityFlatRate = ElectricityFlatRate.valueOf(electricityFlatRate),
    lteQuality = LteQuality.valueOf(lteQuality),
    pitchSlope = PitchSlope.valueOf(pitchSlope),
    levelingBlocksUsed = levelingBlocksUsed,
    notes = notes,
    mapLink = mapLink,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

internal fun Tour.toEntity(): TourEntity = TourEntity(
    id = id,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    destination = destination,
    tourType = tourType.name,
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    pitchAssigned = pitchAssigned,
    electricityFlatRate = electricityFlatRate.name,
    lteQuality = lteQuality.name,
    pitchSlope = pitchSlope.name,
    levelingBlocksUsed = levelingBlocksUsed,
    notes = notes,
    mapLink = mapLink,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun Tour.toCostEntities(): List<TourCostEntity> =
    costs.mapIndexed { index, cost -> TourCostEntity(id, cost.currency.currencyCode, cost.minor, index) }

internal fun CostSumRow.toDomain(): Money = Money(amountMinor, Currency.getInstance(currency))

internal fun TotalsRow.toDomain(costs: List<Money>): TourTotals =
    TourTotals(tours, distanceKm, travelDays, overnightStays, costs)

internal fun YearTotalsRow.toDomain(costs: List<Money>): YearTotals =
    YearTotals(year, TourTotals(tours, distanceKm, travelDays, overnightStays, costs))
