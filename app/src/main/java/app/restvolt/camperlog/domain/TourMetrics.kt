package app.restvolt.camperlog.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Automatisch aus Zeitraum, Übernachtungsstationen und GPS-Track berechnete Tour-Kennzahlen. */
data class TourMetrics(
    val travelDays: Int,
    val overnightStays: Int,
    val distanceKm: Int,
    /** Ob [distanceKm] mangels Track aus den Stationskoordinaten geschätzt ist statt gemessen. */
    val distanceIsEstimated: Boolean = false,
)

/** Fester Umrechnungsfaktor Luftlinie→Straße für eine grobe Entfernungsschätzung ohne echtes Routing (Ballou/Rahardja/Sakai 2002). */
const val ROUTE_CIRCUITY_FACTOR = 1.3

/**
 * Grobe Streckenschätzung aus der Luftlinie zwischen den chronologisch sortierten, verorteten
 * Stationen (siehe [stationsForMap]), hochgerechnet mit [ROUTE_CIRCUITY_FACTOR]. Mit weniger als
 * zwei verorteten Stationen lässt sich nichts schätzen, dann 0.
 */
fun estimatedRouteDistanceMeters(stations: List<Station>): Double {
    val located = stationsForMap(stations).mapNotNull { it.toLatLon() }
    if (located.size < 2) return 0.0
    val airlineMeters = located.zipWithNext { a, b ->
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }.sum()
    return airlineMeters * ROUTE_CIRCUITY_FACTOR
}

/** Berechnet Kennzahlen unabhängig von den gespeicherten, gegebenenfalls manuell angepassten Werten. */
fun Tour.derivedMetrics(
    stations: List<Station>,
    trackPoints: List<TrackPoint>,
    today: LocalDate,
): TourMetrics {
    val periodEnd = endDate ?: today
    val days = if (periodEnd.isBefore(startDate)) 1 else travelDaysBetween(startDate, periodEnd)
    val tourStations = stations.filter { it.tourId == id }
    val nights = tourStations.asSequence()
        .filter { it.type == StationType.OVERNIGHT }
        .sumOf { it.nights ?: 1 }
    val trackMeters = trackLengthMeters(trackPoints.filter { it.tourId == id })
    val estimatedMeters = if (trackMeters == 0.0) estimatedRouteDistanceMeters(tourStations) else 0.0
    val isEstimated = estimatedMeters > 0.0
    val meters = if (isEstimated) estimatedMeters else trackMeters

    return TourMetrics(
        travelDays = days.coerceAtLeast(1).toInt(),
        overnightStays = nights,
        distanceKm = (meters / 1_000.0).roundToInt(),
        distanceIsEstimated = isEstimated,
    )
}
