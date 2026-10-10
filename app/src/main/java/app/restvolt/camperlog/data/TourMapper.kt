package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.YearTotals
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

internal fun TourWithCosts.toDomain(): Tour = tour.toDomain(
    costs.sortedBy(TourCostEntity::position).map { Money(it.amountMinor, Currency.getInstance(it.currency)) },
    manualCountriesAdded = countries.filter(TourCountryEntity::added).mapTo(mutableSetOf()) { it.code },
    manualCountriesRemoved = countries.filterNot(TourCountryEntity::added).mapTo(mutableSetOf()) { it.code },
)

private fun TourEntity.toDomain(costs: List<Money>, manualCountriesAdded: Set<String>, manualCountriesRemoved: Set<String>): Tour = Tour(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    destination = destination,
    name = name,
    slug = slug,
    tourType = TourType.valueOf(tourType),
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    costs = costs,
    notes = notes,
    mapLink = mapLink,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
    manualCountriesAdded = manualCountriesAdded,
    manualCountriesRemoved = manualCountriesRemoved,
    isDemo = isDemo,
)

internal fun Tour.toEntity(): TourEntity = TourEntity(
    id = id,
    uuid = uuid,
    vehicleId = vehicleId,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    destination = destination,
    name = name,
    slug = slug,
    tourType = tourType.name,
    travelDays = travelDays,
    overnightStays = overnightStays,
    distanceKm = distanceKm,
    notes = notes,
    mapLink = mapLink,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
    isDemo = isDemo,
)

internal fun Tour.toCostEntities(): List<TourCostEntity> =
    costs.mapIndexed { index, cost -> TourCostEntity(id, cost.currency.currencyCode, cost.minor, index) }

internal fun Tour.toCountryEntities(): List<TourCountryEntity> =
    manualCountriesAdded.map { TourCountryEntity(id, it, added = true) } +
        manualCountriesRemoved.map { TourCountryEntity(id, it, added = false) }

internal fun CostSumRow.toDomain(): Money = Money(amountMinor, Currency.getInstance(currency))

internal fun TotalsRow.toDomain(costs: List<Money>, categoryCosts: Map<CostCategory, List<Money>> = emptyMap()): TourTotals =
    TourTotals(tours, distanceKm, travelDays, overnightStays, costs, categoryCosts)

internal fun YearTotalsRow.toDomain(costs: List<Money>, categoryCosts: Map<CostCategory, List<Money>> = emptyMap()): YearTotals =
    YearTotals(year, TourTotals(tours, distanceKm, travelDays, overnightStays, costs, categoryCosts))
