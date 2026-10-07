package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val START: LocalDate = LocalDate.of(2026, 7, 1)
private val END: LocalDate = LocalDate.of(2026, 7, 10)

private fun tour() = Tour(
    vehicleId = 1,
    startDate = START,
    endDate = END,
    destination = "",
    tourType = TourType.VACATION,
    travelDays = 10,
    overnightStays = 9,
    distanceKm = 0,
    costs = emptyList(),
    notes = "",
    mapLink = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

private fun vignette(id: Long, country: String?, validUntil: LocalDate?) = Station(
    id = id,
    vehicleId = 1,
    type = StationType.TOLL,
    date = START,
    tollKind = TollKind.VIGNETTE,
    tollCountry = country,
    tollValidUntil = validUntil,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

/** [expiringVignettes]: welche Vignetten einer Tour vor ihrem Ende ablaufen. */
class VignetteWarningsTest {

    @Test
    fun flagsAVignetteExpiringBeforeTourEnd() {
        val stations = listOf(vignette(1, "AT", END.minusDays(1)))

        val result = expiringVignettes(tour(), stations)

        assertEquals(listOf(ExpiringVignette(1, "AT", END.minusDays(1))), result)
    }

    @Test
    fun aSecondLaterVignetteOfTheSameCountryCoversTheRestOfTheTour() {
        val stations = listOf(
            vignette(1, "AT", END.minusDays(5)),
            vignette(2, "AT", END.plusDays(1)),
        )

        val result = expiringVignettes(tour(), stations)

        assertEquals(emptyList<ExpiringVignette>(), result)
    }

    @Test
    fun stillFlagsTheCountryWhenTheLatestVignetteAlsoExpiresBeforeTourEnd() {
        val stations = listOf(
            vignette(1, "AT", END.minusDays(5)),
            vignette(2, "AT", END.minusDays(1)),
        )

        val result = expiringVignettes(tour(), stations)

        assertEquals(listOf(ExpiringVignette(2, "AT", END.minusDays(1))), result)
    }

    @Test
    fun evaluatesVignettesWithoutACountryIndividually() {
        val stations = listOf(
            vignette(1, null, END.minusDays(5)),
            vignette(2, null, END.plusDays(1)),
        )

        val result = expiringVignettes(tour(), stations)

        assertEquals(listOf(ExpiringVignette(1, null, END.minusDays(5))), result)
    }

    @Test
    fun doesNotFlagAVignetteValidUntilExactlyTheTourEnd() {
        val stations = listOf(vignette(1, "AT", END))

        val result = expiringVignettes(tour(), stations)

        assertEquals(emptyList<ExpiringVignette>(), result)
    }

    @Test
    fun ignoresVignettesWithoutAValidUntilDate() {
        val stations = listOf(vignette(1, "AT", null))

        val result = expiringVignettes(tour(), stations)

        assertEquals(emptyList<ExpiringVignette>(), result)
    }

    @Test
    fun ignoresNonVignetteTollStationsAndOtherStationTypes() {
        val stations = listOf(
            Station(
                id = 1,
                vehicleId = 1,
                type = StationType.TOLL,
                date = START,
                tollKind = TollKind.MOTORWAY,
                tollValidUntil = END.minusDays(1),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )

        val result = expiringVignettes(tour(), stations)

        assertEquals(emptyList<ExpiringVignette>(), result)
    }
}
