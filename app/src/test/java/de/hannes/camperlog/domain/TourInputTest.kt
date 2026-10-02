package de.hannes.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TourInputTest {

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
        assertTrue(valid.validate().isEmpty())
    }

    @Test
    fun emptyOptionalNumbersCountAsZero() {
        val input = valid.copy(travelDays = "", overnightStays = "", distanceKm = "", cost = "")
        assertTrue(input.validate().isEmpty())
        val tour = input.toTour(null)
        assertEquals(0, tour.travelDays)
        assertEquals(0L, tour.costCents)
    }

    @Test
    fun missingRequiredFieldsAreReported() {
        val errors = TourInput().validate()
        assertEquals(setOf(TourField.START_DATE, TourField.END_DATE, TourField.DESTINATION), errors.keys)
    }

    @Test
    fun blankDestinationIsInvalid() {
        assertTrue(TourField.DESTINATION in valid.copy(destination = "   ").validate())
    }

    @Test
    fun endBeforeStartIsInvalid() {
        val errors = valid.copy(endDate = LocalDate.of(2026, 4, 30)).validate()
        assertEquals("Enddatum liegt vor dem Startdatum", errors[TourField.END_DATE])
    }

    @Test
    fun sameDayTourIsValid() {
        assertTrue(valid.copy(endDate = valid.startDate, travelDays = "1", overnightStays = "0").validate().isEmpty())
    }

    @Test
    fun negativeAndMalformedNumbersAreInvalid() {
        val errors = valid.copy(travelDays = "-1", distanceKm = "12km", cost = "-5").validate()
        assertEquals("Keine negativen Zahlen", errors[TourField.TRAVEL_DAYS])
        assertEquals("Ungültige Zahl", errors[TourField.DISTANCE_KM])
        assertTrue(TourField.COST in errors)
    }

    @Test
    fun moreNightsThanDaysIsInvalid() {
        assertTrue(TourField.OVERNIGHT_STAYS in valid.copy(overnightStays = "4").validate())
    }

    @Test
    fun mapLinkMustBeWebUrl() {
        assertTrue(TourField.MAP_LINK in valid.copy(mapLink = "javascript:alert(1)").validate())
        assertTrue(TourField.MAP_LINK in valid.copy(mapLink = "maps.google.com").validate())
        assertTrue(valid.copy(mapLink = " https://maps.app.goo.gl/abc ").validate().isEmpty())
    }

    @Test
    fun toTourTrimsAndKeepsIdentity() {
        val original = valid.toTour(null).copy(id = 7)
        val tour = valid.copy(destination = "  Ostsee ", mapLink = "  ").toTour(original)
        assertEquals(7L, tour.id)
        assertEquals("Ostsee", tour.destination)
        assertNull(tour.mapLink)
        assertEquals(12_345L, tour.costCents)
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val tour = valid.toTour(null)
        assertEquals(tour, tour.toInput().toTour(tour))
    }

    @Test
    fun travelDaysIncludeBothEnds() {
        assertEquals(1L, travelDaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
        assertEquals(3L, travelDaysBetween(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 1)))
    }
}
