package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private const val DEFAULT_NAME = "Mein Wohnmobil"

class StationCsvTest {

    @Test
    fun emptyExportContainsOnlyHeader() {
        assertEquals(STATION_CSV_HEADER.joinToString(",") + "\r\n", stationsToCsv(emptyList(), emptyMap(), emptyMap(), DEFAULT_NAME))
    }

    @Test
    fun costAndKwhColumnsAreAppendedAfterTheVehicleColumn() {
        assertEquals(24, STATION_CSV_HEADER.indexOf("fahrzeug"))
        assertEquals("link", STATION_CSV_HEADER.last())
        assertEquals("strom_kwh", STATION_CSV_HEADER[STATION_CSV_HEADER.indexOf("link") - 4])
    }

    @Test
    fun germanHeaderMatchesThePinnedLegacyFormat() {
        assertEquals(
            "id,datum,uhrzeit,typ,name,ort,breitengrad,laengengrad,koordinatenquelle,genauigkeit_m,kartenlink,naechte," +
                "platzart,stellplatz_zugewiesen,strompauschale,lte,neigung,keile_genutzt,versorgung,gerne_wieder,notizen," +
                "angelegt,geaendert,tour,fahrzeug,kosten_stellplatz,kosten_strom,kosten_ver_entsorgung,kosten_tanken_laden," +
                "kosten_maut,kosten_faehre,kosten_essen,kosten_sonstiges,strom_kwh,bewertung,kilometerstand_km,temperatur_c,link",
            STATION_CSV_HEADER.joinToString(","),
        )
        assertEquals(STATION_CSV_HEADER, stationCsvHeader(CsvVocabulary.GERMAN))
    }

