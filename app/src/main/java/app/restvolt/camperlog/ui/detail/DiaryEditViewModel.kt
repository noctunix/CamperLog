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
import app.restvolt.camperlog.domain.LocalDateSerializer
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.defaultDiaryEntryDate
import app.restvolt.camperlog.domain.toDiaryEntry
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.validate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [DiaryEditViewModel]). */
data class DiaryEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: DiaryEntryInput = DiaryEntryInput(),
    val errors: Map<DiaryEntryField, DiaryEntryError> = emptyMap(),
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Der geladene, gespeicherte Eintrag; nötig, um ihn über die Kopfzeile löschen zu können. */
    val original: DiaryEntry? = null,
    /** Daten der übrigen Einträge derselben Tour, für die Prüfung auf Mehrfachbelegung eines Tages. */
    val otherDates: Set<LocalDate> = emptySet(),
    /** Vorbelegtes Datum beim Öffnen eines neuen Eintrags; Basis für [isUnmodifiedNewDiaryEntryDraft]. */
    val initialDate: LocalDate? = null,
)

/**
 * Ob der Entwurf eines neuen Tagebucheintrags noch unverändert der Vorbelegung entspricht - also
 * beim Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun DiaryEditUiState.isUnmodifiedNewDiaryEntryDraft(): Boolean = isNew &&
    input.date == initialDate &&
    input.time == null &&
    input.text.isBlank() &&
    input.odometerKm.isBlank() &&
    input.weather.isBlank()

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für einen neuen Eintrag,
 * solange sein Entwurf nicht mehr [isUnmodifiedNewDiaryEntryDraft] ist; für einen bestehenden, solange
 * sich seit dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun DiaryEditUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewDiaryEntryDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class DiaryEditDraft(
    val input: DiaryEntryInput,
    val showErrors: Boolean,
    /** Für einen neuen Eintrag die id des schon automatisch angelegten Eintrags, falls es ihn schon gibt. */
    val persistedEntryId: Long? = null,
    @Serializable(with = LocalDateSerializer::class) val initialDate: LocalDate? = null,
)

