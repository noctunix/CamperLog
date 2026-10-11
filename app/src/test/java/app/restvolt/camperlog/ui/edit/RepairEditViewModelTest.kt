package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.RepairInput
import app.restvolt.camperlog.ui.FakeExchangeRateRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
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

/** Deckt den Umbau einer Reparatur auf implizites Speichern ab, analog zu `EditTourViewModelTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepairEditViewModelTest {

    private val locale = { Locale.US }
    private val today = { LocalDate.of(2026, 7, 1) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(vehicles: FakeVehicleRepository = FakeVehicleRepository(), repairId: Long = 0) = RepairEditViewModel(
        repository = vehicles,
        exchangeRates = FakeExchangeRateRepository(),
        vehicleId = vehicles.currentVehicleId,
        repairId = repairId,
        savedStateHandle = SavedStateHandle(),
        locale = locale,
        today = today,
    )

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Eine neue Reparatur hat von Anfang
     * an eine echte, stabile id - Anhänge lassen sich darauf sofort beziehen, ohne auf einen
     * expliziten Speichern-Erfolg zu warten.
     */
    @Test
    fun newRepair_getsARealIdImmediatelyWithoutAnyExplicitSave() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)

        val repairId = viewModel.uiState.value.repairId
        assertEquals(vehicles.repairs.single().id, repairId)
        assertTrue(repairId != 0L)
    }

    /** Eine noch leere Pflicht-Beschreibung blockiert das allererste automatische Anlegen nicht. */
    @Test
    fun newRepair_persistsImmediatelyEvenWithAnEmptyDescription() = runTest {
        val vehicles = FakeVehicleRepository()
        newViewModel(vehicles)

        assertEquals("", vehicles.repairs.single().description)
    }

    @Test
    fun newRepair_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)
        val id = viewModel.uiState.value.repairId

        viewModel.onInputChange { it.copy(description = "Reifen gewechselt") }
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", vehicles.repairs.single().description)

        advanceUntilIdle()

        assertEquals(id, vehicles.repairs.single().id)
        assertEquals("Reifen gewechselt", vehicles.repairs.single().description)
    }

    /** Eine ungültige Kennzahl blockiert den Schreibzugriff nicht - sie fällt für den gespeicherten Stand einfach weg. */
    @Test
    fun invalidOdometer_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(description = "Reifen", odometerKm = "abc") }
        advanceUntilIdle()

        assertEquals("Reifen", vehicles.repairs.single().description)
        assertNull(vehicles.repairs.single().odometerKm)
        // Im Formular bleibt die ungültige Eingabe sichtbar, nur der gespeicherte Stand ist bereinigt.
        assertEquals("abc", viewModel.uiState.value.input.odometerKm)
    }

    /** Ein ungültiger Betrag blockiert den Schreibzugriff ebenfalls nicht. */
    @Test
    fun invalidCost_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(description = "Reifen", cost = "12km") }
        advanceUntilIdle()

        assertEquals("Reifen", vehicles.repairs.single().description)
        assertNull(vehicles.repairs.single().cost)
        assertEquals("12km", viewModel.uiState.value.input.cost)
    }

    @Test
    fun saveFailure_setsSaveFailedFlag() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)
        vehicles.failWrites = true

        viewModel.onInputChange { it.copy(description = "Reifen") }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun newRepair_leavingWithoutAnyMeaningfulInput_deletesTheAutoCreatedDraft() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)
        assertEquals(1, vehicles.repairs.size)

        clear(viewModel)

        assertEquals(0, vehicles.repairs.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newRepair_leavingAfterTyping_keepsTheRepairAndFlushesAStillPendingChange() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(description = "Reifen gewechselt") }
        clear(viewModel)

        assertEquals("Reifen gewechselt", vehicles.repairs.single().description)
    }

    @Test
    fun existingRepair_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val vehicles = FakeVehicleRepository()
        val repairId = vehicles.saveRepair(repair(vehicles.currentVehicleId, "Reifen"))
        val viewModel = newViewModel(vehicles, repairId)

        viewModel.onInputChange { it.copy(description = "Bremsen") }
        assertEquals("Reifen", vehicles.repairs.single().description)

        advanceUntilIdle()

        assertEquals("Bremsen", vehicles.repairs.single().description)
    }

    @Test
    fun existingRepair_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val vehicles = FakeVehicleRepository()
        val repairId = vehicles.saveRepair(repair(vehicles.currentVehicleId, "Reifen"))
        val viewModel = newViewModel(vehicles, repairId)

        viewModel.onInputChange { it.copy(description = "Bremsen") }
        clear(viewModel)

        assertEquals("Bremsen", vehicles.repairs.single().description)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf die Reparatur nicht grundlos neu schreiben. */
    @Test
    fun existingRepair_leavingWithoutAnyChange_doesNotRewriteTheRepair() = runTest {
        val vehicles = FakeVehicleRepository()
        val repairId = vehicles.saveRepair(repair(vehicles.currentVehicleId, "Reifen"))
        val viewModel = newViewModel(vehicles, repairId)
        advanceUntilIdle()
        vehicles.saveRepairCount = 0

        clear(viewModel)

        assertEquals(0, vehicles.saveRepairCount)
    }

    @Test
    fun needsLeaveConfirmation_newUnmodifiedDraft_doesNotNeedConfirmation() {
        val state = RepairEditUiState(isNew = true, initialDate = today(), input = RepairInput(date = today()))
        assertFalse(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = RepairEditUiState(
            isNew = true,
            initialDate = today(),
            input = RepairInput(date = today(), description = "Reifen"),
            isDirty = true,
        )
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingRepairWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(RepairEditUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingRepairWithChanges_needsConfirmation() {
        assertTrue(RepairEditUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht eine neue Reparatur auch dann vollständig, wenn sie schon sinnvoll befüllt ist. */
    @Test
    fun newRepair_discardConfirmed_deletesTheRepairEvenWithMeaningfulInput() = runTest {
        val vehicles = FakeVehicleRepository()
        val viewModel = newViewModel(vehicles)

        viewModel.onInputChange { it.copy(description = "Reifen gewechselt") }
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(vehicles.repairs.isEmpty())
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einer bestehenden Reparatur setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück. */
    @Test
    fun existingRepair_discardConfirmed_revertsFieldChangesToTheOpenedState() = runTest {
        val vehicles = FakeVehicleRepository()
        val repairId = vehicles.saveRepair(repair(vehicles.currentVehicleId, "Reifen"))
        val viewModel = newViewModel(vehicles, repairId)

        viewModel.onInputChange { it.copy(description = "Bremsen") }
        advanceUntilIdle()
        assertEquals("Bremsen", vehicles.repairs.single().description)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Reifen", vehicles.repairs.single().description)
    }

    /** [RepairEditViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: RepairEditViewModel) {
        ViewModelStore().apply { put("repair", viewModel) }.clear()
    }

    private fun repair(vehicleId: Long, description: String) = Repair(
        vehicleId = vehicleId,
        date = LocalDate.of(2026, 6, 1),
        description = description,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
