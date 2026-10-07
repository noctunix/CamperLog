package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

private const val DEFAULT_NAME = "Mein Wohnmobil"

class TourCsvTest {

    @Test
    fun plainFieldsStayUnquoted() {
        assertEquals("Gardasee", escapeCsv("Gardasee"))
        assertEquals("", escapeCsv(""))
        assertEquals("Ä-Ö üß", escapeCsv("Ä-Ö üß"))
    }

    @Test
    fun semicolonIsQuotedForGermanExcel() {
        assertEquals("\"Platz;=1+1\"", escapeCsv("Platz;=1+1"))
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
            vehicleId = 1,
            startDate = LocalDate.of(2026, 7, 10),
            endDate = LocalDate.of(2026, 7, 12),
            destination = "Bodensee, Nordufer",
            tourType = TourType.entries.first(),
            travelDays = 3,
            overnightStays = 2,
            distanceKm = 412,
            costs = listOf(Money(8_950, EUR), Money(145_000, NOK)),
            notes = "Sagte: \"toll\"",
            mapLink = null,
            createdAt = Instant.parse("2026-07-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-14T09:30:00Z"),
        )

        val station = overnightStation(
            tourId = 3,
            date = LocalDate.of(2026, 7, 10),
            pitchAssigned = true,
            electricity = ElectricityBilling.NONE,
            lte = LteQuality.GOOD,
            slope = PitchSlope.LEVEL,
            blocksUsed = false,
        )
        val lines = toursToCsv(listOf(tour), listOf(station), mapOf(1L to "Bulli"), DEFAULT_NAME).split("\r\n")

        assertEquals(3, lines.size)
        assertEquals(CSV_HEADER.joinToString(","), lines[0])
        assertEquals(
            "3,2026-07-10,2026-07-12,\"Bodensee, Nordufer\",${tour.tourType.csvValue},3,2,412,89.50,ja," +
                "${ElectricityFlatRate.NOT_USED.csvValue},${LteQuality.GOOD.csvValue},${PitchSlope.LEVEL.csvValue},nein," +
                "\"Sagte: \"\"toll\"\"\",,2026-07-13T08:00:00Z,2026-07-14T09:30:00Z,\"89.50 EUR; 1450.00 NOK\",Bulli," +
                "\"89.50 EUR; 1450.00 NOK\"",
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
            costs = emptyList(),
            notes = "@Kontakt, bitte",
            mapLink = null,
            createdAt = Instant.parse("2026-07-10T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-10T08:00:00Z"),
        )

        val station = overnightStation(
            tourId = 1,
            date = LocalDate.of(2026, 7, 10),
            pitchAssigned = false,
            electricity = ElectricityBilling.METERED,
            lte = LteQuality.OK,
            slope = PitchSlope.SLOPED,
            blocksUsed = true,
        )
        val row = toursToCsv(listOf(tour), listOf(station), emptyMap(), DEFAULT_NAME).split("\r\n")[1]

