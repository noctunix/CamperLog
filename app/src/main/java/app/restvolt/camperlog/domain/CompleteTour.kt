package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Beendet die aktuell noch laufende Tour und schreibt ihre aus Zeitraum, Stationen und Track
 * abgeleiteten Kennzahlen fest. `false` bedeutet, dass Tour oder Datum inzwischen nicht mehr passen.
 */
suspend fun completeTour(
    tourId: Long,
    endDate: LocalDate,
    today: LocalDate,
    tours: TourRepository,
    stations: StationRepository,
    tracks: TrackRepository,
): Boolean {
    val tour = tours.observeTour(tourId).first() ?: return false
    if (tour.endDate != null || endDate < tour.startDate || endDate > today) return false
    val completed = tour.copy(endDate = endDate)
    val metrics = completed.derivedMetrics(
        stations = stations.observeForTour(tourId).first(),
        trackPoints = tracks.observeForTour(tourId).first(),
        today = endDate,
    )
    tours.save(
        completed.copy(
            travelDays = metrics.travelDays,
            overnightStays = metrics.overnightStays,
            distanceKm = metrics.distanceKm,
        ),
    )
    return true
}
