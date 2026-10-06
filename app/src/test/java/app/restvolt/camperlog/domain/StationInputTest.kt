package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StationInputTest {

    private val valid = StationInput(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        name = "Camping Moskenes",
        place = "Moskenes, Norwegen",
        nights = "2",
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate().isEmpty())
    }

    @Test
    fun dateIsRequired() {
        assertEquals(setOf(StationField.DATE), StationInput().validate().keys)
    }

    @Test
    fun bothCoordinatesOrNeitherIsValid() {
        assertTrue(valid.validate().isEmpty())
        assertTrue(valid.copy(latitude = 68.09, longitude = 13.10).validate().isEmpty())
    }

    @Test
    fun onlyOneCoordinateIsInvalid() {
        assertEquals(StationError.COORDINATES_INCOMPLETE, valid.copy(latitude = 68.09).validate()[StationField.COORDINATES])
        assertEquals(StationError.COORDINATES_INCOMPLETE, valid.copy(longitude = 13.10).validate()[StationField.COORDINATES])
    }

    @Test
    fun coordinatesOutOfWgs84RangeAreInvalid() {
        assertEquals(
            StationError.COORDINATES_OUT_OF_RANGE,
            valid.copy(latitude = 91.0, longitude = 13.0).validate()[StationField.COORDINATES],
        )
        assertEquals(
            StationError.COORDINATES_OUT_OF_RANGE,
            valid.copy(latitude = 68.0, longitude = 181.0).validate()[StationField.COORDINATES],
        )
        assertTrue(valid.copy(latitude = -90.0, longitude = -180.0).validate().isEmpty())
        assertTrue(valid.copy(latitude = 90.0, longitude = 180.0).validate().isEmpty())
    }

    @Test
    fun nightsMustBeAtLeastOneWhenGiven() {
        assertTrue(valid.copy(nights = "").validate().isEmpty())
        assertEquals(StationError.TOO_SMALL, valid.copy(nights = "0").validate()[StationField.NIGHTS])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(nights = "abc").validate()[StationField.NIGHTS])
    }

    @Test
    fun nightsAreIgnoredForNonOvernightTypes() {
        assertTrue(valid.copy(type = StationType.FUEL, nights = "0").validate().isEmpty())
    }

    @Test
    fun tooLongTextIsInvalid() {
        assertEquals(StationError.TOO_LONG, valid.copy(name = "x".repeat(MAX_STATION_NAME_LENGTH + 1)).validate()[StationField.NAME])
        assertEquals(StationError.TOO_LONG, valid.copy(place = "x".repeat(MAX_STATION_PLACE_LENGTH + 1)).validate()[StationField.PLACE])
        assertEquals(StationError.TOO_LONG, valid.copy(notes = "x".repeat(MAX_STATION_NOTES_LENGTH + 1)).validate()[StationField.NOTES])
    }

    @Test
    fun mapLinkMustBeAWebUrl() {
        assertEquals(StationError.NOT_A_WEB_LINK, valid.copy(mapLink = "javascript:alert(1)").validate()[StationField.MAP_LINK])
        assertTrue(valid.copy(mapLink = "https://example.org/platz").validate().isEmpty())
    }

    @Test
    fun toStationDropsOvernightFieldsForOtherTypes() {
        val input = valid.copy(
            type = StationType.FUEL,
            nights = "3",
            siteKind = SiteKind.CAMPSITE,
            pitchAssigned = true,
            electricityFlatRate = ElectricityFlatRate.YES,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = true,
            services = setOf(StationService.DIESEL, StationService.FRESH_WATER, StationService.LPG),
        )
        val station = input.toStation(null)

        assertNull(station.nights)
        assertNull(station.siteKind)
        assertNull(station.pitchAssigned)
        assertNull(station.electricityFlatRate)
        assertNull(station.lteQuality)
        assertNull(station.pitchSlope)
        assertNull(station.levelingBlocksUsed)
        assertEquals(setOf(StationService.DIESEL, StationService.FRESH_WATER, StationService.LPG), station.services)
    }

    @Test
    fun toStationDropsServicesNotAllowedForType() {
        val input = valid.copy(type = StationType.SIGHT, services = setOf(StationService.DIESEL, StationService.FRESH_WATER))
        assertEquals(emptySet<StationService>(), input.toStation(null).services)
    }

    @Test
    fun toStationDropsMapLinkWhenCoordinatesArePresent() {
        val input = valid.copy(latitude = 68.09, longitude = 13.10, mapLink = "https://example.org/platz")
        assertNull(input.toStation(null).mapLink)
    }

    @Test
    fun toStationKeepsMapLinkWithoutCoordinates() {
        val input = valid.copy(mapLink = "https://example.org/platz")
        assertEquals("https://example.org/platz", input.toStation(null).mapLink)
    }

    @Test
    fun toStationDropsFavoriteForNonOvernightTypes() {
        val input = valid.copy(type = StationType.FUEL, favorite = true)
        assertEquals(false, input.toStation(null).favorite)
    }

    @Test
    fun toStationKeepsIdentityAndTimestampsFromOriginal() {
        val original = valid.toStation(null).copy(id = 7, uuid = "abc", createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH)
        val station = valid.copy(name = "Anderer Name").toStation(original)
        assertEquals(7L, station.id)
        assertEquals("abc", station.uuid)
        assertEquals("Anderer Name", station.name)
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val station = valid.toStation(null)
        assertEquals(station, station.toInput().toStation(station))
    }

    @Test
    fun allowedServicesPerType() {
        assertEquals(
            setOf(StationService.FRESH_WATER, StationService.GREY_WATER, StationService.CASSETTE, StationService.GAS),
            StationType.OVERNIGHT.allowedServices,
        )
        assertEquals(StationType.OVERNIGHT.allowedServices, StationType.SUPPLY.allowedServices)
        assertTrue(StationType.FUEL.allowedServices.containsAll(StationType.OVERNIGHT.allowedServices))
        assertTrue(StationService.DIESEL in StationType.FUEL.allowedServices)
        assertEquals(emptySet<StationService>(), StationType.SIGHT.allowedServices)
        assertEquals(emptySet<StationService>(), StationType.FOOD.allowedServices)
        assertEquals(emptySet<StationService>(), StationType.FERRY.allowedServices)
        assertEquals(emptySet<StationService>(), StationType.OTHER.allowedServices)
    }
}
