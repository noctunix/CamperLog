package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeLogRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Deckt den Umbau einer Station auf implizites Speichern ab, analog zu `EditTourViewModelTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditStationViewModelTest {

    private val locale = { Locale.US }
    private val today = { LocalDate.of(2026, 7, 1) }
    private val timeNow = { LocalTime.of(10, 0) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        stations: FakeStationRepository = FakeStationRepository(),
        tours: FakeTourRepository = FakeTourRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
        stationId: Long = 0,
        initialTourId: Long? = null,
    ) = EditStationViewModel(
        repository = stations,
        tours = tours,
        vehicles = vehicles,
        stationId = stationId,
        initialTourId = initialTourId,
        savedStateHandle = SavedStateHandle(),
        today = today,
        timeNow = timeNow,
        locale = locale,
    )

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Eine neue Station ohne Tourbezug
     * hat von Anfang an eine echte, stabile id - Anhänge lassen sich darauf sofort beziehen, ohne auf
     * einen expliziten Speichern-Erfolg zu warten, und ohne dass dafür ein Fehlerzustand nötig ist.
     */
    @Test
    fun newStationWithoutTour_getsARealIdImmediatelyWithoutAnyExplicitSaveOrError() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)

        val stationId = viewModel.uiState.value.stationId
        assertEquals(stations.stations.single().id, stationId)
        assertTrue(stationId != 0L)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    /** Wie [newStationWithoutTour_getsARealIdImmediatelyWithoutAnyExplicitSaveOrError], aber mit Tourbezug. */
    @Test
    fun newStationWithTour_getsARealIdImmediatelyWithoutAnyExplicitSaveOrError() = runTest {
        val stations = FakeStationRepository()
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val viewModel = newViewModel(stations, tours, FakeVehicleRepository(listOf(defaultVehicle(id = 1)), currentVehicleId = 1), initialTourId = 1)

        val stationId = viewModel.uiState.value.stationId
        assertEquals(stations.stations.single().id, stationId)
        assertTrue(stationId != 0L)
        assertEquals(1L, stations.stations.single().tourId)
        assertEquals(1L, stations.stations.single().vehicleId)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun newStation_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)
        val id = viewModel.uiState.value.stationId

        viewModel.onInputChange { it.copy(name = "Camping Moskenes") }
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", stations.stations.single().name)

        advanceUntilIdle()

        assertEquals(id, stations.stations.single().id)
        assertEquals("Camping Moskenes", stations.stations.single().name)
    }

    /** Eine ungültige Kennzahl blockiert den Schreibzugriff nicht - sie fällt für den gespeicherten Stand einfach weg. */
    @Test
    fun invalidOdometer_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)

        viewModel.onInputChange { it.copy(name = "Camping Moskenes", odometerKm = "abc") }
        advanceUntilIdle()

        assertEquals("Camping Moskenes", stations.stations.single().name)
        assertEquals(null, stations.stations.single().odometerKm)
        // Im Formular bleibt die ungültige Eingabe sichtbar, nur der gespeicherte Stand ist bereinigt.
        assertEquals("abc", viewModel.uiState.value.input.odometerKm)
    }

    /** Fehlende Koordinaten blockieren den Schreibzugriff ebenfalls nicht - sie fallen für den gespeicherten Stand weg. */
    @Test
    fun incompleteCoordinates_isSanitizedToNullInsteadOfBlockingTheWrite() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)

        viewModel.onInputChange { it.copy(latitude = 68.0912, longitude = null) }
        advanceUntilIdle()

        assertEquals(null, stations.stations.single().latitude)
        assertEquals(68.0912, viewModel.uiState.value.input.latitude)
    }

    @Test
    fun saveFailure_setsSaveFailedFlag() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)
        stations.failWrites = true

        viewModel.onInputChange { it.copy(name = "Camping Moskenes") }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveFailed)
    }

    /**
     * Anders als bei einer Tour oder Reparatur gibt es für eine Station keinen rein zufällig
     * geöffneten, leeren Entwurf: Ihr Formular ist nur über eine bewusste Typauswahl erreichbar.
     * Ein Verlassen ohne jede weitere Eingabe behält die automatisch angelegte Station deshalb,
     * statt sie wie bei Tour/Reparatur stillschweigend zu löschen - nur ein echtes [onDiscardConfirmed]
     * löscht sie noch.
     */
    @Test
    fun newStation_leavingWithoutAnyFurtherInput_keepsTheAutoCreatedStation() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)
        assertEquals(1, stations.stations.size)

        clear(viewModel)

        assertEquals(1, stations.stations.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newStation_leavingAfterTyping_keepsTheStationAndFlushesAStillPendingChange() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)

        viewModel.onInputChange { it.copy(name = "Camping Moskenes") }
        clear(viewModel)

        assertEquals("Camping Moskenes", stations.stations.single().name)
    }

    @Test
    fun existingStation_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val stations = FakeStationRepository(listOf(station(name = "Alt")))
        val viewModel = newViewModel(stations, stationId = 1)

        viewModel.onInputChange { it.copy(name = "Neu") }
        assertEquals("Alt", stations.stations.single().name)

        advanceUntilIdle()

        assertEquals("Neu", stations.stations.single().name)
    }

    @Test
    fun existingStation_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val stations = FakeStationRepository(listOf(station(name = "Alt")))
        val viewModel = newViewModel(stations, stationId = 1)

        viewModel.onInputChange { it.copy(name = "Neu") }
        clear(viewModel)

        assertEquals("Neu", stations.stations.single().name)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf die Station nicht grundlos neu schreiben. */
    @Test
    fun existingStation_leavingWithoutAnyChange_doesNotRewriteTheStation() = runTest {
        val stations = FakeStationRepository(listOf(station(name = "Alt")))
        val viewModel = newViewModel(stations, stationId = 1)
        advanceUntilIdle()
        stations.saveCount = 0

        clear(viewModel)

        assertEquals(0, stations.saveCount)
    }

    /**
     * Anders als bei Tour/Reparatur immer, auch ohne jede weitere Eingabe: Das Stationsformular ist
     * nur über eine bewusste Typauswahl erreichbar, einen rein zufällig geöffneten leeren Entwurf
     * gibt es also nicht.
     */
    @Test
    fun needsLeaveConfirmation_new_alwaysNeedsConfirmation() {
        assertTrue(StationEditUiState(isNew = true).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = StationEditUiState(isNew = true, input = StationInput(name = "Camping Moskenes"), isDirty = true)
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingStationWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(StationEditUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingStationWithChanges_needsConfirmation() {
        assertTrue(StationEditUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht eine neue Station auch dann vollständig, wenn sie schon sinnvoll befüllt ist. */
    @Test
    fun newStation_discardConfirmed_deletesTheStationEvenWithMeaningfulInput() = runTest {
        val stations = FakeStationRepository()
        val viewModel = newViewModel(stations)

        viewModel.onInputChange { it.copy(name = "Camping Moskenes") }
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(stations.stations.isEmpty())
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einer bestehenden Station setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück. */
    @Test
    fun existingStation_discardConfirmed_revertsFieldChangesToTheOpenedState() = runTest {
        val stations = FakeStationRepository(listOf(station(name = "Alt")))
        val viewModel = newViewModel(stations, stationId = 1)

        viewModel.onInputChange { it.copy(name = "Neu") }
        advanceUntilIdle()
        assertEquals("Neu", stations.stations.single().name)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Alt", stations.stations.single().name)
    }

    /**
     * Kernregressionstest für die Bordbuch-Synchronisation unter Autosave: Ein während der Sitzung
     * angehaktes Ver-/Entsorgungs-Häkchen erzeugt genau einen Bordbuch-Eintrag, auch über mehrere
     * Autosave-Durchläufe hinweg; Verwerfen macht ihn wieder rückgängig, weil [EditStationViewModel]
     * dafür unverändert über `repository.save` geht - der diff-basierte Abgleich in
     * `RoomStationRepository.save` (hier durch [FakeStationRepository] nachgebildet) bleibt also
     * wirksam.
     */
    @Test
    fun existingStation_togglingCassetteService_createsExactlyOneLogEntry_andDiscardingRemovesItAgain() = runTest {
        val logs = FakeLogRepository()
        val stations = FakeStationRepository(listOf(station(name = "Alt")), logs)
        val viewModel = newViewModel(stations, stationId = 1)

        viewModel.onInputChange { it.copy(services = setOf(StationService.CASSETTE)) }
        advanceUntilIdle()
        // Eine zweite, inhaltsgleiche Autosave-Runde darf keinen zweiten Eintrag erzeugen.
        viewModel.onInputChange { it.copy(name = "Alt", services = setOf(StationService.CASSETTE)) }
        advanceUntilIdle()

        assertEquals(1, logs.entries.size)
        assertEquals(LogType.CASSETTE_EMPTIED, logs.entries.single().type)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(logs.entries.isEmpty())
        assertEquals(emptySet<StationService>(), stations.stations.single().services)
        assertEquals("Alt", stations.stations.single().name)
    }

    /** [EditStationViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: EditStationViewModel) {
        ViewModelStore().apply { put("station", viewModel) }.clear()
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

    private fun station(name: String) = Station(
        id = 1,
        vehicleId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 1),
        name = name,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
