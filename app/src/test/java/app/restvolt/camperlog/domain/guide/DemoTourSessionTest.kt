package app.restvolt.camperlog.domain.guide

import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Simulierte Demo-Tour des Tutorials, gegen die Fake-Repositories der UI-Tests. */
class DemoTourSessionTest {

    private val tours = FakeTourRepository(emptyList())
    private val vehicles = FakeVehicleRepository(initial = listOf(defaultVehicle(id = 1, name = "Echt")))
    private val stations = FakeStationRepository()
    private val tracks = FakeTrackRepository()
    private val stoppedTrackingFor = mutableListOf<Long>()
    private val session = DemoTourSession(tours, stations, tracks, vehicles) { tourId -> stoppedTrackingFor += tourId }

    @Test
    fun begin_createsAFinishedDemoTourWithItsOwnVehicleThreeStationsAndTwoTrackSegments() = runTest {
        val demoTourId = session.begin()

        val tour = tours.tours.single { it.id == demoTourId }
        assertTrue(tour.isDemo)
        assertTrue(tour.endDate != null)
        assertEquals(listOf(demoTourId), tours.demoTourIds())

        val vehicle = vehicles.vehicles.single { it.id == tour.vehicleId }
        assertTrue(vehicle.isDemo)
        assertTrue(vehicle.id != 1L) // nicht das echte Fahrzeug, siehe Owner-Entscheidung.

        val demoStations = stations.stations.filter { it.tourId == demoTourId }
        assertEquals(3, demoStations.size)
        // Keine der Ver-/Entsorgungs-Dienste, die Bordbuch-Einträge erzeugen würden; kein Kilometerstand.
        assertTrue(demoStations.all { it.services.none { service -> service in SYNCED_SERVICE_LOG_TYPES } })
        assertTrue(demoStations.none { it.services.contains(StationService.CASSETTE) })
        assertTrue(demoStations.all { it.odometerKm == null })

        val points = tracks.points.filter { it.tourId == demoTourId }
        assertTrue(points.isNotEmpty())
        assertEquals(setOf(1, 2), points.map { it.segment }.toSet())
    }

    @Test
    fun begin_sweepsAnOrphanedDemoTourFromAPreviousRunFirst() = runTest {
        val firstDemoTourId = session.begin()
        val firstDemoVehicleId = tours.tours.single { it.id == firstDemoTourId }.vehicleId

        val secondDemoTourId = session.begin()

        assertFalse(tours.tours.any { it.id == firstDemoTourId })
        assertFalse(vehicles.vehicles.any { it.id == firstDemoVehicleId })
        assertEquals(listOf(secondDemoTourId), tours.demoTourIds())
        // sweepOrphans() innerhalb des zweiten begin() stoppt die erste Demo-Tour, bevor sie gelöscht wird.
        assertEquals(listOf(firstDemoTourId), stoppedTrackingFor)
    }

    @Test
    fun end_stopsTrackingBeforeDeletingAndRemovesTheTourAndVehicleButKeepsTheRealOne() = runTest {
        val demoTourId = session.begin()
        val demoVehicleId = tours.tours.single { it.id == demoTourId }.vehicleId

        session.end()

        assertEquals(listOf(demoTourId), stoppedTrackingFor)
        assertFalse(tours.tours.any { it.id == demoTourId })
        assertFalse(vehicles.vehicles.any { it.id == demoVehicleId })
        // Die Trackpunkte selbst hängen am Fremdschlüssel der Tour (ON DELETE CASCADE, siehe
        // MigrationTest); die In-Memory-Fakes bilden das nicht nach, anders als das echte Room.
        assertEquals(listOf(1L), vehicles.vehicles.map { it.id })
        assertFalse(session.active)
    }

    @Test
    fun sweepOrphans_removesDemoDataRegardlessOfTheActiveFlag() = runTest {
        // Simuliert einen Rest, ohne je begin() aufgerufen zu haben - das active-Flag ist also false.
        val demoVehicleId = vehicles.save(defaultVehicle(id = 0, name = "Demo").copy(isDemo = true))
        val demoTourId = tours.save(
            Tour(
                vehicleId = demoVehicleId,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 2),
                destination = "",
                tourType = TourType.VACATION,
                travelDays = 2,
                overnightStays = 1,
                distanceKm = 10,
                costs = emptyList(),
                notes = "",
                mapLink = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                isDemo = true,
            ),
        )
        assertFalse(session.active)

        session.sweepOrphans()

        assertFalse(tours.tours.any { it.id == demoTourId })
        assertFalse(vehicles.vehicles.any { it.id == demoVehicleId })
    }

    @Test
    fun active_reflectsWhetherADemoSessionIsCurrentlyOngoing() = runTest {
        assertFalse(session.active)

        session.begin()
        assertTrue(session.active)

        session.end()
        assertFalse(session.active)
    }
}
