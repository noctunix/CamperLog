package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class TourTextTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val tour = Tour(
        id = 1,
        startDate = LocalDate.of(2026, 7, 10),
        endDate = LocalDate.of(2026, 7, 12),
        destination = "Bodensee",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 412,
        costs = listOf(Money(8_950, EUR)),
        notes = "Ruhiger Platz",
        mapLink = "https://example.org/karte",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun summaryUsesResourceLabels() {
        assertEquals(
            listOf(
                "Tour nach Bodensee",
                "10.07.2026 – 12.07.2026 (Wochenende)",
                "3 Reisetage, 2 Übernachtungen, 412 km",
                "Kosten: 89,50\u00A0€",
                "Notizen: Ruhiger Platz",
                "Karte: https://example.org/karte",
            ),
            tourShareText(resources, tour).lines(),
        )
    }

    @Test
    fun costsInSeveralCurrenciesAreJoined() {
        val lines = tourShareText(resources, tour.copy(costs = listOf(Money(8_950, EUR), Money(3_500, ISK)))).lines()

        assertEquals("Kosten: 89,50\u00A0€ · 3.500\u00A0ISK", lines[3])
    }

    @Test
    fun tourWithoutCostsShowsZero() {
        assertEquals("Kosten: 0,00\u00A0€", tourShareText(resources, tour.copy(costs = emptyList())).lines()[3])
    }

    @Test
    fun singleDayTripUsesSingularAndSkipsEmptyOptionalLines() {
        val dayTrip = tour.copy(
            endDate = tour.startDate,
            travelDays = 1,
            overnightStays = 1,
            notes = " ",
            mapLink = null,
        )

        val lines = tourShareText(resources, dayTrip).lines()

        assertEquals("1 Reisetag, 1 Übernachtung, 412 km", lines[2])
        assertEquals("Kosten: 89,50\u00A0€", lines.last())
        assertEquals("10.07.2026 (Wochenende)", lines[1])
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun englishSystemUsesEnglishDatesLabelsAndAmounts() {
        val lines = tourShareText(resources, tour).lines()

        assertEquals("Jul 10, 2026 – Jul 12, 2026 (Weekend)", lines[1])
        assertEquals("Costs: €89.50", lines[3])
    }
}

private val ISK: Currency = Currency.getInstance("ISK")
