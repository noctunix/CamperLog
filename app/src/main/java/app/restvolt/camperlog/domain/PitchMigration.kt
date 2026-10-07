package app.restvolt.camperlog.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Die alten Stellplatz-Felder einer Tour vor Room-Schema 7 bzw. vor Sicherungsformat 3, die seither
 * auf der Übernachtungs-Station liegen. Beim Sicherungs-Import ist jedes Feld einzeln `null`, wenn es
 * in der alten Datei fehlt oder einen unbekannten Wert trägt, statt die ganze Tour zu verwerfen; die
 * Room-Migration liefert dagegen immer alle fünf Werte.
 */
data class LegacyPitchFields(
    val pitchAssigned: Boolean?,
    val electricityFlatRate: ElectricityFlatRate?,
    val lteQuality: LteQuality?,
    val pitchSlope: PitchSlope?,
    val levelingBlocksUsed: Boolean?,
)

/** Die alten Formular-Vorgaben, gegen die ein Tagestrip auf "unverändert" geprüft wird. */
val DEFAULT_LEGACY_PITCH = LegacyPitchFields(
    pitchAssigned = false,
    electricityFlatRate = ElectricityFlatRate.NOT_USED,
    lteQuality = LteQuality.GOOD,
    pitchSlope = PitchSlope.LEVEL,
    levelingBlocksUsed = false,
)

/** Ein von den alten Vorgaben abweichendes Stellplatz-Attribut eines Tagestrips ohne Übernachtung. */
enum class LegacyPitchAttribute { PITCH_ASSIGNED, ELECTRICITY, LTE, PITCH_SLOPE, LEVELING_BLOCKS }

/**
 * Ergebnis der Umwandlung alter Stellplatz-Felder: [overnightStation] bei mindestens einer
 * Übernachtung, sonst `null` und stattdessen die von den alten Vorgaben abweichenden
 * [dayTripAttributes] eines Tagestrips (zur Anzeige als Notiz-Zeile durch den Aufrufer).
 */
data class LegacyPitchMigration(
    val overnightStation: Station?,
    val dayTripAttributes: List<LegacyPitchAttribute>,
)

/**
 * Baut aus den alten Stellplatz-Feldern einer Tour exakt die Station, die Room-Migration 6→7 und der
 * Sicherungs-Import für ältere Formate gleichermaßen anlegen. Bei
 * [overnightStays] `0` entsteht keine Station; stattdessen nennt [LegacyPitchMigration.dayTripAttributes],
 * welche Werte von den alten Vorgaben abweichen, damit der Aufrufer daraus eine lokalisierte
 * Notiz-Zeile bauen kann.
 */
fun migrateLegacyPitch(
    uuid: String,
    vehicleId: Long,
    tourId: Long?,
    startDate: LocalDate,
    destination: String,
    overnightStays: Int,
    pitch: LegacyPitchFields,
    createdAt: Instant,
    updatedAt: Instant,
): LegacyPitchMigration {
    if (overnightStays > 0) {
        return LegacyPitchMigration(
            overnightStation = Station(
                uuid = uuid,
                vehicleId = vehicleId,
                tourId = tourId,
                type = StationType.OVERNIGHT,
                date = startDate,
                time = null,
                name = destination,
                place = "",
                notes = "",
                nights = overnightStays,
                siteKind = null,
                pitchAssigned = pitch.pitchAssigned,
                electricityBilling = migrateLegacyElectricityFlatRate(pitch.electricityFlatRate),
                lteQuality = pitch.lteQuality,
                pitchSlope = pitch.pitchSlope,
                levelingBlocksUsed = pitch.levelingBlocksUsed,
                services = emptySet(),
                weather = null,
                favorite = false,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            dayTripAttributes = emptyList(),
        )
    }
    // Ein fehlendes Feld (null) gilt als unbekannt, nicht als Abweichung, und bleibt ungemeldet.
    val attributes = buildList {
        if (pitch.pitchAssigned != null && pitch.pitchAssigned != DEFAULT_LEGACY_PITCH.pitchAssigned) add(LegacyPitchAttribute.PITCH_ASSIGNED)
        if (pitch.electricityFlatRate != null && pitch.electricityFlatRate != DEFAULT_LEGACY_PITCH.electricityFlatRate) add(LegacyPitchAttribute.ELECTRICITY)
        if (pitch.lteQuality != null && pitch.lteQuality != DEFAULT_LEGACY_PITCH.lteQuality) add(LegacyPitchAttribute.LTE)
        if (pitch.pitchSlope != null && pitch.pitchSlope != DEFAULT_LEGACY_PITCH.pitchSlope) add(LegacyPitchAttribute.PITCH_SLOPE)
        if (pitch.levelingBlocksUsed != null && pitch.levelingBlocksUsed != DEFAULT_LEGACY_PITCH.levelingBlocksUsed) add(LegacyPitchAttribute.LEVELING_BLOCKS)
    }
    return LegacyPitchMigration(overnightStation = null, dayTripAttributes = attributes)
}
