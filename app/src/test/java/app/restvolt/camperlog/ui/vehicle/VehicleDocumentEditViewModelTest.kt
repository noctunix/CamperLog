package app.restvolt.camperlog.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.ui.FakeVehicleDocumentRepository
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

/** Deckt den Umbau eines Fahrzeugdokuments auf implizites Speichern ab, analog zu `RepairEditViewModelTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleDocumentEditViewModelTest {

    private val vehicleId = 1L
    private val now = { Instant.EPOCH }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(repository: FakeVehicleDocumentRepository = FakeVehicleDocumentRepository(), documentId: Long = 0) =
        VehicleDocumentEditViewModel(
            repository = repository,
            vehicleId = vehicleId,
            documentId = documentId,
            savedStateHandle = SavedStateHandle(),
            now = now,
        )

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Ein neues Dokument hat von Anfang
     * an eine echte, stabile id - Anhänge lassen sich darauf sofort beziehen, ohne auf einen
     * expliziten Speichern-Erfolg zu warten.
     */
    @Test
    fun newDocument_getsARealIdImmediatelyWithoutAnyExplicitSave() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)

        val documentId = viewModel.uiState.value.documentId
        assertEquals(repository.documents.single().id, documentId)
        assertTrue(documentId != 0L)
    }

    /** Ein noch leerer Pflicht-Titel blockiert das allererste automatische Anlegen nicht. */
    @Test
    fun newDocument_persistsImmediatelyEvenWithAnEmptyTitle() = runTest {
        val repository = FakeVehicleDocumentRepository()
        newViewModel(repository)

        assertEquals("", repository.documents.single().title)
    }

    @Test
    fun newDocument_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)
        val id = viewModel.uiState.value.documentId

        viewModel.onInputChange { it.copy(title = "Fahrzeugschein") }
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", repository.documents.single().title)

        advanceUntilIdle()

        assertEquals(id, repository.documents.single().id)
        assertEquals("Fahrzeugschein", repository.documents.single().title)
    }

    @Test
    fun saveFailure_setsSaveFailedFlag() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)
        repository.failWrites = true

        viewModel.onInputChange { it.copy(title = "Fahrzeugschein") }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun newDocument_leavingWithoutAnyMeaningfulInput_deletesTheAutoCreatedDraft() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)
        assertEquals(1, repository.documents.size)

        clear(viewModel)

        assertEquals(0, repository.documents.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newDocument_leavingAfterTyping_keepsTheDocumentAndFlushesAStillPendingChange() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)

        viewModel.onInputChange { it.copy(title = "Fahrzeugschein") }
        clear(viewModel)

        assertEquals("Fahrzeugschein", repository.documents.single().title)
    }

    @Test
    fun existingDocument_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val documentId = repository.save(document(vehicleId, "Fahrzeugschein"))
        val viewModel = newViewModel(repository, documentId)

        viewModel.onInputChange { it.copy(title = "KFZ-Schein") }
        assertEquals("Fahrzeugschein", repository.documents.single().title)

        advanceUntilIdle()

        assertEquals("KFZ-Schein", repository.documents.single().title)
    }

    @Test
    fun existingDocument_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val documentId = repository.save(document(vehicleId, "Fahrzeugschein"))
        val viewModel = newViewModel(repository, documentId)

        viewModel.onInputChange { it.copy(title = "KFZ-Schein") }
        clear(viewModel)

        assertEquals("KFZ-Schein", repository.documents.single().title)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf das Dokument nicht grundlos neu schreiben. */
    @Test
    fun existingDocument_leavingWithoutAnyChange_doesNotRewriteTheDocument() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val documentId = repository.save(document(vehicleId, "Fahrzeugschein"))
        val viewModel = newViewModel(repository, documentId)
        advanceUntilIdle()
        repository.saveCount = 0

        clear(viewModel)

        assertEquals(0, repository.saveCount)
    }

    @Test
    fun needsLeaveConfirmation_newUnmodifiedDraft_doesNotNeedConfirmation() {
        val state = VehicleDocumentEditUiState(isNew = true)
        assertFalse(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = VehicleDocumentEditUiState(isNew = true, input = VehicleDocumentInput(title = "Fahrzeugschein"), isDirty = true)
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingDocumentWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(VehicleDocumentEditUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingDocumentWithChanges_needsConfirmation() {
        assertTrue(VehicleDocumentEditUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht ein neues Dokument auch dann vollständig, wenn es schon sinnvoll befüllt ist. */
    @Test
    fun newDocument_discardConfirmed_deletesTheDocumentEvenWithMeaningfulInput() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val viewModel = newViewModel(repository)

        viewModel.onInputChange { it.copy(title = "Fahrzeugschein") }
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(repository.documents.isEmpty())
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einem bestehenden Dokument setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück. */
    @Test
    fun existingDocument_discardConfirmed_revertsFieldChangesToTheOpenedState() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val documentId = repository.save(document(vehicleId, "Fahrzeugschein"))
        val viewModel = newViewModel(repository, documentId)

        viewModel.onInputChange { it.copy(title = "KFZ-Schein") }
        advanceUntilIdle()
        assertEquals("KFZ-Schein", repository.documents.single().title)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Fahrzeugschein", repository.documents.single().title)
    }

    @Test
    fun loadingAnExistingDocument_fillsTheInputFromTheStoredDocument() = runTest {
        val repository = FakeVehicleDocumentRepository()
        val documentId = repository.save(
            document(vehicleId, "KFZ-Police").copy(kind = DocumentKind.INSURANCE, expiryDate = LocalDate.of(2027, 1, 1)),
        )
        val viewModel = newViewModel(repository, documentId)
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals("KFZ-Police", input.title)
        assertEquals(DocumentKind.INSURANCE, input.kind)
        assertEquals(LocalDate.of(2027, 1, 1), input.expiryDate)
    }

    /** [VehicleDocumentEditViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: VehicleDocumentEditViewModel) {
        ViewModelStore().apply { put("document", viewModel) }.clear()
    }

    private fun document(vehicleId: Long, title: String) = VehicleDocument(
        vehicleId = vehicleId,
        kind = DocumentKind.OTHER,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
