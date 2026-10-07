package app.restvolt.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationType
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
                "Kosten: 89,50 €",
                "Stationen (0)",
                "Notizen: Ruhiger Platz",
                "Karte: https://example.org/karte",
            ),
            tourShareText(resources, tour, emptyList(), emptySet()).lines(),
        )
    }

    @Test
    fun costsInSeveralCurrenciesAreJoined() {
        val lines = tourShareText(resources, tour.copy(costs = listOf(Money(8_950, EUR), Money(3_500, ISK))), emptyList(), emptySet()).lines()

        assertEquals("Kosten: 89,50 € · 3.500 ISK", lines[3])
    }

    @Test
    fun tourWithoutCostsShowsZero() {
        assertEquals("Kosten: 0,00 €", tourShareText(resources, tour.copy(costs = emptyList()), emptyList(), emptySet()).lines()[3])
    }

    @Test
    fun costsByCategoryAreListedInCategoryOrder() {
        val stations = listOf(
            station(type = StationType.FUEL, costs = listOf(StationCost(CostCategory.FUEL, Money(4_000, EUR)))),
            station(type = StationType.OVERNIGHT, costs = listOf(StationCost(CostCategory.PITCH, Money(2_000, EUR)))),
        )

        val lines = tourShareText(resources, tour.copy(costs = emptyList()), stations, emptySet()).lines()

        assertEquals("Kosten: 60,00 €", lines[3])
        assertEquals("Stellplatz: 20,00 €", lines[4])
        assertEquals("Tanken/Laden: 40,00 €", lines[5])
    }

    @Test
    fun countriesLineListsLocalizedNamesSortedAlphabetically() {
        val lines = tourShareText(resources, tour, emptyList(), setOf("NO", "DE")).lines()

        assertEquals("Länder: Deutschland, Norwegen", lines[4])
    }

    @Test
    fun stopsCountReflectsTheNumberOfStations() {
        val stations = listOf(station(), station())

        val lines = tourShareText(resources, tour, stations, emptySet()).lines()

        assertEquals("Stationen (2)", lines[4])
    }

    @Test
    fun wouldReturnListsOvernightStopsMarkedAsFavoriteButNotOtherFavoriteTypes() {
        val stations = listOf(
            station(type = StationType.OVERNIGHT, name = "Camping Alpenblick", favorite = true),
            station(type = StationType.OVERNIGHT, name = "Wiese am See", favorite = false),
            station(type = StationType.FUEL, favorite = true),
        )

        val lines = tourShareText(resources, tour, stations, emptySet()).lines()

        assertEquals("Stationen (3)", lines[4])
        assertEquals("Gerne wieder: Camping Alpenblick", lines[5])
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

        val lines = tourShareText(resources, dayTrip, emptyList(), emptySet()).lines()

        assertEquals("1 Reisetag, 1 Übernachtung, 412 km", lines[2])
        assertEquals("Kosten: 89,50 €", lines[3])
        assertEquals("Stationen (0)", lines.last())
        assertEquals("10.07.2026 (Wochenende)", lines[1])
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun englishSystemUsesEnglishDatesLabelsAndAmounts() {
        val lines = tourShareText(resources, tour, emptyList(), emptySet()).lines()

        assertEquals("Jul 10, 2026 – Jul 12, 2026 (Weekend)", lines[1])
        assertEquals("Costs: €89.50", lines[3])
    }

    private fun station(
        type: StationType = StationType.OVERNIGHT,
        name: String = "",
        costs: List<StationCost> = emptyList(),
        favorite: Boolean = false,
    ) = Station(
        vehicleId = 1,
        type = type,
        date = LocalDate.of(2026, 7, 10),
        name = name,
        costs = costs,
        favorite = favorite,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

private val ISK: Currency = Currency.getInstance("ISK")
