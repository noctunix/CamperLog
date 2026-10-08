package app.restvolt.camperlog.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Automatisch aus Zeitraum, Übernachtungsstationen und GPS-Track berechnete Tour-Kennzahlen. */
data class TourMetrics(
    val travelDays: Int,
    val overnightStays: Int,
    val distanceKm: Int,
)

/** Berechnet Kennzahlen unabhängig von den gespeicherten, gegebenenfalls manuell angepassten Werten. */
fun Tour.derivedMetrics(
    stations: List<Station>,
    trackPoints: List<TrackPoint>,
    today: LocalDate,
): TourMetrics {
    val periodEnd = endDate ?: today
    val days = if (periodEnd.isBefore(startDate)) 1 else travelDaysBetween(startDate, periodEnd)
    val nights = stations.asSequence()
        .filter { it.tourId == id && it.type == StationType.OVERNIGHT }
        .sumOf { it.nights ?: 1 }
    val meters = trackLengthMeters(trackPoints.filter { it.tourId == id })

    return TourMetrics(
        travelDays = days.coerceAtLeast(1).toInt(),
        overnightStays = nights,
        distanceKm = (meters / 1_000.0).roundToInt(),
    )
}
