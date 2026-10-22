package app.restvolt.camperlog.domain.guide

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.LatLon
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.defaultCostCategory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Anzeigetexte der Demo-Tour; aus `strings.xml` gelesen und ohne Android-Abhängigkeit hier hinein gereicht. */
data class DemoTourContent(
    val vehicleName: String = "Sample camper",
    val destination: String = "Alpine loop",
    val stationNames: List<String> = listOf(
        "Lakeside pitch",
        "Fresh water at the harbour",
        "Filling station",
        "Mountain tunnel",
        "Mountain pass viewpoint",
        "Forest campsite",
    ),
)

/**
 * Hält die simulierte Demo-Tour des Tutorials bereit: eine bereits beendete Tour mit vorgefertigten
 * Trackpunkten und sechs Stationen verschiedener Art auf einem eigenen, als Demo markierten Fahrzeug ([Vehicle.isDemo]), unabhängig von
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

    /** Id der laufenden Demo-Tour, solange [active]; sonst `null`. */
    @Volatile
    var tourId: Long? = null
        private set

    /**
     * Räumt Reste eines abgebrochenen vorherigen Durchlaufs auf ([sweepOrphans]) und legt dann eine
     * neue, bereits beendete Demo-Tour mit eigenem Demo-Fahrzeug, sechs Stationen (Übernachtung,
     * Versorgung, Tanken, Maut, Sehenswertes, Übernachtung) und vorgefertigten Trackpunkten an.
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
        this.tourId = tourId
        tourId
    }

    /** Beendet die laufende Demo-Sitzung und entfernt alle Demo-Daten sofort. */
    suspend fun end() = mutex.withLock {
        sweepOrphansLocked()
        active = false
        tourId = null
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
        return DEMO_STOPS.mapIndexed { index, stop ->
            val point = DEMO_ROUTE[index]
            Station(
                vehicleId = vehicleId,
                tourId = tourId,
                type = stop.type,
                date = start.plusDays(stop.dayOffset),
                name = content.stationNames.getOrElse(index) { content.destination },
                latitude = point.latitude,
                longitude = point.longitude,
                coordinateSource = CoordinateSource.ENTERED,
                nights = if (stop.type == StationType.OVERNIGHT) 1 else null,
                siteKind = if (stop.type == StationType.OVERNIGHT) SiteKind.CAMPSITE else null,
                services = stop.services,
                fuelLiters = stop.fuelLiters,
                tollKind = stop.tollKind,
                tollCountry = stop.tollCountry,
                costs = stop.costMinor?.let { listOf(StationCost(stop.type.defaultCostCategory, Money(it, EUR))) }.orEmpty(),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    /**
     * Ein Segment je Paar aufeinanderfolgender Stationen, je mit ein paar interpolierten Punkten. Die
     * Segmente eines Tages laufen nacheinander ab 08:00 Uhr, damit die Zeiten nicht über das Tourende
     * hinausragen und sich nicht überlappen.
     */
    private fun demoTrackPoints(tourId: Long, start: LocalDate): List<TrackPoint> {
        val segmentsPerDay = mutableMapOf<Long, Int>()
        return DEMO_ROUTE.zipWithNext().flatMapIndexed { segmentIndex, (from, to) ->
            val day = DEMO_STOPS[segmentIndex].dayOffset
            val slot = segmentsPerDay.merge(day, 1, Int::plus)!! - 1
            val segmentStart = start.plusDays(day).atStartOfDay(ZoneOffset.UTC).toInstant()
                .plus(DEMO_FIRST_SEGMENT_HOUR + slot * DEMO_SEGMENT_SLOT_HOURS, ChronoUnit.HOURS)
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

    /** Eine vorgefertigte Demo-Station; [dayOffset] zählt Tage ab Tourbeginn, Beträge in Cent. */
    private class DemoStop(
        val type: StationType,
        val dayOffset: Long,
        val costMinor: Long? = null,
        val services: Set<StationService> = emptySet(),
        val fuelLiters: BigDecimal? = null,
        val tollKind: TollKind? = null,
        val tollCountry: String? = null,
    )

    private companion object {
        /** Tage zwischen erster und letzter Station der Demo-Tour; die Tour dauert damit [DEMO_TOUR_LENGTH_DAYS] + 1 Tage. */
        const val DEMO_TOUR_LENGTH_DAYS = 2L
        const val DEMO_POINTS_PER_SEGMENT = 6
        const val DEMO_POINT_INTERVAL_MINUTES = 20L
        const val DEMO_FIRST_SEGMENT_HOUR = 8L
        const val DEMO_SEGMENT_SLOT_HOURS = 2L

        /**
         * Die sechs Stationen, ein Beispiel je Hauptart. Ver-/Entsorgung nur mit Frischwasser: Kassette,
         * Grauwasser und Gas würden Bordbuch-Einträge erzeugen. Eine Tank-Station legt zwar einen
         * Bordbuch-Eintrag am Demo-Fahrzeug an, der aber mit diesem Fahrzeug wieder verschwindet.
         */
        val DEMO_STOPS = listOf(
            DemoStop(StationType.OVERNIGHT, dayOffset = 0, costMinor = 3_200),
            DemoStop(StationType.SUPPLY, dayOffset = 1, costMinor = 300, services = setOf(StationService.FRESH_WATER)),
            DemoStop(StationType.FUEL, dayOffset = 1, costMinor = 9_500, services = setOf(StationService.DIESEL), fuelLiters = BigDecimal("55.0")),
            DemoStop(StationType.TOLL, dayOffset = 1, costMinor = 1_300, tollKind = TollKind.TUNNEL, tollCountry = "AT"),
            DemoStop(StationType.SIGHT, dayOffset = 2),
            DemoStop(StationType.OVERNIGHT, dayOffset = 2, costMinor = 2_800),
        )

        /** Plausible, aber frei erfundene Koordinaten im Alpenraum - keine echten Adressen; je Eintrag von [DEMO_STOPS]. */
        val DEMO_ROUTE = listOf(
            LatLon(47.4000, 10.9000),
            LatLon(47.4200, 11.0000),
            LatLon(47.4350, 11.0800),
            LatLon(47.4500, 11.1500),
            LatLon(47.4900, 11.3000),
            LatLon(47.5200, 11.4000),
        )
    }
}
