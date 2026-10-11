package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.restvolt.camperlog.domain.AxleInput
import app.restvolt.camperlog.domain.VehicleInput
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Deckt den Umbau eines Fahrzeugs auf implizites Speichern ab, analog zu `RepairEditViewModelTest`.
 * Ein neues Fahrzeug wird immer neben einem schon bestehenden angelegt - genau wie in der echten App,
 * die nie ganz ohne Fahrzeug ist (siehe `VehicleDao.resolveCurrentVehicleId`) - daher lehnt
 * [FakeVehicleRepository.delete] ein Löschen beim Verwerfen hier nie als letztes Fahrzeug ab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditVehicleViewModelTest {

    private val locale = { Locale.US }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(vehicles: FakeVehicleRepository, vehicleId: Long = 0) = EditVehicleViewModel(
        repository = vehicles,
        vehicleId = vehicleId,
        savedStateHandle = SavedStateHandle(),
        locale = locale,
    )

    /** Das beim Öffnen eines neuen Fahrzeugs schon bestehende, davon unberührte andere Fahrzeug. */
    private fun repositoryWithOtherVehicle() = FakeVehicleRepository(listOf(vehicle(id = 1, name = "Anderes")))

    private fun FakeVehicleRepository.createdVehicle() = vehicles.single { it.id != 1L }

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Ein neues Fahrzeug hat von Anfang
     * an eine echte, stabile id, ohne dass der Nutzer explizit speichern muss.
     */
    @Test
    fun newVehicle_getsARealIdImmediatelyWithoutAnyExplicitSave() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        newViewModel(vehicles)

        assertEquals(2, vehicles.vehicles.size)
    }

    /** Ein noch leerer Pflicht-Name blockiert das allererste automatische Anlegen nicht. */
    @Test
    fun newVehicle_persistsImmediatelyEvenWithABlankName() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        newViewModel(vehicles)

        assertEquals("", vehicles.createdVehicle().name)
    }

    @Test
    fun newVehicle_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(name = "Wohnmobil") }
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", vehicles.createdVehicle().name)

        advanceUntilIdle()

        assertEquals("Wohnmobil", vehicles.createdVehicle().name)
    }

    /** Eine ungültige Kennzahl blockiert den Schreibzugriff nicht - sie fällt für den gespeicherten Stand einfach weg. */
    @Test
    fun invalidNumberField_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(name = "Wohnmobil", powerKw = "abc") }
        advanceUntilIdle()

        assertEquals("Wohnmobil", vehicles.createdVehicle().name)
        assertNull(vehicles.createdVehicle().powerKw)
        // Im Formular bleibt die ungültige Eingabe sichtbar, nur der gespeicherte Stand ist bereinigt.
        assertEquals("abc", viewModel.uiState.value.input.powerKw)
    }

    /** Ein ungültiger Betrag blockiert den Schreibzugriff ebenfalls nicht. */
    @Test
    fun invalidAmount_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(name = "Wohnmobil", purchasePrice = "12km") }
        advanceUntilIdle()

        assertEquals("Wohnmobil", vehicles.createdVehicle().name)
        assertNull(vehicles.createdVehicle().purchasePrice)
        assertEquals("12km", viewModel.uiState.value.input.purchasePrice)
    }

    @Test
    fun saveFailure_setsSaveFailedFlag() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)
        vehicles.failWrites = true

        viewModel.onInputChange { it.copy(name = "Wohnmobil") }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun newVehicle_leavingWithoutAnyMeaningfulInput_deletesTheAutoCreatedDraft() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)
        assertEquals(2, vehicles.vehicles.size)

        clear(viewModel)

        assertEquals(1, vehicles.vehicles.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newVehicle_leavingAfterTyping_keepsTheVehicleAndFlushesAStillPendingChange() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(name = "Wohnmobil") }
        clear(viewModel)

        assertEquals("Wohnmobil", vehicles.createdVehicle().name)
    }

    @Test
    fun existingVehicle_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val vehicles = FakeVehicleRepository(listOf(vehicle(id = 1, name = "Alt")))
        val viewModel = newViewModel(vehicles, vehicleId = 1)

        viewModel.onInputChange { it.copy(name = "Neu") }
        assertEquals("Alt", vehicles.vehicles.single().name)

        advanceUntilIdle()

        assertEquals("Neu", vehicles.vehicles.single().name)
    }

    @Test
    fun existingVehicle_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val vehicles = FakeVehicleRepository(listOf(vehicle(id = 1, name = "Alt")))
        val viewModel = newViewModel(vehicles, vehicleId = 1)

        viewModel.onInputChange { it.copy(name = "Neu") }
        clear(viewModel)

        assertEquals("Neu", vehicles.vehicles.single().name)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf das Fahrzeug nicht grundlos neu schreiben. */
    @Test
    fun existingVehicle_leavingWithoutAnyChange_doesNotRewriteTheVehicle() = runTest {
        val vehicles = FakeVehicleRepository(listOf(vehicle(id = 1, name = "Alt")))
        val viewModel = newViewModel(vehicles, vehicleId = 1)
        advanceUntilIdle()
        vehicles.saveCount = 0

        clear(viewModel)

        assertEquals(0, vehicles.saveCount)
    }

    @Test
    fun needsLeaveConfirmation_newUnmodifiedDraft_doesNotNeedConfirmation() {
        val state = EditVehicleUiState(isNew = true, input = VehicleInput())
        assertFalse(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = EditVehicleUiState(isNew = true, input = VehicleInput(name = "Wohnmobil"), isDirty = true)
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingVehicleWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(EditVehicleUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingVehicleWithChanges_needsConfirmation() {
        assertTrue(EditVehicleUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht ein neues Fahrzeug auch dann vollständig, wenn es schon sinnvoll befüllt ist. */
    @Test
    fun newVehicle_discardConfirmed_deletesTheVehicleEvenWithMeaningfulInput() = runTest {
        val vehicles = repositoryWithOtherVehicle()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(name = "Wohnmobil") }
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals(1, vehicles.vehicles.size)
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einem bestehenden Fahrzeug setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück, inklusive Achsen. */
    @Test
    fun existingVehicle_discardConfirmed_revertsFieldChangesToTheOpenedStateIncludingAxles() = runTest {
        val original = vehicle(id = 1, name = "Alt")
        val vehicles = FakeVehicleRepository(listOf(original))
        val viewModel = newViewModel(vehicles, vehicleId = 1)

        viewModel.onInputChange { it.copy(name = "Neu", axles = it.axles + AxleInput(tireSize = "225/75 R16")) }
        advanceUntilIdle()
        assertEquals("Neu", vehicles.vehicles.single().name)
        assertEquals(3, vehicles.vehicles.single().axles.size)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Alt", vehicles.vehicles.single().name)
        assertEquals(original.axles, vehicles.vehicles.single().axles)
    }

    /** [EditVehicleViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: EditVehicleViewModel) {
        ViewModelStore().apply { put("vehicle", viewModel) }.clear()
    }

    private fun vehicle(id: Long, name: String) = defaultVehicle(id = id, name = name)
}
