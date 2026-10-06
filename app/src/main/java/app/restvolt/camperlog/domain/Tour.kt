package app.restvolt.camperlog.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Eine Wohnmobil-Tour. [costs] enthält höchstens einen Betrag je Währung.
 * Eine Tour mit [id] 0 ist noch nicht gespeichert. [uuid] identifiziert die Tour geräteübergreifend
 * (z. B. beim Import); leer bedeutet „noch nicht vergeben“, das Repository vergibt sie beim Anlegen.
 */
data class Tour(
    val id: Long = 0,
    val uuid: String = "",
    /** 0 bedeutet beim Speichern „aktuelles Fahrzeug" (das Repository löst das auf). */
    val vehicleId: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val destination: String,
    val tourType: TourType,
    val travelDays: Int,
    val overnightStays: Int,
    val distanceKm: Int,
    val costs: List<Money>,
    val notes: String,
    val mapLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Jahr der Tour, bestimmt durch das Startdatum. */
    val year: Int get() = startDate.year
}

/** Art der Tour mit stabilem Exportwert [csvValue]. */
enum class TourType(val csvValue: String) {
    DAY_TRIP("Tagestour"),
    WEEKEND("Wochenende"),
    VACATION("Urlaub"),
}

/** Ob eine Strompauschale vor Ort galt, mit stabilem Exportwert [csvValue]. */
enum class ElectricityFlatRate(val csvValue: String) {
    YES("ja"),
    NO("nein"),
    NOT_USED("nicht genutzt"),
}

/** Qualität des Mobilfunknetzes am Platz, mit stabilem Exportwert [csvValue]. */
enum class LteQuality(val csvValue: String) {
    GOOD("gut"),
    OK("geht so"),
    BAD("schlecht"),
}

/** Neigung des Standplatzes, mit stabilem Exportwert [csvValue]. */
enum class PitchSlope(val csvValue: String) {
    LEVEL("gerade"),
    SLOPED("abschüssig"),
}

/**
 * Aufsummierte Kennzahlen über eine Menge von Touren; [costs] je Währung, sortiert nach Code.
 * [categoryCosts] ist die Aufschlüsselung der enthaltenen Stationskosten nach Kategorie, für eine
 * aufklappbare Anzeige; manuelle Tourkosten haben keine Kategorie und stecken nur in [costs].
 */
data class TourTotals(
    val tours: Int,
    val distanceKm: Long,
    val travelDays: Long,
    val overnightStays: Long,
    val costs: List<Money>,
    val categoryCosts: Map<CostCategory, List<Money>> = emptyMap(),
)

/** Kennzahlen [totals] aller Touren, die im Jahr [year] beginnen. */
data class YearTotals(val year: Int, val totals: TourTotals)
