package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class StationCostTest {

    private val nok = Currency.getInstance("NOK")

    private fun station(type: StationType, costs: List<StationCost> = emptyList()) = Station(
        vehicleId = 1,
        type = type,
        date = LocalDate.of(2026, 7, 4),
        costs = costs,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun defaultCostCategoryPerStationType() {
        assertEquals(CostCategory.PITCH, StationType.OVERNIGHT.defaultCostCategory)
        assertEquals(CostCategory.SUPPLY, StationType.SUPPLY.defaultCostCategory)
        assertEquals(CostCategory.FUEL, StationType.FUEL.defaultCostCategory)
        assertEquals(CostCategory.TOLL, StationType.TOLL.defaultCostCategory)
        assertEquals(CostCategory.FERRY, StationType.FERRY.defaultCostCategory)
        assertEquals(CostCategory.FOOD, StationType.FOOD.defaultCostCategory)
        assertEquals(CostCategory.OTHER, StationType.SIGHT.defaultCostCategory)
        assertEquals(CostCategory.OTHER, StationType.OTHER.defaultCostCategory)
    }

    @Test
    fun effectiveCostsWithoutElectricityCostAreJustTheManualCosts() {
        val costs = listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR)))
        assertEquals(costs, station(StationType.SUPPLY, costs).effectiveCosts())
    }

    @Test
    fun effectiveCostsAddTheDerivedElectricityCostAsItsOwnCategory() {
        val station = station(StationType.OVERNIGHT, listOf(StationCost(CostCategory.PITCH, Money(1000, EUR)))).copy(
            nights = 2,
            electricityBilling = ElectricityBilling.FLAT_PER_NIGHT,
            electricityCurrency = EUR,
            electricityFlatAmount = Money(400, EUR),
        )
        assertEquals(
            listOf(StationCost(CostCategory.PITCH, Money(1000, EUR)), StationCost(CostCategory.ELECTRICITY, Money(800, EUR))),
            station.effectiveCosts(),
        )
    }

    @Test
    fun effectiveCostsMergeAManualElectricityLineWithTheDerivedAmount() {
        val station = station(
            StationType.OVERNIGHT,
            listOf(StationCost(CostCategory.ELECTRICITY, Money(100, EUR), "Zuschlag")),
        ).copy(
            electricityBilling = ElectricityBilling.FLAT_PER_STAY,
            electricityCurrency = EUR,
            electricityFlatAmount = Money(900, EUR),
        )
        assertEquals(listOf(StationCost(CostCategory.ELECTRICITY, Money(1000, EUR), "Zuschlag")), station.effectiveCosts())
    }

    @Test
    fun stationCostTotalsSumAcrossStationsAndCurrencies() {
        val stations = listOf(
            station(StationType.SUPPLY, listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR)))),
            station(StationType.TOLL, listOf(StationCost(CostCategory.TOLL, Money(200, EUR)))),
            station(StationType.TOLL, listOf(StationCost(CostCategory.TOLL, Money(3000, nok)))),
        )
        assertEquals(listOf(Money(700, EUR), Money(3000, nok)), stations.stationCostTotals())
    }

    @Test
    fun costsByCategoryGroupsAcrossStations() {
        val stations = listOf(
            station(StationType.SUPPLY, listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR)))),
            station(StationType.SUPPLY, listOf(StationCost(CostCategory.SUPPLY, Money(300, EUR)))),
            station(StationType.TOLL, listOf(StationCost(CostCategory.TOLL, Money(200, EUR)))),
        )
        assertEquals(
            mapOf(CostCategory.SUPPLY to listOf(Money(800, EUR)), CostCategory.TOLL to listOf(Money(200, EUR))),
            stations.costsByCategory(),
        )
    }

    @Test
    fun totalCostsAddManualTourCostsAndStationCostsOfTheTour() {
        val tour = Tour(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 3),
            destination = "Lofoten",
            tourType = TourType.WEEKEND,
            travelDays = 3,
            overnightStays = 2,
            distanceKm = 100,
            costs = listOf(Money(1000, EUR)),
            notes = "",
            mapLink = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val stations = listOf(station(StationType.SUPPLY, listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR)))))
        assertEquals(listOf(Money(1500, EUR)), tour.totalCosts(stations))
    }

    @Test
    fun totalCostsWithoutStationsIsJustTheManualCosts() {
        val tour = Tour(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 3),
            destination = "Lofoten",
            tourType = TourType.WEEKEND,
            travelDays = 3,
            overnightStays = 2,
            distanceKm = 100,
            costs = listOf(Money(1000, EUR)),
            notes = "",
            mapLink = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertEquals(listOf(Money(1000, EUR)), tour.totalCosts(emptyList()))
    }
}