        assertEquals(
            "1,2026-07-10,2026-07-10,'=cmd|' /C calc'!A0,${TourType.DAY_TRIP.csvValue},1,0,80,0.00,nein," +
                "${ElectricityFlatRate.NO.csvValue},${LteQuality.OK.csvValue},${PitchSlope.SLOPED.csvValue},ja," +
                "\"'@Kontakt, bitte\",,2026-07-10T08:00:00Z,2026-07-10T08:00:00Z,,$DEFAULT_NAME,",
            row,
        )
    }

    @Test
    fun pitchColumnsAreEmptyWithoutAnOvernightStation() {
        val tour = tour(5, vehicleId = 0)

        val row = toursToCsv(listOf(tour), emptyList(), emptyMap(), DEFAULT_NAME).split("\r\n")[1].split(",")

        val pitchColumns = listOf("stellplatz_zugewiesen", "strompauschale", "lte", "stellplatz_neigung", "keile_genutzt")
        pitchColumns.forEach { column -> assertEquals("", row[CSV_HEADER.indexOf(column)]) }
    }

    @Test
    fun kostenEurHoldsOnlyTheEuroShare() {
        val tour = Tour(
            id = 2,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 4),
            destination = "Lofoten",
            tourType = TourType.VACATION,
            travelDays = 4,
            overnightStays = 3,
            distanceKm = 900,
            costs = listOf(Money(300_000, NOK), Money(350_000, ISK)),
            notes = "",
            mapLink = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val values = toursToCsv(listOf(tour), emptyList(), emptyMap(), DEFAULT_NAME).split("\r\n")[1].split(",")

        assertEquals("0.00", values[CSV_HEADER.indexOf("kosten_eur")])
        assertEquals("\"3000.00 NOK; 350000 ISK\"", values[CSV_HEADER.indexOf("kosten")])
    }

    @Test
    fun totalCostsColumnAddsStationCostsOfTheTourToTheManualTourCosts() {
        val tour = tour(7, vehicleId = 0).copy(costs = listOf(Money(10_000, EUR)))
        val ownStation = overnightStation(
            tourId = 7,
            date = LocalDate.of(2026, 7, 1),
            pitchAssigned = false,
            electricity = ElectricityBilling.NONE,
            lte = LteQuality.GOOD,
            slope = PitchSlope.LEVEL,
            blocksUsed = false,
        ).copy(costs = listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR))))
        val otherTourStation = overnightStation(
            tourId = 99,
            date = LocalDate.of(2026, 7, 1),
            pitchAssigned = false,
            electricity = ElectricityBilling.NONE,
            lte = LteQuality.GOOD,
            slope = PitchSlope.LEVEL,
            blocksUsed = false,
        ).copy(costs = listOf(StationCost(CostCategory.SUPPLY, Money(999, EUR))))

        val row = toursToCsv(listOf(tour), listOf(ownStation, otherTourStation), emptyMap(), DEFAULT_NAME).split("\r\n")[1]

        assertEquals("105.00 EUR", row.split(",")[CSV_HEADER.indexOf("kosten_gesamt")])
    }

    @Test
    fun emptyExportContainsOnlyHeader() {
        assertEquals(CSV_HEADER.joinToString(",") + "\r\n", toursToCsv(emptyList(), emptyList(), emptyMap(), DEFAULT_NAME))
    }

    @Test
    fun germanHeaderMatchesThePinnedLegacyFormat() {
        assertEquals(
            "id,startdatum,enddatum,ziel,tourart,reisetage,uebernachtungen,km,kosten_eur,stellplatz_zugewiesen," +
                "strompauschale,lte,stellplatz_neigung,keile_genutzt,notizen,kartenlink,angelegt,geaendert,kosten,fahrzeug,kosten_gesamt",
            CSV_HEADER.joinToString(","),
        )
        assertEquals(CSV_HEADER, tourCsvHeader(CsvVocabulary.GERMAN))
    }

    @Test
    fun englishHeaderAndRowUseEnglishVocabulary() {
        assertEquals(
            "id,start_date,end_date,destination,trip_type,travel_days,overnight_stays,distance_km,costs_eur,pitch_assigned," +
                "electricity_flat_rate,lte_quality,pitch_slope,leveling_blocks_used,notes,map_link,created_at,updated_at,costs,vehicle,total_costs",
            tourCsvHeader(CsvVocabulary.ENGLISH).joinToString(","),
        )

        val tour = Tour(
            id = 3,
            vehicleId = 1,
            startDate = LocalDate.of(2026, 7, 10),
            endDate = LocalDate.of(2026, 7, 12),
            destination = "Lake Garda",
            tourType = TourType.DAY_TRIP,
            travelDays = 3,
            overnightStays = 2,
            distanceKm = 412,
            costs = listOf(Money(8_950, EUR)),
            notes = "",
            mapLink = null,
            createdAt = Instant.parse("2026-07-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-14T09:30:00Z"),
        )
        val station = overnightStation(
            tourId = 3,
            date = LocalDate.of(2026, 7, 10),
            pitchAssigned = true,
            electricity = ElectricityBilling.NONE,
            lte = LteQuality.GOOD,
            slope = PitchSlope.LEVEL,
            blocksUsed = false,
        )

        val row = toursToCsv(listOf(tour), listOf(station), mapOf(1L to "Bulli"), DEFAULT_NAME, CsvVocabulary.ENGLISH).split("\r\n")[1]

        assertEquals(
            "3,2026-07-10,2026-07-12,Lake Garda,day trip,3,2,412,89.50,yes,not used,good,level,no," +
                ",,2026-07-13T08:00:00Z,2026-07-14T09:30:00Z,89.50 EUR,Bulli,89.50 EUR",
            row,
        )
    }

    @Test
    fun totalCostsColumnIsAppendedAfterTheVehicleColumn() {
        assertEquals("fahrzeug", CSV_HEADER[CSV_HEADER.size - 2])
        assertEquals("kosten_gesamt", CSV_HEADER.last())
    }

    @Test
    fun vehicleColumnUsesMappedNameOrDefaultWhenBlankOrMissing() {
        val named = tour(1, vehicleId = 10)
        val blankName = tour(2, vehicleId = 20)
        val unmapped = tour(3, vehicleId = 99)
        val vehicleIndex = CSV_HEADER.indexOf("fahrzeug")

        val rows = toursToCsv(listOf(named, blankName, unmapped), emptyList(), mapOf(10L to "Bulli", 20L to ""), DEFAULT_NAME).split("\r\n")

        assertEquals("Bulli", rows[1].split(",")[vehicleIndex])
        assertEquals(DEFAULT_NAME, rows[2].split(",")[vehicleIndex])
        assertEquals(DEFAULT_NAME, rows[3].split(",")[vehicleIndex])
    }

    @Test
    fun vehicleColumnIsProtectedAgainstFormulaInjection() {
        val row = toursToCsv(listOf(tour(1, vehicleId = 1)), emptyList(), mapOf(1L to "=cmd|' /C calc'!A0"), DEFAULT_NAME).split("\r\n")[1]

        assertEquals("'=cmd|' /C calc'!A0", row.split(",")[CSV_HEADER.indexOf("fahrzeug")])
    }

    private fun tour(id: Long, vehicleId: Long) = Tour(
        id = id,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 1),
        destination = "Ziel",
        tourType = TourType.DAY_TRIP,
        travelDays = 1,
        overnightStays = 0,
        distanceKm = 1,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun overnightStation(
        tourId: Long,
        date: LocalDate,
        pitchAssigned: Boolean,
        electricity: ElectricityBilling,
        lte: LteQuality,
        slope: PitchSlope,
        blocksUsed: Boolean,
    ) = Station(
        vehicleId = 0,
        tourId = tourId,
        type = StationType.OVERNIGHT,
        date = date,
        pitchAssigned = pitchAssigned,
        electricityBilling = electricity,
        lteQuality = lte,
        pitchSlope = slope,
        levelingBlocksUsed = blocksUsed,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

private val NOK: Currency = Currency.getInstance("NOK")
private val ISK: Currency = Currency.getInstance("ISK")
