package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Prüft, dass das Ändern des Fahrzeugs einer Tour ihre Stationen und Checklisten mitnimmt. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditTourViewModelTest {

    private val locale = { Locale.US }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun changingTheVehicleMovesStationsAndChecklistsOfTheTour() = runTest {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A"), defaultVehicle(id = 2, name = "B")), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val stations = FakeStationRepository(listOf(station(vehicleId = 1, tourId = 1)))
        val checklists = FakeChecklistRepository(listOf(checklist(vehicleId = 1, tourId = 1)))
        val viewModel = EditTourViewModel(tours, vehicles, stations, checklists, 1, SavedStateHandle(), locale)

        viewModel.onVehicleChange(2)
        viewModel.save()

        assertEquals(2L, stations.stations.single().vehicleId)
        assertEquals(2L, checklists.checklists.single().vehicleId)
    }

    @Test
    fun keepingTheVehicleLeavesStationsAndChecklistsUntouched() = runTest {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val stations = FakeStationRepository(listOf(station(vehicleId = 1, tourId = 1)))
        val checklists = FakeChecklistRepository(listOf(checklist(vehicleId = 1, tourId = 1)))
        val viewModel = EditTourViewModel(tours, vehicles, stations, checklists, 1, SavedStateHandle(), locale)

        viewModel.save()

        assertEquals(1L, stations.stations.single().vehicleId)
        assertEquals(1L, checklists.checklists.single().vehicleId)
    }

    @Test
    fun settingEndDateDerivesAllMetricsFromTourData() = runTest {
        val running = tour(vehicleId = 1).copy(endDate = null, travelDays = 99, overnightStays = 99, distanceKm = 99)
        val stations = FakeStationRepository(listOf(station(vehicleId = 1, tourId = 1).copy(nights = 2)))
        val tracks = FakeTrackRepository(
            listOf(
                point(second = 0, longitude = 0.0),
                point(second = 1, longitude = 0.009),
            ),
        )
        val viewModel = EditTourViewModel(
            FakeTourRepository(listOf(running)),
            FakeVehicleRepository(),
            stations,
            FakeChecklistRepository(),
            1,
            SavedStateHandle(),
            locale,
            tracks,
        )

        viewModel.onEndDateChange(LocalDate.of(2026, 7, 4))

        assertEquals("4", viewModel.uiState.value.input.travelDays)
        assertEquals("2", viewModel.uiState.value.input.overnightStays)
        assertEquals("1", viewModel.uiState.value.input.distanceKm)
    }

    private fun tour(vehicleId: Long) = Tour(
        id = 1,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 3),
        destination = "Gardasee",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(vehicleId: Long, tourId: Long) = Station(
        id = 1,
        vehicleId = vehicleId,
        tourId = tourId,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun checklist(vehicleId: Long, tourId: Long) = Checklist(
        id = 1,
        vehicleId = vehicleId,
        tourId = tourId,
        title = "Abfahrt",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun point(second: Long, longitude: Double) = TrackPoint(
        tourId = 1,
        segment = 1,
        recordedAt = Instant.EPOCH.plusSeconds(second),
        latitude = 0.0,
        longitude = longitude,
    )
}