/**
 * Lädt, validiert und speichert einen Tagebucheintrag einer Tour. [entryId] 0 legt einen neuen
 * Eintrag an, dessen Datum sich aus [defaultDiaryEntryDate] ergibt.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für einen neuen wie für einen bestehenden Eintrag. Ein neuer Eintrag wird dafür
 * als echter Datensatz angelegt, sobald sein vorbelegtes Datum feststeht - das lädt asynchron die
 * Tour (siehe [defaultDate]), kann also kurz unbekannt sein. Verlässt der Nutzer das Formular wieder,
 * entscheidet [onCleared] je nach Fall: Ein komplett unberührter Entwurf eines neuen Eintrags wird
 * gelöscht; eine echte Änderung kann der Nutzer vorher über [onDiscardConfirmed] verwerfen - für
 * einen neuen Eintrag heißt das Löschen, für einen bestehenden das Zurücksetzen auf den beim Öffnen
 * geladenen Stand (siehe [openedEntry]).
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt des gespeicherten Eintrags angezeigt; für einen neuen Eintrag sichert das auch die
 * id des schon automatisch angelegten Eintrags gegen einen solchen Neustart ab.
 *
 * @param today Bezugstag für die Vorbelegung des Datums eines neuen Eintrags
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
        DiaryEditUiState(isNew = entryId == 0L, isLoading = entryId != 0L, initialDate = draft?.initialDate).let { state ->
            if (draft != null) state.copy(input = draft.input, isDirty = true) else state
        },
    )
    val uiState: StateFlow<DiaryEditUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: DiaryEntry? = null

    /**
     * Für einen bestehenden Eintrag der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für einen neuen Eintrag `null`, da ihr Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedEntry: DiaryEntry? = null
    private var showErrors = draft?.showErrors ?: false

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide einen neuen Eintrag anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            val entries = repository.observeForTour(tourId).first()
            val existing = entries.firstOrNull { it.id == entryId }
            if (entryId != 0L && existing == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            if (entryId != 0L) {
                original = existing
                openedEntry = existing
                val otherDates = entries.filterNot { it.id == entryId }.mapTo(HashSet(), DiaryEntry::date)
                _uiState.update { state ->
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (state.isDirty) state.input else existing?.toInput() ?: state.input
                    state.copy(isLoading = false, original = existing, otherDates = otherDates, input = input)
                }
                return@launch
            }
            val restoredId = draft?.persistedEntryId
            if (restoredId != null) {
                original = entries.firstOrNull { it.id == restoredId }
                if (original != null) _uiState.update { it.copy(original = original) }
            }
            val otherDates = entries.filterNot { it.id == (original?.id ?: 0L) }.mapTo(HashSet(), DiaryEntry::date)
            _uiState.update { it.copy(otherDates = otherDates) }
            if (original == null) {
                val resolvedInitialDate = defaultDate(otherDates)
                _uiState.update { state ->
                    // Nur vorbelegen, solange der Nutzer noch kein eigenes Datum gewählt hat.
                    val input = if (state.isDirty) state.input else state.input.copy(date = resolvedInitialDate)
                    state.copy(initialDate = resolvedInitialDate, input = input)
                }
                attemptPersist()
            }
        }
    }

    /** Vorbelegtes Datum, abhängig vom Zeitraum der Tour; `null`, falls diese (Randfall) nicht mehr existiert. */
    private suspend fun defaultDate(otherDates: Set<LocalDate>): LocalDate? {
        val tour = tours.observeTour(tourId).first() ?: return null
        return defaultDiaryEntryDate(tour.startDate, tour.endDate, otherDates, today())
    }

    /**
     * Übernimmt eine Formularänderung. Es gibt keinen Speichern-Versuch mehr, der Fehler sichtbar
     * machen könnte - stattdessen macht schon die erste Eingabe alle aktuellen Fehler sichtbar, und
     * löst außerdem den debounced Autosave aus.
     */
    fun onInputChange(transform: (DiaryEntryInput) -> DiaryEntryInput) {
        showErrors = true
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
        scheduleAutosave()
    }

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für einen neuen Eintrag), sonst aktualisieren. Ein noch leerer Text blockiert
     * das nicht - er bleibt als inline angezeigter Pflichtfeld-Fehler stehen, verhindert aber nicht,
     * dass die übrige Eingabe sofort gespeichert wird. Eine ungültige Kennzahl wird stattdessen
     * entschärft, siehe [sanitizedForAutosave]. Zwei Einträge derselben Tour am selben Tag sind dagegen
     * ein echter, nicht automatisch lösbarer Konflikt - der Schreibzugriff bleibt blockiert, bis der
     * Nutzer ein anderes Datum wählt, der Fehler bleibt dabei weiterhin inline sichtbar.
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val basis = original
            val date = state.input.date ?: basis?.date ?: today()
            if (date in state.otherDates) return@withLock
            val effectiveInput = state.input.copy(date = date).sanitizedForAutosave(state.otherDates)
            val entry = effectiveInput.toDiaryEntry(basis, tourId)
            try {
                val id = repository.save(entry)
                original = repository.observeForTour(tourId).first().firstOrNull { it.id == id } ?: entry.copy(id = id)
                _uiState.update { it.copy(original = original, saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /**
     * Entschärft eine Eingabe für [attemptPersist]: Eine ungültige Kennzahl fällt weg, statt den
     * Schreibzugriff für den ganzen Eintrag zu blockieren - der Rest der Eingabe (z. B. der Text)
     * speichert sich weiter, während das betroffene Feld inline seinen Fehler zeigt. Das verändert nur
     * den gespeicherten Stand, nicht die Anzeige im Formular.
     */
    private fun DiaryEntryInput.sanitizedForAutosave(otherDates: Set<LocalDate>): DiaryEntryInput {
        val validation = validate(otherDates)
        return if (DiaryEntryField.ODOMETER_KM in validation) copy(odometerKm = "") else this
    }

    /** Plant [attemptPersist] mit kurzer Verzögerung, damit schnell aufeinanderfolgende Tastendrücke nur einen Schreibzugriff auslösen. */
    private fun scheduleAutosave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            attemptPersist()
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
        val state = _uiState.value
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(DiaryEditDraft(state.input, showErrors, original?.id, state.initialDate))
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für einen neuen Eintrag heißt das, den automatisch angelegten
     * Datensatz zu löschen; für einen bestehenden, ihn auf den beim Öffnen des Formulars geladenen
     * Stand zurückzusetzen (siehe [openedEntry]). Bricht außerdem einen noch wartenden Autosave ab,
     * damit er nicht doch noch zwischen diesem Aufruf und dem tatsächlichen Verlassen feuert.
     */
    fun onDiscardConfirmed() {
        discardConfirmed = true
        pendingSaveJob?.cancel()
    }

    /** Ob [onDiscardConfirmed] für diese Sitzung schon aufgerufen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /**
     * Beim Verlassen ohne sinnvolle Eingabe (ein neuer, komplett unberührter Eintrag) oder nach
     * [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn.
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && (discardConfirmed || state.isUnmodifiedNewDiaryEntryDraft()) -> deleteNewEntryDraft()
                !state.isNew && discardConfirmed -> revertToOpenedEntry()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /** Löscht den automatisch angelegten, noch nicht sinnvoll befüllten bzw. explizit verworfenen neuen Eintrag wieder. */
    private suspend fun deleteNewEntryDraft() {
        original?.id?.let { id -> runCatching { repository.delete(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Setzt einen bestehenden Eintrag auf [openedEntry] zurück. */
    private suspend fun revertToOpenedEntry() {
        val baseline = openedEntry ?: return
        persistMutex.withLock { runCatching { repository.save(baseline) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }
}

private const val DRAFT_KEY = "diary_entry_draft"

/** Verzögerung zwischen einer Formularänderung eines Tagebucheintrags und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
