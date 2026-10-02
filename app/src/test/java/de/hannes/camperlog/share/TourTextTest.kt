package de.hannes.camperlog.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourType
import de.hannes.camperlog.domain.formatEuro
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        costCents = 8_950,
        pitchAssigned = true,
        electricityFlatRate = ElectricityFlatRate.NOT_USED,
        lteQuality = LteQuality.OK,
        pitchSlope = PitchSlope.SLOPED,
        levelingBlocksUsed = true,
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
                "Kosten: ${formatEuro(8_950)}",
                "Stellplatz zugewiesen: ja",
                "Strompauschale: nicht genutzt",
                "LTE: geht so",
                "Stellplatz: abschüssig, Keile genutzt",
                "Notizen: Ruhiger Platz",
                "Karte: https://example.org/karte",
            ),
            tourShareText(resources, tour).lines(),
        )
    }

    @Test
    fun singleDayTripUsesSingularAndSkipsEmptyOptionalLines() {
        val dayTrip = tour.copy(
            endDate = tour.startDate,
            travelDays = 1,
            overnightStays = 1,
            levelingBlocksUsed = false,
            notes = " ",
            mapLink = null,
        )

        val lines = tourShareText(resources, dayTrip).lines()

        assertEquals("1 Reisetag, 1 Übernachtung, 412 km", lines[2])
        assertEquals("Stellplatz: abschüssig, Keile nicht genutzt", lines.last())
    }
}
