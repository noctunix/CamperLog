package app.restvolt.camperlog.ui.edit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourInput
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeChecklistRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeTrackRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import app.restvolt.camperlog.ui.defaultVehicle
import app.restvolt.camperlog.ui.settings.HomeLocationSettings
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context.getSharedPreferences("home_location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun homeLocationSettings(name: String = "", latitude: Double = 52.52, longitude: Double = 13.405) =
        HomeLocationSettings(context).also {
            it.name = name
            it.setLocation(latitude, longitude, CoordinateSource.ENTERED)
        }

    @Test
    fun changingTheVehicleMovesStationsAndChecklistsOfTheTour() = runTest {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A"), defaultVehicle(id = 2, name = "B")), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val stations = FakeStationRepository(listOf(station(vehicleId = 1, tourId = 1)))
        val checklists = FakeChecklistRepository(listOf(checklist(vehicleId = 1, tourId = 1)))
        val viewModel = EditTourViewModel(tours, vehicles, stations, checklists, 1, SavedStateHandle(), locale)

        viewModel.onVehicleChange(2)
        advanceUntilIdle()

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

        viewModel.onInputChange { it }
        advanceUntilIdle()

        assertEquals(1L, stations.stations.single().vehicleId)
        assertEquals(1L, checklists.checklists.single().vehicleId)
    }

    @Test
    fun newTour_preselectsTheFirstUnsoldVehicleInsteadOfASoldCurrentVehicle() = runTest {
        val sold = defaultVehicle(id = 1, name = "A", sold = true)
        val unsold = defaultVehicle(id = 2, name = "B")
        val vehicles = FakeVehicleRepository(listOf(sold, unsold), currentVehicleId = 1)
        val viewModel = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, SavedStateHandle(), locale)

        assertEquals(2L, viewModel.uiState.value.input.vehicleId)
    }

    @Test
    fun newTour_preselectsNothingWhenAllVehiclesAreSold() = runTest {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A", sold = true)), currentVehicleId = 1)
        val viewModel = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, SavedStateHandle(), locale)

        assertEquals(0L, viewModel.uiState.value.input.vehicleId)
    }

    @Test
    fun newTour_ignoresTheDemoVehicleWhenPreselecting() = runTest {
        val demo = defaultVehicle(id = 1, name = "Sample camper").copy(isDemo = true)
        val vehicles = FakeVehicleRepository(listOf(demo), currentVehicleId = 1)
        val viewModel = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, SavedStateHandle(), locale)

        assertEquals(0L, viewModel.uiState.value.input.vehicleId)
    }

    /**
     * Deckt die Fahrzeug-Inline-Anlage der Pilot-Tour "Erste Tour anlegen" ab: Beim Erreichen des
     * Fahrzeug-Schritts existiert nur das Demo-Fahrzeug, die Vorbelegung bleibt also zunächst leer.
     * Das Tourformular bleibt während der ganzen Zeit geöffnet (die Fahrzeuganlage pausiert nur die
     * geführte Tour, nicht dieses ViewModel) - die dauerhaft laufende Vorbelegung muss darum auch
     * reagieren, wenn das neue Fahrzeug erst nach der Anlage dieses ViewModels gespeichert wird.
     */
    @Test
    fun newTour_preselectsAVehicleCreatedWhileTheFormIsAlreadyOpen() = runTest {
        val demo = defaultVehicle(id = 1, name = "Sample camper").copy(isDemo = true)
        val vehicles = FakeVehicleRepository(listOf(demo), currentVehicleId = 1)
        val viewModel = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, SavedStateHandle(), locale)
        assertEquals(0L, viewModel.uiState.value.input.vehicleId)

        val newVehicleId = vehicles.save(defaultVehicle(id = 0, name = "Hymer"))

        assertEquals(newVehicleId, viewModel.uiState.value.input.vehicleId)
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

    @Test
    fun slugFollowsDestinationUntilManuallyChanged() = runTest {
        val viewModel = newViewModel()

        viewModel.onDestinationChange("Gardasee")
        assertEquals("gardasee", viewModel.uiState.value.input.slug)

        viewModel.onDestinationChange("Gardasee Nord")
        assertEquals("gardasee-nord", viewModel.uiState.value.input.slug)

        viewModel.onSlugChange("custom-slug")
        viewModel.onDestinationChange("Ostsee")
        assertEquals("custom-slug", viewModel.uiState.value.input.slug)
    }

    @Test
    fun slugPrefersNameOverDestination() = runTest {
        val viewModel = newViewModel()

        viewModel.onDestinationChange("Gardasee")
        viewModel.onNameChange("Sommerurlaub")

        assertEquals("sommerurlaub", viewModel.uiState.value.input.slug)
    }

    @Test
    fun settingAnEndDateResetsTheTrackSwitch() = runTest {
        val viewModel = newViewModel()
        viewModel.onTrackSwitchChange(true)
        assertEquals(true, viewModel.uiState.value.trackSwitch)

        viewModel.onEndDateChange(LocalDate.of(2026, 7, 4))

        assertEquals(false, viewModel.uiState.value.trackSwitch)
    }

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Der eigentliche Vorfall (GPS-
     * Aufzeichnung ließ sich nicht starten, weil ein vorheriges Speichern scheiterte) wird dadurch
     * strukturell unmöglich, dass eine neue Tour von Anfang an eine echte, stabile id hat.
     */
    @Test
    fun newTour_getsARealIdImmediatelyWithoutAnyExplicitSave() = runTest {
        val tours = FakeTourRepository()
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )

        val tourId = viewModel.uiState.value.tourId
        assertEquals(tours.tours.single().id, tourId)
        assertTrue(tours.tours.single().id != 0L)
    }

    @Test
    fun newTour_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val tours = FakeTourRepository()
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )
        val id = checkNotNull(viewModel.uiState.value.tourId)

        viewModel.onDestinationChange("Gardasee")
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", tours.tours.single().destination)

        advanceUntilIdle()

        assertEquals(id, tours.tours.single().id)
        assertEquals("Gardasee", tours.tours.single().destination)
    }

    @Test
    fun newTour_leavingWithoutAnyMeaningfulInput_deletesTheAutoCreatedDraft() = runTest {
        val tours = FakeTourRepository()
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )
        assertEquals(1, tours.tours.size)

        clear(viewModel)

        assertEquals(0, tours.tours.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newTour_leavingAfterTyping_keepsTheTourAndFlushesAStillPendingChange() = runTest {
        val tours = FakeTourRepository()
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )

        viewModel.onDestinationChange("Gardasee")
        clear(viewModel)

        assertEquals("Gardasee", tours.tours.single().destination)
    }

    /**
     * Der Konflikt "schon eine laufende Tour für dieses Fahrzeug" blockiert die Eingabe nicht mehr:
     * Die Tour wird dafür nicht angelegt, der Nutzer kann aber weiter tippen; sobald sich der
     * Konflikt auflöst, holt der nächste Autosave die inzwischen eingegebenen Daten nach.
     */
    @Test
    fun newTour_runningVehicleConflict_isNonBlockingAndResolvesOnTheNextSuccessfulAutosave() = runTest {
        val tours = FakeTourRepository()
        tours.failWithRunningTourConflict = true
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )

        assertTrue(viewModel.uiState.value.runningTourConflict)
        assertEquals(0, tours.tours.size)

        viewModel.onDestinationChange("Gardasee")
        advanceUntilIdle()
        assertEquals("Gardasee", viewModel.uiState.value.input.destination)
        assertEquals(0, tours.tours.size)

        tours.failWithRunningTourConflict = false
        viewModel.onNameChange("Sommerurlaub")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.runningTourConflict)
        assertEquals("Gardasee", tours.tours.single().destination)
    }

    @Test
    fun homeSwitchOn_withoutEndDate_createsOnlyTheStartStation() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(name = "Elternhaus"),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onDestinationChange("Gardasee")

        viewModel.onHomeSwitchChange(true)

        val saved = stations.stations.single()
        assertEquals(StationType.OVERNIGHT, saved.type)
        assertEquals("Elternhaus", saved.name)
        assertEquals(LocalDate.of(2026, 7, 1), saved.date)
        assertEquals(52.52, saved.latitude)
        assertEquals(13.405, saved.longitude)
        assertNull(saved.nights)
    }

    @Test
    fun homeSwitchOn_withEndDate_createsStartAndEndStations() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(),
            defaultHomeStationName = { "Zuhause" },
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onEndDateChange(LocalDate.of(2026, 7, 3))
        viewModel.onDestinationChange("Gardasee")

        viewModel.onHomeSwitchChange(true)

        assertEquals(2, stations.stations.size)
        assertEquals(setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)), stations.stations.map { it.date }.toSet())
        assertTrue(stations.stations.all { it.name == "Zuhause" && it.nights == null })
    }

    @Test
    fun homeSwitchOff_createsNoHomeStation() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onDestinationChange("Gardasee")

        assertEquals(0, stations.stations.size)
    }

    @Test
    fun homeSwitchOn_withoutAnyVehicleYet_usesTheResolvedVehicleIdForTheHomeStation() = runTest {
        val stations = FakeStationRepository()
        val tours = FakeTourRepository(currentVehicleId = { 7L })
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(emptyList()),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(name = "Elternhaus"),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onDestinationChange("Gardasee")

        viewModel.onHomeSwitchChange(true)

        assertEquals(7L, stations.stations.single().vehicleId)
    }

    @Test
    fun homeSwitchOn_withoutHomeLocationSet_createsNoHomeStation() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = HomeLocationSettings(context),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onDestinationChange("Gardasee")

        viewModel.onHomeSwitchChange(true)

        assertEquals(0, stations.stations.size)
    }

    /** Beim Ausschalten entfernte Zuhause-Station(en) entstehen beim nächsten Einschalten neu. */
    @Test
    fun homeSwitchOff_removesAStillUnchangedHomeStationAgain() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(name = "Elternhaus"),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onHomeSwitchChange(true)
        assertEquals(1, stations.stations.size)

        viewModel.onHomeSwitchChange(false)

        assertEquals(0, stations.stations.size)
    }

    /** Hat der Nutzer die automatisch angelegte Station inzwischen selbst bearbeitet, bleibt sie beim Ausschalten stehen. */
    @Test
    fun homeSwitchOff_keepsAHomeStationTheUserAlreadyEdited() = runTest {
        val stations = FakeStationRepository()
        val viewModel = EditTourViewModel(
            FakeTourRepository(),
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            stations,
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
            homeLocationSettings = homeLocationSettings(name = "Elternhaus"),
        )
        viewModel.onStartDateChange(LocalDate.of(2026, 7, 1))
        viewModel.onHomeSwitchChange(true)
        val created = stations.stations.single()
        stations.save(created.copy(name = "Eigener Name"))

        viewModel.onHomeSwitchChange(false)

        assertEquals(1, stations.stations.size)
    }

    /** Kernregressionstest für Teil A des Umbaus: Eine bestehende Tour hat keinen Speichern-Button mehr. */
    @Test
    fun existingTour_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            1,
            SavedStateHandle(),
            locale,
        )

        viewModel.onDestinationChange("Ostsee")
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("Gardasee", tours.tours.single().destination)

        advanceUntilIdle()

        assertEquals("Ostsee", tours.tours.single().destination)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht auch für eine bestehende Tour beim Verlassen nicht verloren. */
    @Test
    fun existingTour_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            1,
            SavedStateHandle(),
            locale,
        )

        viewModel.onDestinationChange("Ostsee")
        clear(viewModel)

        assertEquals("Ostsee", tours.tours.single().destination)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf die Tour nicht grundlos neu schreiben. */
    @Test
    fun existingTour_leavingWithoutAnyChange_doesNotRewriteTheTour() = runTest {
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            1,
            SavedStateHandle(),
            locale,
        )
        advanceUntilIdle()

        clear(viewModel)

        assertEquals(0, tours.saveCount)
    }

    @Test
    fun needsLeaveConfirmation_newUnmodifiedDraft_doesNotNeedConfirmation() {
        assertFalse(EditUiState(isNew = true).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = EditUiState(isNew = true, input = TourInput(destination = "Gardasee"), isDirty = true)
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingTourWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(EditUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingTourWithChanges_needsConfirmation() {
        assertTrue(EditUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht eine neue Tour auch dann vollständig, wenn sie schon sinnvoll befüllt ist. */
    @Test
    fun newTour_discardConfirmed_deletesTheTourEvenWithMeaningfulInput() = runTest {
        val tours = FakeTourRepository()
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            0,
            SavedStateHandle(),
            locale,
        )

        viewModel.onDestinationChange("Gardasee")
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(tours.tours.isEmpty())
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einer bestehenden Tour setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück. */
    @Test
    fun existingTour_discardConfirmed_revertsFieldChangesToTheOpenedState() = runTest {
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val viewModel = EditTourViewModel(
            tours,
            FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
            FakeStationRepository(),
            FakeChecklistRepository(),
            1,
            SavedStateHandle(),
            locale,
        )

        viewModel.onDestinationChange("Ostsee")
        advanceUntilIdle()
        assertEquals("Ostsee", tours.tours.single().destination)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Gardasee", tours.tours.single().destination)
    }

    /**
     * Kernregressionstest für Teil B: Verwerfen muss auch einen während der Sitzung gewechselten
     * Fahrzeugbezug komplett zurückdrehen, sonst blieben Stationen und Checklisten beim falschen
     * Fahrzeug stehen, während die Tour selbst wieder beim ursprünglichen steht.
     */
    @Test
    fun existingTour_discardConfirmed_revertsAVehicleChangeIncludingItsStationsAndChecklists() = runTest {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A"), defaultVehicle(id = 2, name = "B")), currentVehicleId = 1)
        val tours = FakeTourRepository(listOf(tour(vehicleId = 1)))
        val stations = FakeStationRepository(listOf(station(vehicleId = 1, tourId = 1)))
        val checklists = FakeChecklistRepository(listOf(checklist(vehicleId = 1, tourId = 1)))
        val viewModel = EditTourViewModel(tours, vehicles, stations, checklists, 1, SavedStateHandle(), locale)

        viewModel.onVehicleChange(2)
        advanceUntilIdle()
        assertEquals(2L, tours.tours.single().vehicleId)
        assertEquals(2L, stations.stations.single().vehicleId)
        assertEquals(2L, checklists.checklists.single().vehicleId)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals(1L, tours.tours.single().vehicleId)
        assertEquals(1L, stations.stations.single().vehicleId)
        assertEquals(1L, checklists.checklists.single().vehicleId)
    }

    /** [EditTourViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: EditTourViewModel) {
        ViewModelStore().apply { put("tour", viewModel) }.clear()
    }

    private fun newViewModel() = EditTourViewModel(
        FakeTourRepository(),
        FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "A")), currentVehicleId = 1),
        FakeStationRepository(),
        FakeChecklistRepository(),
        0,
        SavedStateHandle(),
        locale,
    )

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
