package de.hannes.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TourInputTest {

    private val de = Locale.GERMANY

    private val valid = TourInput(
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 3),
        destination = "Gardasee",
        travelDays = "3",
        overnightStays = "2",
        distanceKm = "840",
        cost = "123,45",
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate(de).isEmpty())
    }

    @Test
    fun emptyOptionalNumbersCountAsZero() {
        val input = valid.copy(travelDays = "", overnightStays = "", distanceKm = "", cost = "")
        assertTrue(input.validate(de).isEmpty())
        val tour = input.toTour(null, de)
        assertEquals(0, tour.travelDays)
        assertEquals(0L, tour.costCents)
    }

    @Test
    fun missingRequiredFieldsAreReported() {
        val errors = TourInput().validate(de)
        assertEquals(setOf(TourField.START_DATE, TourField.END_DATE, TourField.DESTINATION), errors.keys)
    }

    @Test
    fun blankDestinationIsInvalid() {
        assertTrue(TourField.DESTINATION in valid.copy(destination = "   ").validate(de))
    }

    @Test
    fun endBeforeStartIsInvalid() {
        val errors = valid.copy(endDate = LocalDate.of(2026, 4, 30)).validate(de)
        assertEquals(TourError.END_BEFORE_START, errors[TourField.END_DATE])
    }

    @Test
    fun sameDayTourIsValid() {
        assertTrue(valid.copy(endDate = valid.startDate, travelDays = "1", overnightStays = "0").validate(de).isEmpty())
    }

    @Test
    fun negativeAndMalformedNumbersAreInvalid() {
        val errors = valid.copy(travelDays = "-1", distanceKm = "12km", cost = "-5").validate(de)
        assertEquals(TourError.NEGATIVE_NUMBER, errors[TourField.TRAVEL_DAYS])
        assertEquals(TourError.INVALID_NUMBER, errors[TourField.DISTANCE_KM])
        assertEquals(TourError.INVALID_AMOUNT, errors[TourField.COST])
    }

    @Test
    fun moreNightsThanDaysIsInvalid() {
        assertEquals(TourError.MORE_NIGHTS_THAN_DAYS, valid.copy(overnightStays = "4").validate(de)[TourField.OVERNIGHT_STAYS])
    }

    @Test
    fun mapLinkMustBeWebUrl() {
        assertEquals(TourError.NOT_A_WEB_LINK, valid.copy(mapLink = "javascript:alert(1)").validate(de)[TourField.MAP_LINK])
        assertTrue(TourField.MAP_LINK in valid.copy(mapLink = "maps.google.com").validate(de))
        assertTrue(valid.copy(mapLink = " https://maps.app.goo.gl/abc ").validate(de).isEmpty())
    }

    @Test
    fun toTourTrimsAndKeepsIdentity() {
        val original = valid.toTour(null, de).copy(id = 7)
        val tour = valid.copy(destination = "  Ostsee ", mapLink = "  ").toTour(original, de)
        assertEquals(7L, tour.id)
        assertEquals("Ostsee", tour.destination)
        assertNull(tour.mapLink)
        assertEquals(12_345L, tour.costCents)
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val tour = valid.toTour(null, de)
        assertEquals(tour, tour.toInput(de).toTour(tour, de))
    }

    @Test
    fun costFollowsLocaleNumberFormat() {
        val tour = valid.toTour(null, de)
        assertEquals("123.45", tour.toInput(Locale.US).cost)
        assertEquals(tour, tour.toInput(Locale.US).toTour(tour, Locale.US))
        assertEquals(TourError.INVALID_AMOUNT, valid.copy(cost = "1,234").validate(de)[TourField.COST])
        assertTrue(valid.copy(cost = "1,234").validate(Locale.US).isEmpty())
    }

    @Test
    fun travelDaysIncludeBothEnds() {
        assertEquals(1L, travelDaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
        assertEquals(3L, travelDaysBetween(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 1)))
    }
}
