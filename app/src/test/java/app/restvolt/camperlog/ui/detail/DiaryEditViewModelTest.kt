package app.restvolt.camperlog.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryError
import app.restvolt.camperlog.domain.DiaryEntryField
import app.restvolt.camperlog.domain.DiaryEntryInput
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.FakeDiaryEntryRepository
import app.restvolt.camperlog.ui.FakeTourRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Deckt den Umbau eines Tagebucheintrags auf implizites Speichern ab, analog zu `RepairEditViewModelTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryEditViewModelTest {

    private val today = { LocalDate.of(2026, 7, 1) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        diary: FakeDiaryEntryRepository = FakeDiaryEntryRepository(),
        tours: FakeTourRepository = FakeTourRepository(listOf(tour())),
        tourId: Long = tours.tours.single().id,
        entryId: Long = 0,
    ) = DiaryEditViewModel(
        repository = diary,
        tours = tours,
        tourId = tourId,
        entryId = entryId,
        savedStateHandle = SavedStateHandle(),
        today = today,
    )

    /**
     * Kernregressionstest für den Umbau auf implizites Speichern: Ein neuer Tagebucheintrag hat von
     * Anfang an eine echte, stabile id, ohne jedes explizite Speichern.
     */
    @Test
    fun newEntry_getsARealIdImmediatelyWithoutAnyExplicitSave() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)

        val id = viewModel.uiState.value.original?.id
        assertNotNull(id)
        assertEquals(diary.entries.single().id, id)
    }

    /** Ein noch leerer Pflichttext blockiert das allererste automatische Anlegen nicht. */
    @Test
    fun newEntry_persistsImmediatelyEvenWithAnEmptyText() = runTest {
        val diary = FakeDiaryEntryRepository()
        newViewModel(diary)

        assertEquals("", diary.entries.single().text)
    }

    @Test
    fun newEntry_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)
        val id = viewModel.uiState.value.original?.id

        viewModel.onInputChange { it.copy(text = "Langer Tag am Fjord.") }
        // Der Schreibzugriff ist entprellt; direkt nach der Eingabe steht der alte Stand noch in der DB.
        assertEquals("", diary.entries.single().text)

        advanceUntilIdle()

        assertEquals(id, diary.entries.single().id)
        assertEquals("Langer Tag am Fjord.", diary.entries.single().text)
    }

    /** Eine ungültige Kennzahl blockiert den Schreibzugriff nicht - sie fällt für den gespeicherten Stand einfach weg. */
    @Test
    fun invalidOdometer_isSanitizedToBlankInsteadOfBlockingTheWrite() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)

        viewModel.onInputChange { it.copy(text = "Langer Tag", odometerKm = "abc") }
        advanceUntilIdle()

        assertEquals("Langer Tag", diary.entries.single().text)
        assertNull(diary.entries.single().odometerKm)
        // Im Formular bleibt die ungültige Eingabe sichtbar, nur der gespeicherte Stand ist bereinigt.
        assertEquals("abc", viewModel.uiState.value.input.odometerKm)
    }

    @Test
    fun saveFailure_setsSaveFailedFlag() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)
        diary.failWrites = true

        viewModel.onInputChange { it.copy(text = "Langer Tag") }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun newEntry_leavingWithoutAnyMeaningfulInput_deletesTheAutoCreatedDraft() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)
        assertEquals(1, diary.entries.size)

        clear(viewModel)

        assertEquals(0, diary.entries.size)
    }

    /** Ein noch nicht geschriebener letzter Tastendruck geht beim Verlassen nicht verloren. */
    @Test
    fun newEntry_leavingAfterTyping_keepsTheEntryAndFlushesAStillPendingChange() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)

        viewModel.onInputChange { it.copy(text = "Langer Tag am Fjord.") }
        clear(viewModel)

        assertEquals("Langer Tag am Fjord.", diary.entries.single().text)
    }

    @Test
    fun existingEntry_autosavesFieldChangesAfterTheDebounceWithoutAnyExplicitSave() = runTest {
        val diary = FakeDiaryEntryRepository()
        val tours = FakeTourRepository(listOf(tour()))
        val entryId = diary.save(entry(tours.tours.single().id, LocalDate.of(2026, 7, 2), "Ankunft"))
        val viewModel = newViewModel(diary, tours, tours.tours.single().id, entryId)

        viewModel.onInputChange { it.copy(text = "Weiterfahrt") }
        assertEquals("Ankunft", diary.entries.single().text)

        advanceUntilIdle()

        assertEquals("Weiterfahrt", diary.entries.single().text)
    }

    @Test
    fun existingEntry_leavingAfterTyping_flushesAStillPendingChange() = runTest {
        val diary = FakeDiaryEntryRepository()
        val tours = FakeTourRepository(listOf(tour()))
        val entryId = diary.save(entry(tours.tours.single().id, LocalDate.of(2026, 7, 2), "Ankunft"))
        val viewModel = newViewModel(diary, tours, tours.tours.single().id, entryId)

        viewModel.onInputChange { it.copy(text = "Weiterfahrt") }
        clear(viewModel)

        assertEquals("Weiterfahrt", diary.entries.single().text)
    }

    /** Ein unverändert geöffnetes und wieder verlassenes Formular darf den Eintrag nicht grundlos neu schreiben. */
    @Test
    fun existingEntry_leavingWithoutAnyChange_doesNotRewriteTheEntry() = runTest {
        val diary = FakeDiaryEntryRepository()
        val tours = FakeTourRepository(listOf(tour()))
        val entryId = diary.save(entry(tours.tours.single().id, LocalDate.of(2026, 7, 2), "Ankunft"))
        val viewModel = newViewModel(diary, tours, tours.tours.single().id, entryId)
        advanceUntilIdle()
        diary.saveCount = 0

        clear(viewModel)

        assertEquals(0, diary.saveCount)
    }

    @Test
    fun needsLeaveConfirmation_newUnmodifiedDraft_doesNotNeedConfirmation() {
        val state = DiaryEditUiState(isNew = true, initialDate = today(), input = DiaryEntryInput(date = today()))
        assertFalse(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_newModifiedDraft_needsConfirmation() {
        val state = DiaryEditUiState(
            isNew = true,
            initialDate = today(),
            input = DiaryEntryInput(date = today(), text = "Langer Tag"),
            isDirty = true,
        )
        assertTrue(state.needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingEntryWithoutChanges_doesNotNeedConfirmation() {
        assertFalse(DiaryEditUiState(isNew = false, isDirty = false).needsLeaveConfirmation())
    }

    @Test
    fun needsLeaveConfirmation_existingEntryWithChanges_needsConfirmation() {
        assertTrue(DiaryEditUiState(isNew = false, isDirty = true).needsLeaveConfirmation())
    }

    /** Verwerfen löscht einen neuen Eintrag auch dann vollständig, wenn er schon sinnvoll befüllt ist. */
    @Test
    fun newEntry_discardConfirmed_deletesTheEntryEvenWithMeaningfulInput() = runTest {
        val diary = FakeDiaryEntryRepository()
        val viewModel = newViewModel(diary)

        viewModel.onInputChange { it.copy(text = "Langer Tag am Fjord.") }
        assertTrue(viewModel.uiState.value.needsLeaveConfirmation())
        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertTrue(diary.entries.isEmpty())
        assertTrue(viewModel.wasDiscarded())
    }

    /** Verwerfen bei einem bestehenden Eintrag setzt geänderte Felder auf den beim Öffnen geladenen Stand zurück. */
    @Test
    fun existingEntry_discardConfirmed_revertsFieldChangesToTheOpenedState() = runTest {
        val diary = FakeDiaryEntryRepository()
        val tours = FakeTourRepository(listOf(tour()))
        val entryId = diary.save(entry(tours.tours.single().id, LocalDate.of(2026, 7, 2), "Ankunft"))
        val viewModel = newViewModel(diary, tours, tours.tours.single().id, entryId)

        viewModel.onInputChange { it.copy(text = "Weiterfahrt") }
        advanceUntilIdle()
        assertEquals("Weiterfahrt", diary.entries.single().text)

        viewModel.onDiscardConfirmed()
        clear(viewModel)

        assertEquals("Ankunft", diary.entries.single().text)
    }

    /**
     * Zwei Einträge derselben Tour am selben Tag sind ein echter Konflikt: Der Schreibzugriff bleibt
     * blockiert, bis der Nutzer ein anderes Datum wählt - der automatisch angelegte Entwurf behält
     * dabei sein letztes gültiges Datum.
     */
    @Test
    fun duplicateDate_blocksTheWriteUntilCorrected() = runTest {
        val diary = FakeDiaryEntryRepository()
        val tours = FakeTourRepository(listOf(tour()))
        val takenDate = LocalDate.of(2026, 7, 2)
        diary.save(entry(tours.tours.single().id, takenDate, "Ankunft"))
        val viewModel = newViewModel(diary, tours, tours.tours.single().id)
        val newEntryId = viewModel.uiState.value.original?.id
        assertEquals(today(), diary.entries.first { it.id == newEntryId }.date)

        viewModel.onInputChange { it.copy(date = takenDate) }
        advanceUntilIdle()

        assertEquals(DiaryEntryError.DUPLICATE_DATE, viewModel.uiState.value.errors[DiaryEntryField.DATE])
        assertEquals(today(), diary.entries.first { it.id == newEntryId }.date)

        viewModel.onInputChange { it.copy(date = LocalDate.of(2026, 7, 3)) }
        advanceUntilIdle()

        assertFalse(DiaryEntryField.DATE in viewModel.uiState.value.errors)
        assertEquals(LocalDate.of(2026, 7, 3), diary.entries.first { it.id == newEntryId }.date)
    }

    /** [DiaryEditViewModel.onCleared] ist wie bei jedem `ViewModel` nur intern erreichbar; über den Store löst sich das Pendant zum echten Verlassen aus. */
    private fun clear(viewModel: DiaryEditViewModel) {
        ViewModelStore().apply { put("diary", viewModel) }.clear()
    }

    private fun tour() = Tour(
        id = 1,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 10),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = listOf(Money(8_990, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun entry(tourId: Long, date: LocalDate, text: String) = DiaryEntry(
        tourId = tourId,
        date = date,
        text = text,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