    @Test
    fun englishHeaderAndRowUseEnglishVocabulary() {
        assertEquals(
            "id,date,time,type,name,place,latitude,longitude,coordinate_source,accuracy_m,map_link,nights,site_kind," +
                "pitch_assigned,electricity_billing,lte_quality,pitch_slope,leveling_blocks_used,services,favorite,notes," +
                "created_at,updated_at,tour,vehicle,costs_pitch,costs_electricity,costs_supply_disposal,costs_fuel_charging," +
                "costs_toll,costs_ferry,costs_food,costs_other,electricity_kwh,rating,odometer_km,temperature_c,link",
            stationCsvHeader(CsvVocabulary.ENGLISH).joinToString(","),
        )

        val station = Station(
            id = 7,
            vehicleId = 1,
            tourId = 3,
            type = StationType.OVERNIGHT,
            date = LocalDate.of(2026, 7, 4),
            time = LocalTime.of(18, 40),
            name = "Camping Moskenes",
            place = "Moskenes, Norway",
            latitude = 68.0912,
            longitude = 13.1023,
            coordinateSource = CoordinateSource.ENTERED,
            accuracyM = null,
            mapLink = null,
            notes = "Nice view",
            nights = 2,
            siteKind = SiteKind.CAMPSITE,
            pitchAssigned = true,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = false,
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.35"),
            electricityMeterStart = BigDecimal("100"),
            electricityMeterEnd = BigDecimal("110"),
            costs = listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR))),
            services = setOf(StationService.CASSETTE, StationService.FRESH_WATER),
            favorite = true,
            createdAt = Instant.parse("2026-07-04T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-05T09:30:00Z"),
        )

        val row = stationsToCsv(listOf(station), mapOf(3L to "Lofoten"), mapOf(1L to "Bulli"), DEFAULT_NAME, CsvVocabulary.ENGLISH).split("\r\n")[1]

        assertEquals(
            "7,2026-07-04,18:40,overnight,Camping Moskenes,\"Moskenes, Norway\"," +
                "68.0912,13.1023,entered,,,2,campsite,yes," +
                "metered,good,level,no," +
                "\"fresh_water; cassette_toilet\",yes,Nice view," +
                "2026-07-04T08:00:00Z,2026-07-05T09:30:00Z,Lofoten,Bulli,,3.50 EUR,5.00 EUR,,,,,,10,,,,",
            row,
        )
    }

    @Test
    fun exportHasHeaderAndOneCrlfTerminatedRowPerStation() {
        val station = Station(
            id = 7,
            vehicleId = 1,
            tourId = 3,
            type = StationType.OVERNIGHT,
            date = LocalDate.of(2026, 7, 4),
            time = LocalTime.of(18, 40),
            name = "Camping Moskenes",
            place = "Moskenes, Norwegen",
            latitude = 68.0912,
            longitude = 13.1023,
            coordinateSource = CoordinateSource.ENTERED,
            accuracyM = null,
            mapLink = null,
            notes = "Schöner Blick",
            nights = 2,
            siteKind = SiteKind.CAMPSITE,
            pitchAssigned = true,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = false,
            electricityBilling = ElectricityBilling.METERED,
            electricityCurrency = EUR,
            electricityPricePerKwh = BigDecimal("0.35"),
            electricityMeterStart = BigDecimal("100"),
            electricityMeterEnd = BigDecimal("110"),
            costs = listOf(StationCost(CostCategory.SUPPLY, Money(500, EUR))),
            services = setOf(StationService.CASSETTE, StationService.FRESH_WATER),
            favorite = true,
            createdAt = Instant.parse("2026-07-04T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-05T09:30:00Z"),
        )

        val lines = stationsToCsv(listOf(station), mapOf(3L to "Lofoten"), mapOf(1L to "Bulli"), DEFAULT_NAME).split("\r\n")

        assertEquals(3, lines.size)
        assertEquals(STATION_CSV_HEADER.joinToString(","), lines[0])
        assertEquals(
            "7,2026-07-04,18:40,${StationType.OVERNIGHT.csvValue},Camping Moskenes,\"Moskenes, Norwegen\"," +
                "68.0912,13.1023,${CoordinateSource.ENTERED.csvValue},,,2,${SiteKind.CAMPSITE.csvValue},ja," +
                "${ElectricityBilling.METERED.csvValue},${LteQuality.GOOD.csvValue},${PitchSlope.LEVEL.csvValue},nein," +
                "\"${StationService.FRESH_WATER.csvValue}; ${StationService.CASSETTE.csvValue}\",ja,Schöner Blick," +
                "2026-07-04T08:00:00Z,2026-07-05T09:30:00Z,Lofoten,Bulli,,3.50 EUR,5.00 EUR,,,,,,10,,,,",
            lines[1],
        )
        assertEquals("", lines[2])
    }

    @Test
    fun stationWithoutTourHasAnEmptyTourColumnAndDefaultVehicleName() {
        val station = minimalStation()

        val row = stationsToCsv(listOf(station), emptyMap(), emptyMap(), DEFAULT_NAME).split("\r\n")[1].split(",")

        assertEquals("", row[STATION_CSV_HEADER.indexOf("tour")])
        assertEquals(DEFAULT_NAME, row[STATION_CSV_HEADER.indexOf("fahrzeug")])
    }

    @Test
    fun freeTextFieldsAreProtectedAgainstFormulaInjection() {
        val station = minimalStation().copy(
            name = "=cmd|' /C calc'!A0",
            place = "+49 Platz",
            notes = "@Kontakt bitte",
        )

        val row = stationsToCsv(listOf(station), emptyMap(), emptyMap(), DEFAULT_NAME).split("\r\n")[1]
        val fields = row.split(",")

        assertEquals("'=cmd|' /C calc'!A0", fields[STATION_CSV_HEADER.indexOf("name")])
        assertEquals("'+49 Platz", fields[STATION_CSV_HEADER.indexOf("ort")])
        assertEquals("'@Kontakt bitte", fields[STATION_CSV_HEADER.indexOf("notizen")])
    }

    @Test
    fun tourAndVehicleNameAreProtectedAgainstFormulaInjection() {
        val station = minimalStation(tourId = 1, vehicleId = 1)

        val row = stationsToCsv(listOf(station), mapOf(1L to "=HYPERLINK(\"x\")"), mapOf(1L to "-2+3"), DEFAULT_NAME).split("\r\n")[1]

        assertEquals("'-2+3", row.split(",")[STATION_CSV_HEADER.indexOf("fahrzeug")])
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", row.split(",")[STATION_CSV_HEADER.indexOf("tour")])
    }

    @Test
    fun ratingOdometerTemperatureAndLinkAreExportedInTheirOwnColumns() {
        val station = minimalStation().copy(rating = 4, odometerKm = 54_000, manualTemperatureDeciC = 183, link = "https://example.org/platz")

        val row = stationsToCsv(listOf(station), emptyMap(), emptyMap(), DEFAULT_NAME).split("\r\n")[1].split(",")

        assertEquals("4", row[STATION_CSV_HEADER.indexOf("bewertung")])
        assertEquals("54000", row[STATION_CSV_HEADER.indexOf("kilometerstand_km")])
        assertEquals("18", row[STATION_CSV_HEADER.indexOf("temperatur_c")])
        assertEquals("https://example.org/platz", row[STATION_CSV_HEADER.indexOf("link")])
    }

    private fun minimalStation(tourId: Long? = null, vehicleId: Long = 0) = Station(
        vehicleId = vehicleId,
        tourId = tourId,
        type = StationType.SIGHT,
        date = LocalDate.of(2026, 6, 26),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
