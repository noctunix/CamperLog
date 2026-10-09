package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class StationTest {

    private val tour = Tour(
        id = 1,
        startDate = LocalDate.of(2026, 7, 4),
        endDate = LocalDate.of(2026, 7, 17),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 14,
        overnightStays = 13,
        distanceKm = 3420,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(date: LocalDate, type: StationType = StationType.SIGHT, time: LocalTime? = null, nights: Int? = null) = Station(
        vehicleId = 1,
        tourId = 1,
        type = type,
        date = date,
        time = time,
        nights = nights,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun todayWithinTourIsPreferredOverStations() {
        val today = LocalDate.of(2026, 7, 10)
        val date = defaultStationDate(tour, listOf(station(LocalDate.of(2026, 7, 4))), today)
        assertEquals(today, date)
    }

    @Test
    fun withoutStationsFallsBackToTourStart() {
        val today = LocalDate.of(2026, 9, 1)
        assertEquals(tour.startDate, defaultStationDate(tour, emptyList(), today))
    }

    @Test
    fun suggestsDayAfterLastStationWhenTodayIsOutsideTheTour() {
        val today = LocalDate.of(2026, 9, 1)
        val stations = listOf(station(LocalDate.of(2026, 7, 4)), station(LocalDate.of(2026, 7, 6)))
        assertEquals(LocalDate.of(2026, 7, 7), defaultStationDate(tour, stations, today))
    }

    @Test
    fun overnightStationAddsItsNights() {
        val today = LocalDate.of(2026, 9, 1)
        val stations = listOf(station(LocalDate.of(2026, 7, 4), type = StationType.OVERNIGHT, nights = 3))
        assertEquals(LocalDate.of(2026, 7, 7), defaultStationDate(tour, stations, today))
    }

    @Test
    fun suggestionIsCappedAtTourEnd() {
        val today = LocalDate.of(2026, 9, 1)
        val stations = listOf(station(LocalDate.of(2026, 7, 17), type = StationType.OVERNIGHT, nights = 5))
        assertEquals(tour.endDate, defaultStationDate(tour, stations, today))
    }

    private fun vehicle(requiredEnergyTypes: Set<EnergyType> = emptySet()) = Vehicle(
        requiredEnergyTypes = requiredEnergyTypes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun fuelServicesForShowsEverythingWithoutExactlyOneConfiguredVehicle() {
        assertEquals(FUEL_SERVICES, fuelServicesFor(emptyList()))
        assertEquals(FUEL_SERVICES, fuelServicesFor(listOf(vehicle(), vehicle())))
        assertEquals(FUEL_SERVICES, fuelServicesFor(listOf(vehicle(setOf(EnergyType.DIESEL)), vehicle(setOf(EnergyType.ELECTRICITY)))))
    }

    @Test
    fun fuelServicesForShowsEverythingWhenTheSingleVehicleIsNotConfigured() {
        assertEquals(FUEL_SERVICES, fuelServicesFor(listOf(vehicle())))
    }

    @Test
    fun fuelServicesForFiltersToTheSingleVehiclesEnergyTypes() {
        val services = fuelServicesFor(listOf(vehicle(setOf(EnergyType.DIESEL, EnergyType.ELECTRICITY))))
        assertEquals(setOf(StationService.DIESEL, StationService.ELECTRICITY), services)
    }

    @Test
    fun fuelServicesForMapsGasToLpg() {
        val services = fuelServicesFor(listOf(vehicle(setOf(EnergyType.GAS))))
        assertEquals(setOf(StationService.LPG), services)
    }
}
