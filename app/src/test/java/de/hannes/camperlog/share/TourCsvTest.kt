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
    fun formulaTriggersArePrefixedWithApostrophe() {
        assertEquals("'=HYPERLINK(\"x\")", neutralizeFormula("=HYPERLINK(\"x\")"))
        assertEquals("'+49 Platz", neutralizeFormula("+49 Platz"))
        assertEquals("'-2+3", neutralizeFormula("-2+3"))
        assertEquals("'@SUM(A1)", neutralizeFormula("@SUM(A1)"))
        assertEquals("'\t=1", neutralizeFormula("\t=1"))
        assertEquals("'\r=1", neutralizeFormula("\r=1"))
        assertEquals("'  =1+1", neutralizeFormula("  =1+1"))
    }

    @Test
    fun harmlessTextIsNotChanged() {
        assertEquals("", neutralizeFormula(""))
        assertEquals("Gardasee", neutralizeFormula("Gardasee"))
        assertEquals("Ä-Ö = schön", neutralizeFormula("Ä-Ö = schön"))
        assertEquals("https://example.org/?a=1", neutralizeFormula("https://example.org/?a=1"))
    }

    @Test
    fun exportNeutralizesFreeTextButKeepsNumbersAndDates() {
        val tour = Tour(
            id = 1,
            startDate = LocalDate.of(2026, 7, 10),
            endDate = LocalDate.of(2026, 7, 10),
            destination = "=cmd|' /C calc'!A0",
            tourType = TourType.DAY_TRIP,
            travelDays = 1,
            overnightStays = 0,
            distanceKm = 80,
            costCents = 0,
            pitchAssigned = false,
            electricityFlatRate = ElectricityFlatRate.NO,
            lteQuality = LteQuality.OK,
            pitchSlope = PitchSlope.SLOPED,
            levelingBlocksUsed = true,
            notes = "@Kontakt, bitte",
            mapLink = null,
            createdAt = Instant.parse("2026-07-10T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-10T08:00:00Z"),
        )

        val row = toursToCsv(listOf(tour)).split("\r\n")[1]

        assertEquals(
            "1,2026-07-10,2026-07-10,'=cmd|' /C calc'!A0,${TourType.DAY_TRIP.label},1,0,80,0.00,nein," +
                "${ElectricityFlatRate.NO.label},${LteQuality.OK.label},${PitchSlope.SLOPED.label},ja," +
                "\"'@Kontakt, bitte\",,2026-07-10T08:00:00Z,2026-07-10T08:00:00Z",
            row,
        )
    }

    @Test
    fun emptyExportContainsOnlyHeader() {
        assertEquals(CSV_HEADER.joinToString(",") + "\r\n", toursToCsv(emptyList()))
    }
}
