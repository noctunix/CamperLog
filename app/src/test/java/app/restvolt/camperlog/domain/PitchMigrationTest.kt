package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PitchMigrationTest {

    private val createdAt = Instant.parse("2026-07-15T08:00:00Z")
    private val updatedAt = Instant.parse("2026-07-16T09:00:00Z")

    @Test
    fun overnightStaysProduceOneStationWithCopiedValues() {
        val pitch = LegacyPitchFields(true, ElectricityFlatRate.YES, LteQuality.BAD, PitchSlope.SLOPED, true)
        val result = migrateLegacyPitch(
            uuid = "station-uuid",
            vehicleId = 5,
            tourId = 9,
            startDate = LocalDate.of(2026, 7, 4),
            destination = "Lofoten",
            overnightStays = 13,
            pitch = pitch,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        val station = checkNotNull(result.overnightStation)
        assertEquals("station-uuid", station.uuid)
        assertEquals(5L, station.vehicleId)
        assertEquals(9L, station.tourId)
        assertEquals(StationType.OVERNIGHT, station.type)
        assertEquals(LocalDate.of(2026, 7, 4), station.date)
        assertNull(station.time)
        assertEquals("Lofoten", station.name)
        assertEquals("", station.place)
        assertEquals(13, station.nights)
        assertNull(station.siteKind)
        assertEquals(true, station.pitchAssigned)
        assertEquals(ElectricityBilling.FLAT_PER_STAY, station.electricityBilling)
        assertEquals(LteQuality.BAD, station.lteQuality)
        assertEquals(PitchSlope.SLOPED, station.pitchSlope)
        assertEquals(true, station.levelingBlocksUsed)
        assertEquals(emptySet<StationService>(), station.services)
        assertEquals(createdAt, station.createdAt)
        assertEquals(updatedAt, station.updatedAt)
        assertEquals(emptyList<LegacyPitchAttribute>(), result.dayTripAttributes)
    }

    @Test
    fun dayTripWithDefaultValuesProducesNoStationAndNoAttributes() {
        val result = migrateLegacyPitch(
            uuid = "station-uuid",
            vehicleId = 1,
            tourId = 2,
            startDate = LocalDate.of(2026, 7, 4),
            destination = "Ostsee",
            overnightStays = 0,
            pitch = DEFAULT_LEGACY_PITCH,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        assertNull(result.overnightStation)
        assertEquals(emptyList<LegacyPitchAttribute>(), result.dayTripAttributes)
    }

    @Test
    fun dayTripWithNonDefaultValuesReportsEveryDifferingAttribute() {
        val pitch = DEFAULT_LEGACY_PITCH.copy(pitchSlope = PitchSlope.SLOPED, levelingBlocksUsed = true)
        val result = migrateLegacyPitch(
            uuid = "station-uuid",
            vehicleId = 1,
            tourId = 2,
            startDate = LocalDate.of(2026, 7, 4),
            destination = "Ostsee",
            overnightStays = 0,
            pitch = pitch,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        assertNull(result.overnightStation)
        assertEquals(listOf(LegacyPitchAttribute.PITCH_SLOPE, LegacyPitchAttribute.LEVELING_BLOCKS), result.dayTripAttributes)
    }

    @Test
    fun dayTripWithEveryAttributeDiffering() {
        val pitch = LegacyPitchFields(true, ElectricityFlatRate.NO, LteQuality.OK, PitchSlope.SLOPED, true)
        val result = migrateLegacyPitch(
            uuid = "station-uuid",
            vehicleId = 1,
            tourId = null,
            startDate = LocalDate.of(2026, 7, 4),
            destination = "Ostsee",
            overnightStays = 0,
            pitch = pitch,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        assertEquals(
            listOf(
                LegacyPitchAttribute.PITCH_ASSIGNED,
                LegacyPitchAttribute.ELECTRICITY,
                LegacyPitchAttribute.LTE,
                LegacyPitchAttribute.PITCH_SLOPE,
                LegacyPitchAttribute.LEVELING_BLOCKS,
            ),
            result.dayTripAttributes,
        )
    }
}
