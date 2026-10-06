package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class StationInputTest {

    private val valid = StationInput(
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        name = "Camping Moskenes",
        place = "Moskenes, Norwegen",
        nights = "2",
    )

    private val validToll = valid.copy(type = StationType.TOLL, nights = "")
    private val validFerry = valid.copy(type = StationType.FERRY, nights = "")

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
            electricityBilling = ElectricityBilling.FLAT_PER_STAY,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = true,
            services = setOf(StationService.DIESEL, StationService.FRESH_WATER, StationService.LPG),
        )
        val station = input.toStation(null)

        assertNull(station.nights)
        assertNull(station.siteKind)
        assertNull(station.pitchAssigned)
        assertNull(station.electricityBilling)
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
    fun futureDateWithASyncedServiceIsInvalid() {
        val today = LocalDate.of(2026, 7, 4)
        val tomorrow = today.plusDays(1)

        assertEquals(
            StationError.FUTURE_DATE,
            valid.copy(date = tomorrow, services = setOf(StationService.CASSETTE)).validate(today)[StationField.SERVICES],
        )
        assertEquals(
            StationError.FUTURE_DATE,
            valid.copy(date = tomorrow, services = setOf(StationService.GREY_WATER)).validate(today)[StationField.SERVICES],
        )
        assertEquals(
            StationError.FUTURE_DATE,
            valid.copy(date = tomorrow, services = setOf(StationService.GAS)).validate(today)[StationField.SERVICES],
        )
    }

    @Test
    fun futureDateWithoutASyncedServiceStaysValid() {
        val today = LocalDate.of(2026, 7, 4)
        val tomorrow = today.plusDays(1)

        assertTrue(valid.copy(date = tomorrow).validate(today).isEmpty())
        assertTrue(valid.copy(date = tomorrow, services = setOf(StationService.FRESH_WATER)).validate(today).isEmpty())
    }

    @Test
    fun todayWithASyncedServiceStaysValid() {
        val today = LocalDate.of(2026, 7, 4)
        assertTrue(valid.copy(date = today, services = setOf(StationService.CASSETTE)).validate(today).isEmpty())
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
        assertEquals(emptySet<StationService>(), StationType.TOLL.allowedServices)
        assertEquals(emptySet<StationService>(), StationType.OTHER.allowedServices)
    }

    @Test
    fun electricityPriceBelowOrAtTheBoundIsValid() {
        assertTrue(valid.copy(electricityPricePerKwh = "100").validate().isEmpty())
        assertTrue(valid.copy(electricityPricePerKwh = "0").validate().isEmpty())
    }

    @Test
    fun electricityPriceAboveTheBoundIsInvalid() {
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityPricePerKwh = "100.01").validate()[StationField.ELECTRICITY])
    }

    @Test
    fun electricityKwhFieldsAboveTheBoundAreInvalid() {
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityMeterStart = "100001").validate()[StationField.ELECTRICITY])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityMeterEnd = "100001").validate()[StationField.ELECTRICITY])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityKwhUsed = "100001").validate()[StationField.ELECTRICITY])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityKwhPerCoin = "100001").validate()[StationField.ELECTRICITY])
        assertTrue(valid.copy(electricityKwhUsed = "100000").validate().isEmpty())
    }

    @Test
    fun electricityCoinsAboveTheBoundAreInvalid() {
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityCoinsUsed = "10001").validate()[StationField.ELECTRICITY])
        assertTrue(valid.copy(electricityCoinsUsed = "10000").validate().isEmpty())
    }

    @Test
    fun electricityMoneyFieldsRejectInvalidAmounts() {
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityFlatAmount = "nicht-numerisch").validate()[StationField.ELECTRICITY])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityBaseFee = "nicht-numerisch").validate()[StationField.ELECTRICITY])
        assertEquals(StationError.INVALID_NUMBER, valid.copy(electricityCoinPrice = "nicht-numerisch").validate()[StationField.ELECTRICITY])
    }

    @Test
    fun electricityPricePerKwhIsLocaleAware() {
        assertTrue(valid.copy(electricityPricePerKwh = "0,35").validate(locale = Locale.GERMANY).isEmpty())
        assertTrue(valid.copy(electricityPricePerKwh = "0.35").validate(locale = Locale.US).isEmpty())
    }

    @Test
    fun electricityBoundsAreOnlyCheckedForOvernightStations() {
        assertTrue(valid.copy(type = StationType.FUEL, electricityPricePerKwh = "999").validate().isEmpty())
    }

    @Test
    fun tollCountryAcceptsAKnownIsoCodeCaseInsensitively() {
        assertTrue(validToll.copy(tollCountry = "de").validate().isEmpty())
        assertTrue(validToll.copy(tollCountry = "AT").validate().isEmpty())
        assertTrue(validToll.copy(tollCountry = "").validate().isEmpty())
    }

    @Test
    fun tollCountryRejectsAnUnknownCode() {
        assertEquals(StationError.INVALID_COUNTRY, validToll.copy(tollCountry = "XX").validate()[StationField.TOLL_COUNTRY])
    }

    @Test
    fun tollCountryIsOnlyCheckedForTollStations() {
        assertTrue(valid.copy(tollCountry = "XX").validate().isEmpty())
    }

    @Test
    fun tollValidUntilMustNotBeBeforeValidFrom() {
        val from = LocalDate.of(2026, 1, 1)
        assertEquals(
            StationError.END_BEFORE_START,
            validToll.copy(tollValidFrom = from, tollValidUntil = from.minusDays(1)).validate()[StationField.TOLL_VALID_UNTIL],
        )
        assertTrue(validToll.copy(tollValidFrom = from, tollValidUntil = from).validate().isEmpty())
        assertTrue(validToll.copy(tollValidFrom = from, tollValidUntil = from.plusDays(1)).validate().isEmpty())
    }

    @Test
    fun toStationKeepsElectricityOnlyForOvernightStations() {
        val input = valid.copy(
            type = StationType.FUEL,
            electricityBilling = ElectricityBilling.FLAT_PER_STAY,
            electricityFlatAmount = "12",
        )
        val station = input.toStation(null)
        assertNull(station.electricityBilling)
        assertNull(station.electricityCurrency)
        assertNull(station.electricityFlatAmount)
    }

    @Test
    fun toStationKeepsElectricityForOvernightStations() {
        val input = valid.copy(
            electricityBilling = ElectricityBilling.FLAT_PER_NIGHT,
            electricityCurrency = EUR,
            electricityFlatAmount = "12,50",
        )
        val station = input.toStation(null, Locale.GERMANY)
        assertEquals(ElectricityBilling.FLAT_PER_NIGHT, station.electricityBilling)
        assertEquals(EUR, station.electricityCurrency)
        assertEquals(Money(1250, EUR), station.electricityFlatAmount)
    }

    @Test
    fun toStationKeepsTollFieldsOnlyForTollStations() {
        val station = validToll.copy(
            tollKind = TollKind.VIGNETTE,
            tollPaymentMethod = "App",
            tollCountry = "at",
            tollValidFrom = LocalDate.of(2026, 1, 1),
            tollValidUntil = LocalDate.of(2026, 12, 31),
        ).toStation(null)
        assertEquals(TollKind.VIGNETTE, station.tollKind)
        assertEquals("App", station.tollPaymentMethod)
        assertEquals("AT", station.tollCountry)
        assertEquals(LocalDate.of(2026, 1, 1), station.tollValidFrom)
        assertEquals(LocalDate.of(2026, 12, 31), station.tollValidUntil)

        val notToll = validToll.copy(type = StationType.OVERNIGHT, nights = "1", tollKind = TollKind.VIGNETTE, tollCountry = "at").toStation(null)
        assertNull(notToll.tollKind)
        assertNull(notToll.tollCountry)
        assertEquals("", notToll.tollPaymentMethod)
    }

    @Test
    fun toStationKeepsFerryBookingReferenceOnlyForFerryStations() {
        val ferry = validFerry.copy(ferryBookingReference = "ABC-123").toStation(null)
        assertEquals("ABC-123", ferry.ferryBookingReference)

        val notFerry = valid.copy(ferryBookingReference = "ABC-123").toStation(null)
        assertEquals("", notFerry.ferryBookingReference)
    }

    @Test
    fun toStationKeepsCostsFromTheOriginalStation() {
        val original = valid.toStation(null).copy(
            id = 7,
            costs = listOf(StationCost(CostCategory.PITCH, Money(1500, EUR))),
        )
        val station = valid.copy(name = "Anderer Name").toStation(original)
        assertEquals(listOf(StationCost(CostCategory.PITCH, Money(1500, EUR))), station.costs)
    }

    @Test
    fun roundTripThroughInputIsLosslessWithElectricityFields() {
        val station = valid.toStation(null).copy(
            electricityBilling = ElectricityBilling.BASE_PLUS_METERED,
            electricityCurrency = EUR,
            electricityBaseFee = Money(300, EUR),
            electricityPricePerKwh = BigDecimal("0.35"),
            electricityMeterStart = BigDecimal("100.5"),
            electricityMeterEnd = BigDecimal("110.25"),
        )
        assertEquals(station, station.toInput(Locale.GERMANY).toStation(station, Locale.GERMANY))
    }

    @Test
    fun roundTripThroughInputIsLosslessForTollStations() {
        val station = validToll.toStation(null).copy(
            tollKind = TollKind.VIGNETTE,
            tollPaymentMethod = "App",
            tollCountry = "AT",
            tollValidFrom = LocalDate.of(2026, 1, 1),
            tollValidUntil = LocalDate.of(2026, 12, 31),
        )
        assertEquals(station, station.toInput().toStation(station))
    }
}
