package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Currency
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
        costs = listOf(CostInput("123,45")),
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate(de).isEmpty())
    }

    @Test
    fun emptyOptionalNumbersCountAsZero() {
        val input = valid.copy(travelDays = "", overnightStays = "", distanceKm = "", costs = listOf(CostInput()))
        assertTrue(input.validate(de).isEmpty())
        val tour = input.toTour(null, de)
        assertEquals(0, tour.travelDays)
        assertEquals(emptyList<Money>(), tour.costs)
    }

    @Test
    fun missingRequiredFieldsAreReported() {
        val errors = TourInput().validate(de)
        assertEquals(setOf(TourField.START_DATE), errors.keys)
    }

    @Test
    fun missingEndDateCreatesRunningTour() {
        val input = valid.copy(endDate = null)

        assertTrue(input.validate(de).isEmpty())
        assertNull(input.toTour(null, de).endDate)
    }

    @Test
    fun blankDestinationIsValid() {
        assertTrue(valid.copy(destination = "   ").validate(de).isEmpty())
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
        val errors = valid.copy(travelDays = "-1", distanceKm = "12km", costs = listOf(CostInput("-5"))).validate(de)
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
        assertEquals(listOf(Money(12_345, EUR)), tour.costs)
    }

    @Test
    fun toTourTrimsNameAndSlug() {
        val tour = valid.copy(name = "  Sommerurlaub  ", slug = "  sommerurlaub-2026  ").toTour(null, de)
        assertEquals("Sommerurlaub", tour.name)
        assertEquals("sommerurlaub-2026", tour.slug)
    }

    @Test
    fun suggestSlugTransliteratesLowercasesAndDashesSeparators() {
        assertEquals("ueber-den-aermelkanal", suggestSlug("Über den Ärmelkanal"))
        assertEquals("oedensee-fjaellbacka", suggestSlug("Ödensee / Fjällbacka!"))
        assertEquals("", suggestSlug("   "))
        assertEquals("camping", suggestSlug("-Camping-"))
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val tour = valid.toTour(null, de)
        assertEquals(tour, tour.toInput(de).toTour(tour, de))
    }

    @Test
    fun runningTourRoundTripThroughInputIsLossless() {
        val tour = valid.copy(endDate = null).toTour(null, de)

        assertEquals(tour, tour.toInput(de).toTour(tour, de))
    }

    @Test
    fun costFollowsLocaleNumberFormat() {
        val tour = valid.toTour(null, de)
        assertEquals("123.45", tour.toInput(Locale.US).costs.single().amount)
        assertEquals(tour, tour.toInput(Locale.US).toTour(tour, Locale.US))
        assertEquals(TourError.INVALID_AMOUNT, valid.copy(costs = listOf(CostInput("1,234"))).validate(de)[TourField.COST])
        assertTrue(valid.copy(costs = listOf(CostInput("1,234"))).validate(Locale.US).isEmpty())
    }

    @Test
    fun severalCurrenciesKeepTheirOrderAndDigits() {
        val input = valid.copy(costs = listOf(CostInput("1.450,50", NOK), CostInput("12.000", ISK), CostInput("20", EUR)))
        val tour = input.toTour(null, de)
        assertEquals(listOf(Money(145_050, NOK), Money(12_000, ISK), Money(2_000, EUR)), tour.costs)
        assertEquals(listOf("1450,50", "12000", "20,00"), tour.toInput(de).costs.map(CostInput::amount))
        assertEquals(tour, tour.toInput(de).toTour(tour, de))
    }

    @Test
    fun costErrorsPointToTheFaultyRow() {
        val input = valid.copy(costs = listOf(CostInput("10"), CostInput("1,5", ISK), CostInput("", NOK)))
        assertEquals(mapOf(1 to TourError.INVALID_AMOUNT), input.costErrors(de))
        assertEquals(TourError.INVALID_AMOUNT, input.validate(de)[TourField.COST])
    }

    @Test
    fun tooLargeCostHasOwnError() {
        val input = valid.copy(costs = listOf(CostInput("100.000.000.001")))
        assertEquals(mapOf(0 to TourError.AMOUNT_TOO_LARGE), input.costErrors(de))
        assertEquals(TourError.AMOUNT_TOO_LARGE, input.validate(de)[TourField.COST])
    }

    @Test
    fun blankAndZeroRowsAreDroppedAndDuplicatesSummed() {
        val input = valid.copy(
            costs = listOf(CostInput("10", EUR), CostInput("", NOK), CostInput("0", ISK), CostInput("2,50", EUR)),
        )
        assertEquals(listOf(Money(1_250, EUR)), input.toTour(null, de).costs)
    }

    @Test
    fun tourWithoutCostsGetsOneEmptyEuroRow() {
        val tour = valid.copy(costs = listOf(CostInput())).toTour(null, de)
        assertEquals(listOf(CostInput()), tour.toInput(de).costs)
    }

    @Test
    fun travelDaysIncludeBothEnds() {
        assertEquals(1L, travelDaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
        assertEquals(3L, travelDaysBetween(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 1)))
    }
}

private val NOK: Currency = Currency.getInstance("NOK")
private val ISK: Currency = Currency.getInstance("ISK")
