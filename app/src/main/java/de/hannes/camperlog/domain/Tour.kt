package de.hannes.camperlog.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Eine Wohnmobil-Tour. Geldbeträge sind in Cent gespeichert.
 * Eine Tour mit [id] 0 ist noch nicht gespeichert.
 */
data class Tour(
    val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val destination: String,
    val tourType: TourType,
    val travelDays: Int,
    val overnightStays: Int,
    val distanceKm: Int,
    val costCents: Long,
    val pitchAssigned: Boolean,
    val electricityFlatRate: ElectricityFlatRate,
    val lteQuality: LteQuality,
    val pitchSlope: PitchSlope,
    val levelingBlocksUsed: Boolean,
    val notes: String,
    val mapLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Jahr der Tour, bestimmt durch das Startdatum. */
    val year: Int get() = startDate.year
}

/** Art der Tour mit deutscher Bezeichnung [label]. */
enum class TourType(val label: String) {
    DAY_TRIP("Tagestour"),
    WEEKEND("Wochenende"),
    VACATION("Urlaub"),
}

/** Ob eine Strompauschale vor Ort galt, mit deutscher Bezeichnung [label]. */
enum class ElectricityFlatRate(val label: String) {
    YES("ja"),
    NO("nein"),
    NOT_USED("nicht genutzt"),
}

/** Qualität des Mobilfunknetzes am Platz, mit deutscher Bezeichnung [label]. */
enum class LteQuality(val label: String) {
    GOOD("gut"),
    OK("geht so"),
    BAD("schlecht"),
}

/** Neigung des Standplatzes, mit deutscher Bezeichnung [label]. */
enum class PitchSlope(val label: String) {
    LEVEL("gerade"),
    SLOPED("abschüssig"),
}

/** Aufsummierte Kennzahlen über eine Menge von Touren. */
data class TourTotals(
    val tours: Int,
    val distanceKm: Long,
    val travelDays: Long,
    val overnightStays: Long,
    val costCents: Long,
)

/** Kennzahlen [totals] aller Touren, die im Jahr [year] beginnen. */
data class YearTotals(val year: Int, val totals: TourTotals)
