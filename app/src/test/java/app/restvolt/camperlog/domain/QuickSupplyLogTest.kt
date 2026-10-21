package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class QuickSupplyLogTest {

    private val date = LocalDate.of(2026, 7, 4)

    private fun station(services: Set<StationService>, name: String = "", notes: String = "") = Station(
        id = 1,
        vehicleId = 1,
        tourId = 2,
        type = StationType.SUPPLY,
        date = date,
        name = name,
        notes = notes,
        services = services,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun quickSupplyService_mapsTheThreeSyncedTypes() {
        assertEquals(StationService.CASSETTE, quickSupplyService(LogType.CASSETTE_EMPTIED))
        assertEquals(StationService.GREY_WATER, quickSupplyService(LogType.GREY_WATER_EMPTIED))
        assertEquals(StationService.GAS, quickSupplyService(LogType.GAS_BOTTLE_SWAPPED))
    }

    @Test
    fun quickSupplyService_isNullForTheHeaterTypes() {
        assertNull(quickSupplyService(LogType.DIESEL_HEATER_RUN))
        assertNull(quickSupplyService(LogType.GAS_HEATER_RUN))
    }

    @Test
    fun quickSupplyStation_withoutExisting_createsANewSupplyStation() {
        val station = quickSupplyStation(null, vehicleId = 1, service = StationService.CASSETTE, date = date, tourId = 2)

        assertEquals(StationType.SUPPLY, station.type)
        assertEquals(1L, station.vehicleId)
        assertEquals(2L, station.tourId)
        assertEquals(date, station.date)
        assertEquals(setOf(StationService.CASSETTE), station.services)
        assertEquals(0L, station.id)
    }

    @Test
    fun quickSupplyStation_withExisting_addsTheServiceToIt() {
        val existing = station(services = setOf(StationService.GREY_WATER))

        val updated = quickSupplyStation(existing, vehicleId = 1, service = StationService.CASSETTE, date = date, tourId = 2)

        assertEquals(1L, updated.id)
        assertEquals(setOf(StationService.GREY_WATER, StationService.CASSETTE), updated.services)
    }

    @Test
    fun stationAfterRemovingQuickSupply_withOtherServicesLeft_keepsTheStation() {
        val existing = station(services = setOf(StationService.CASSETTE, StationService.GAS))

        val updated = stationAfterRemovingQuickSupply(existing, StationService.CASSETTE)

        assertEquals(setOf(StationService.GAS), updated?.services)
    }

    @Test
    fun stationAfterRemovingQuickSupply_asTheLastServiceAndWithoutOtherContent_deletesTheStation() {
        val existing = station(services = setOf(StationService.CASSETTE))

        val updated = stationAfterRemovingQuickSupply(existing, StationService.CASSETTE)

        assertNull(updated)
    }

    @Test
    fun stationAfterRemovingQuickSupply_withOtherContent_keepsTheEmptyShell() {
        val existing = station(services = setOf(StationService.CASSETTE), name = "Camping Moskenes")

        val updated = stationAfterRemovingQuickSupply(existing, StationService.CASSETTE)

        assertEquals(emptySet<StationService>(), updated?.services)
        assertEquals("Camping Moskenes", updated?.name)
    }
}
