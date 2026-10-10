package app.restvolt.camperlog.domain.guide

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.LatLon
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Anzeigetexte der Demo-Tour; aus `strings.xml` gelesen und ohne Android-Abhängigkeit hier hinein gereicht. */
data class DemoTourContent(
    val vehicleName: String = "Sample camper",
    val destination: String = "Alpine loop",
    val stationNames: List<String> = listOf("Lakeside pitch", "Mountain pass stop", "Forest campsite"),
)

/**
 * Hält die simulierte Demo-Tour des Tutorials bereit: eine bereits beendete Tour mit vorgefertigten
 * Trackpunkten auf einem eigenen, als Demo markierten Fahrzeug ([Vehicle.isDemo]), unabhängig von
 * den echten Fahrzeugen und Touren des Nutzers. Demo-Stationen bekommen keine der
 * [app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES]-Dienste (die würden Bordbuch-Einträge
 * erzeugen), keinen Kilometerstand und keine Anhänge.
 *
 * [stopIfTracking] ist [app.restvolt.camperlog.tracking.TrackRecordingService.stopIfTracking], von
 * außen injiziert, damit diese Klasse Android-frei bleibt; da die Demo-Tour nie wirklich aufgezeichnet
 * wird (die Trackpunkte sind vorgefertigt), greift er praktisch nie - er bleibt als Sicherheitsnetz,
 * falls doch einmal eine echte Aufzeichnung auf die Demo-Tour zeigen sollte.
 *
 * Alle drei Einstiegspunkte sind mutex-gesichert und laufen seriell: [begin] räumt zuerst über
 * [sweepOrphans] auf, bevor die neue Demo-Tour entsteht; [end] und [sweepOrphans] stoppen eine
 * eventuell laufende Aufzeichnung immer, bevor sie löschen (umgekehrte Reihenfolge zum normalen
 * Nutzer-Löschen, das wegen der Rückgängig-Snackbar zuerst löscht).
 */
class DemoTourSession(
    private val tours: TourRepository,
    private val stations: StationRepository,
    private val tracks: TrackRepository,
    private val vehicles: VehicleRepository,
    private val content: DemoTourContent = DemoTourContent(),
    private val clock: () -> Instant = Instant::now,
    private val stopIfTracking: (Long) -> Unit,
) {
    private val mutex = Mutex()

    @Volatile
    var active: Boolean = false
        private set

    /**
     * Räumt Reste eines abgebrochenen vorherigen Durchlaufs auf ([sweepOrphans]) und legt dann eine
     * neue, bereits beendete Demo-Tour mit eigenem Demo-Fahrzeug, drei Stationen und vorgefertigten
     * Trackpunkten an.
     *
     * @return die id der angelegten Demo-Tour
     */
    suspend fun begin(): Long = mutex.withLock {
        sweepOrphansLocked()
        val now = clock()
        val vehicleId = vehicles.save(demoVehicle(now))
        val start = now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(DEMO_TOUR_LENGTH_DAYS)
        val tourId = tours.save(demoTour(vehicleId, start, now))
        demoStations(tourId, vehicleId, start, now).forEach { stations.save(it) }
        tracks.addAll(demoTrackPoints(tourId, start))
        active = true
        tourId
    }

    /** Beendet die laufende Demo-Sitzung und entfernt alle Demo-Daten sofort. */
    suspend fun end() = mutex.withLock {
        sweepOrphansLocked()
        active = false
    }

    /**
     * Entfernt verwaiste Demo-Daten eines abgebrochenen oder nie beendeten Tutorials, unabhängig vom
     * [active]-Flag: wird bedingungslos bei jedem Kaltstart aufgerufen, einmal pro Prozess-Leben.
     */
    suspend fun sweepOrphans() = mutex.withLock { sweepOrphansLocked() }

    private suspend fun sweepOrphansLocked() {
        val demoTourIds = tours.demoTourIds()
        demoTourIds.forEach(stopIfTracking)
        demoTourIds.forEach { tours.delete(it) }
        vehicles.deleteDemoVehicles()
    }

    private fun demoVehicle(now: Instant) = Vehicle(name = content.vehicleName, createdAt = now, updatedAt = now, isDemo = true)

    private fun demoTour(vehicleId: Long, start: LocalDate, now: Instant): Tour {
        val end = start.plusDays(DEMO_TOUR_LENGTH_DAYS)
        return Tour(
            vehicleId = vehicleId,
            startDate = start,
            endDate = end,
            destination = content.destination,
            tourType = TourType.VACATION,
            travelDays = (DEMO_TOUR_LENGTH_DAYS + 1).toInt(),
            overnightStays = DEMO_TOUR_LENGTH_DAYS.toInt(),
            distanceKm = 180,
            costs = emptyList(),
            notes = "",
            mapLink = null,
            createdAt = now,
            updatedAt = now,
            isDemo = true,
        )
    }

    private fun demoStations(tourId: Long, vehicleId: Long, start: LocalDate, now: Instant): List<Station> {
        return DEMO_ROUTE.mapIndexed { index, point ->
            Station(
                vehicleId = vehicleId,
                tourId = tourId,
                type = StationType.OVERNIGHT,
                date = start.plusDays(index.toLong()),
                name = content.stationNames.getOrElse(index) { content.destination },
                latitude = point.latitude,
                longitude = point.longitude,
                coordinateSource = CoordinateSource.ENTERED,
                nights = 1,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    /** Zwei Segmente entlang der drei Stationen, je mit ein paar interpolierten Punkten. */
    private fun demoTrackPoints(tourId: Long, start: LocalDate): List<TrackPoint> {
        val trackStart = start.atStartOfDay(ZoneOffset.UTC).toInstant()
        return DEMO_ROUTE.zipWithNext().flatMapIndexed { segmentIndex, (from, to) ->
            val segmentStart = trackStart.plus((segmentIndex * DEMO_TOUR_LENGTH_DAYS), ChronoUnit.DAYS)
            interpolatedPoints(tourId, segment = segmentIndex + 1, from = from, to = to, startTime = segmentStart)
        }
    }

    private fun interpolatedPoints(tourId: Long, segment: Int, from: LatLon, to: LatLon, startTime: Instant): List<TrackPoint> =
        (0 until DEMO_POINTS_PER_SEGMENT).map { step ->
            val fraction = step.toDouble() / (DEMO_POINTS_PER_SEGMENT - 1)
            TrackPoint(
                tourId = tourId,
                segment = segment,
                recordedAt = startTime.plus(step * DEMO_POINT_INTERVAL_MINUTES, ChronoUnit.MINUTES),
                latitude = from.latitude + (to.latitude - from.latitude) * fraction,
                longitude = from.longitude + (to.longitude - from.longitude) * fraction,
            )
        }

    private companion object {
        /** Tage zwischen den drei Stationen der Demo-Tour; die Tour dauert damit [DEMO_TOUR_LENGTH_DAYS] + 1 Tage. */
        const val DEMO_TOUR_LENGTH_DAYS = 2L
        const val DEMO_POINTS_PER_SEGMENT = 6
        const val DEMO_POINT_INTERVAL_MINUTES = 90L

        /** Plausible, aber frei erfundene Koordinaten im Alpenraum - keine echten Adressen. */
        val DEMO_ROUTE = listOf(
            LatLon(47.4000, 10.9000),
            LatLon(47.4500, 11.1500),
            LatLon(47.5200, 11.4000),
        )
    }
}
