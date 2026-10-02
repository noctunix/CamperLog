package de.hannes.camperlog.share

import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TourCsvTest {

    @Test
    fun plainFieldsStayUnquoted() {
        assertEquals("Gardasee", escapeCsv("Gardasee"))
        assertEquals("", escapeCsv(""))
        assertEquals("Ä-Ö ü;ß", escapeCsv("Ä-Ö ü;ß"))
    }

    @Test
    fun specialCharactersAreQuoted() {
        assertEquals("\"Lyon, Frankreich\"", escapeCsv("Lyon, Frankreich"))
        assertEquals("\"Platz \"\"Seeblick\"\"\"", escapeCsv("Platz \"Seeblick\""))
        assertEquals("\"Zeile 1\nZeile 2\"", escapeCsv("Zeile 1\nZeile 2"))
        assertEquals("\"a\r\nb\"", escapeCsv("a\r\nb"))
        assertEquals("\" eingerückt\"", escapeCsv(" eingerückt"))
        assertEquals("\"Ende \"", escapeCsv("Ende "))
    }

    @Test
    fun exportHasHeaderAndOneCrlfTerminatedRowPerTour() {
        val tour = Tour(
            id = 3,
            startDate = LocalDate.of(2026, 7, 10),
            endDate = LocalDate.of(2026, 7, 12),
            destination = "Bodensee, Nordufer",
            tourType = TourType.entries.first(),
            travelDays = 3,
            overnightStays = 2,
            distanceKm = 412,
            costCents = 8_950,
            pitchAssigned = true,
            electricityFlatRate = ElectricityFlatRate.NOT_USED,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = false,
            notes = "Sagte: \"toll\"",
            mapLink = null,
            createdAt = Instant.parse("2026-07-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-14T09:30:00Z"),
        )

        val lines = toursToCsv(listOf(tour)).split("\r\n")

        assertEquals(3, lines.size)
        assertEquals(CSV_HEADER.joinToString(","), lines[0])
        assertEquals(
            "3,2026-07-10,2026-07-12,\"Bodensee, Nordufer\",${tour.tourType.label},3,2,412,89.50,ja," +
                "${ElectricityFlatRate.NOT_USED.label},${LteQuality.GOOD.label},${PitchSlope.LEVEL.label},nein," +
                "\"Sagte: \"\"toll\"\"\",,2026-07-13T08:00:00Z,2026-07-14T09:30:00Z",
            lines[1],
        )
        assertEquals("", lines[2])
    }

    @Test
    fun emptyExportContainsOnlyHeader() {
        assertEquals(CSV_HEADER.joinToString(",") + "\r\n", toursToCsv(emptyList()))
    }
}
