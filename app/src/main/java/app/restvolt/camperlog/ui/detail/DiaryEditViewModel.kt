package app.restvolt.camperlog.ui.detail

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryError
import app.restvolt.camperlog.domain.DiaryEntryField
import app.restvolt.camperlog.domain.DiaryEntryInput
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.defaultDiaryEntryDate
import app.restvolt.camperlog.domain.toDiaryEntry
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Zustand des Tagebuchformulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class DiaryEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: DiaryEntryInput = DiaryEntryInput(),
    val errors: Map<DiaryEntryField, DiaryEntryError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
    /** Der geladene, gespeicherte Eintrag; nötig, um ihn über die Kopfzeile löschen zu können. */
    val original: DiaryEntry? = null,
    /** Daten der übrigen Einträge derselben Tour, für die Prüfung auf Mehrfachbelegung eines Tages. */
    val otherDates: Set<LocalDate> = emptySet(),
)

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class DiaryEditDraft(val input: DiaryEntryInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert einen Tagebucheintrag einer Tour. [entryId] 0 legt einen neuen
 * Eintrag an, dessen Datum sich aus [defaultDiaryEntryDate] ergibt. Geänderte Eingaben liegen
 * zusätzlich in [savedStateHandle] und werden nach einem Neustart des Prozesses statt des
 * gespeicherten Eintrags angezeigt.
 *
 * @param today Bezugstag für die Datumsvorbelegung eines neuen Eintrags
 */
class DiaryEditViewModel(
    private val repository: DiaryEntryRepository,
    private val tours: TourRepository,
    private val tourId: Long,
    entryId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val draft: DiaryEditDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        DiaryEditUiState(isNew = entryId == 0L, isLoading = true).let { state ->
            if (draft != null) state.copy(input = draft.input, isDirty = true) else state
        },
    )
    val uiState: StateFlow<DiaryEditUiState> = _uiState.asStateFlow()

    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            val entries = repository.observeForTour(tourId).first()
            val original = entries.firstOrNull { it.id == entryId }
            if (entryId != 0L && original == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val otherDates = entries.filterNot { it.id == entryId }.mapTo(HashSet(), DiaryEntry::date)
            _uiState.update { state ->
                val input = when {
                    state.isDirty -> state.input
                    original != null -> original.toInput()
                    else -> DiaryEntryInput(date = defaultDate(otherDates))
                }
                state.copy(isLoading = false, original = original, otherDates = otherDates, input = input)
            }
        }
    }

    private suspend fun defaultDate(otherDates: Set<LocalDate>): LocalDate? {
        val tour = tours.observeTour(tourId).first() ?: return null
        return defaultDiaryEntryDate(tour.startDate, tour.endDate, otherDates, today())
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (DiaryEntryInput) -> DiaryEntryInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    /** Validiert und speichert. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val errors = state.input.validate(state.otherDates)
        if (errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update { it.copy(errors = errors, rejectedSaves = it.rejectedSaves + 1) }
            return
        }
        val entry = state.input.toDiaryEntry(state.original, tourId)
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(entry)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Die Fehlermeldung zu [DiaryEditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun DiaryEditUiState.withErrors(): DiaryEditUiState {
        if (!showErrors) return copy(errors = emptyMap())
        return copy(errors = input.validate(otherDates))
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(DiaryEditDraft(input, showErrors))
    }
}

private const val DRAFT_KEY = "diary_entry_draft"
