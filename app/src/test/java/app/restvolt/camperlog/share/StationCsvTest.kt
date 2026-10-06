package app.restvolt.camperlog.share

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import org.junit.Assert.assertEquals
import org.junit.Test
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
    fun headerEndsWithVehicleColumn() {
        assertEquals("fahrzeug", STATION_CSV_HEADER.last())
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
            electricityFlatRate = ElectricityFlatRate.YES,
            lteQuality = LteQuality.GOOD,
            pitchSlope = PitchSlope.LEVEL,
            levelingBlocksUsed = false,
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
                "${ElectricityFlatRate.YES.csvValue},${LteQuality.GOOD.csvValue},${PitchSlope.LEVEL.csvValue},nein," +
                "\"${StationService.FRESH_WATER.csvValue}; ${StationService.CASSETTE.csvValue}\",ja,Schöner Blick," +
                "2026-07-04T08:00:00Z,2026-07-05T09:30:00Z,Lofoten,Bulli",
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

        assertEquals("'-2+3", row.substringAfterLast(","))
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", row.split(",")[STATION_CSV_HEADER.indexOf("tour")])
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
