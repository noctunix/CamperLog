package app.restvolt.camperlog.ui.vehicle

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.LocalDateSerializer
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
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
import java.time.Instant
import java.time.LocalDate

/** Formulareingabe eines Fahrzeugdokuments. */
@Serializable
data class VehicleDocumentInput(
    val kind: DocumentKind = DocumentKind.OTHER,
    val title: String = "",
    @Serializable(with = LocalDateSerializer::class) val expiryDate: LocalDate? = null,
)

/** Zustand des Formulars. Jede Eingabe speichert sich laufend selbst (siehe [VehicleDocumentEditViewModel]). */
data class VehicleDocumentEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: VehicleDocumentInput = VehicleDocumentInput(),
    val titleError: Boolean = false,
    /** Seit dem Öffnen des Formulars wurde mindestens eine Eingabe geändert. */
    val isDirty: Boolean = false,
    /** Der letzte automatische Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Das geladene, gespeicherte Dokument; nötig, um es auf seinen Ausgangsstand zurückzusetzen. */
    val original: VehicleDocument? = null,
    /**
     * Die id dieses Dokuments, sobald bekannt: für ein neues Dokument ab seiner ersten automatischen
     * Anlage, sonst ab dem Laden; `0` bis dahin, siehe [app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection].
     */
    val documentId: Long = 0,
)

/**
 * Ob der Entwurf eines neuen Dokuments noch unverändert der Vorbelegung entspricht - also beim
 * Verlassen ohne Rückfrage verworfen werden darf, statt als leere Karteileiche zu bleiben.
 */
internal fun VehicleDocumentEditUiState.isUnmodifiedNewDocumentDraft(): Boolean = isNew && input == VehicleDocumentInput()

/**
 * Ob ein Verlassen zuerst den Verwerfen/Speichern-Dialog zeigen muss: Für ein neues Dokument,
 * solange sein Entwurf nicht mehr [isUnmodifiedNewDocumentDraft] ist; für ein bestehendes, solange
 * sich seit dem Öffnen des Formulars wirklich etwas geändert hat.
 */
internal fun VehicleDocumentEditUiState.needsLeaveConfirmation(): Boolean = if (isNew) !isUnmodifiedNewDocumentDraft() else isDirty

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class VehicleDocumentDraft(
    val input: VehicleDocumentInput,
    val showErrors: Boolean,
    /** Für ein neues Dokument die id des schon automatisch angelegten Dokuments, falls es sie schon gibt. */
    val persistedDocumentId: Long? = null,
)

/**
 * Lädt, validiert und speichert ein Fahrzeugdokument. [documentId] 0 legt ein neues Dokument an.
 *
 * Jede Eingabe speichert sich nach kurzer Zeit automatisch (implizites Speichern, siehe
 * [attemptPersist]), für ein neues wie für ein bestehendes Dokument. Ein neues Dokument wird dafür
 * sofort als echter Datensatz angelegt, auch mit noch leerem Titel - dadurch lassen sich Dateien von
 * Anfang an anhängen, ohne auf einen expliziten Speichern-Erfolg zu warten. Verlässt der Nutzer das
 * Formular wieder, entscheidet [onCleared] je nach Fall: Ein komplett unberührter Entwurf eines neuen
 * Dokuments wird gelöscht; eine echte Änderung kann der Nutzer vorher über [onDiscardConfirmed]
 * verwerfen - für ein neues Dokument heißt das Löschen (mit seinen Anhängen), für ein bestehendes das
 * Zurücksetzen auf den beim Öffnen geladenen Stand (siehe [openedDocument]).
 *
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart des
 * Prozesses statt des gespeicherten Dokuments angezeigt; für ein neues Dokument sichert das auch die
 * id des schon automatisch angelegten Dokuments gegen einen solchen Neustart ab.
 *
 * @param now liefert den aktuellen Zeitpunkt für Anlage und Änderung
 */
class VehicleDocumentEditViewModel(
    private val repository: VehicleDocumentRepository,
    private val vehicleId: Long,
    documentId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val draft: VehicleDocumentDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        VehicleDocumentEditUiState(isNew = documentId == 0L, isLoading = documentId != 0L, documentId = documentId).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<VehicleDocumentEditUiState> = _uiState.asStateFlow()

    /** Der zuletzt erfolgreich automatisch gespeicherte Stand; Basis für den nächsten [attemptPersist]-Versuch. */
    private var original: VehicleDocument? = null

    /**
     * Für ein bestehendes Dokument der beim Öffnen des Formulars geladene Stand, unverändert über die
     * ganze Bearbeitungssitzung - die Grundlage für [onDiscardConfirmed], falls der Nutzer seine
     * Änderungen verwirft. Bleibt für ein neues Dokument `null`, da sein Verwerfen den Datensatz löscht
     * statt ihn zurückzusetzen.
     */
    private var openedDocument: VehicleDocument? = null
    private var showErrors = draft?.showErrors ?: false

    /** Verhindert, dass zwei gleichzeitige Autosave-Versuche beide ein neues Dokument anlegen. */
    private val persistMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /** Beim nächsten [onCleared] verwerfen statt die letzte Änderung zu übernehmen; siehe [onDiscardConfirmed]. */
    private var discardConfirmed = false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (documentId != 0L) {
            viewModelScope.launch {
                val document = repository.observeForVehicle(vehicleId).first().firstOrNull { it.id == documentId }
                original = document
                openedDocument = document
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else document?.toInput() ?: it.input
                    it.copy(isLoading = false, notFound = document == null, input = input, original = document, documentId = document?.id ?: documentId)
                }
            }
        } else {
            viewModelScope.launch {
                val restoredId = draft?.persistedDocumentId
                if (restoredId != null) {
                    original = repository.observeForVehicle(vehicleId).first().firstOrNull { it.id == restoredId }
                    if (original != null) _uiState.update { it.copy(documentId = restoredId, original = original) }
                }
                if (original == null) attemptPersist()
            }
        }
    }

    /**
     * Übernimmt eine Formularänderung. Es gibt keinen Speichern-Versuch mehr, der Fehler sichtbar
     * machen könnte - stattdessen macht schon die erste Eingabe alle aktuellen Fehler sichtbar, und
     * löst außerdem den debounced Autosave aus.
     */
    fun onInputChange(transform: (VehicleDocumentInput) -> VehicleDocumentInput) {
        showErrors = true
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
        scheduleAutosave()
    }

    /**
     * Versucht die aktuelle Eingabe in die Datenbank zu schreiben: anlegen, falls [original] noch
     * keine gibt (nur für ein neues Dokument), sonst aktualisieren. Ein noch leerer Titel blockiert das
     * nicht - er bleibt als inline angezeigter Pflichtfeld-Fehler stehen, verhindert aber nicht, dass
     * die übrige Eingabe (und damit auch die id für Anhänge) sofort verfügbar ist.
     */
    private suspend fun attemptPersist() {
        persistMutex.withLock {
            val state = _uiState.value
            val basis = original
            val document = VehicleDocument(
                id = basis?.id ?: 0,
                uuid = basis?.uuid.orEmpty(),
                vehicleId = vehicleId,
                kind = state.input.kind,
                title = state.input.title,
                expiryDate = state.input.expiryDate,
                createdAt = basis?.createdAt ?: now(),
                updatedAt = now(),
            )
            try {
                val id = repository.save(document)
                original = repository.observeForVehicle(vehicleId).first().firstOrNull { it.id == id } ?: document.copy(id = id)
                _uiState.update { it.copy(documentId = id, original = original, saveFailed = false) }
                saveDraft()
            } catch (_: SQLException) {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    /** Plant [attemptPersist] mit kurzer Verzögerung, damit schnell aufeinanderfolgende Tastendrücke nur einen Schreibzugriff auslösen. */
    private fun scheduleAutosave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            attemptPersist()
        }
    }

    /** Die Fehlermeldung zu [VehicleDocumentEditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun VehicleDocumentEditUiState.withErrors(): VehicleDocumentEditUiState =
        if (!showErrors) copy(titleError = false) else copy(titleError = input.title.isBlank())

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(VehicleDocumentDraft(input, showErrors, original?.id))
    }

    /**
     * Verwirft die aktuelle Bearbeitungssitzung beim nächsten Verlassen (siehe [onCleared]), statt die
     * letzte Änderung zu übernehmen: Für ein neues Dokument heißt das, den automatisch angelegten
     * Datensatz (mit seinen Anhängen) zu löschen; für ein bestehendes, es auf den beim Öffnen des
     * Formulars geladenen Stand zurückzusetzen (siehe [openedDocument]). Bricht außerdem einen noch
     * wartenden Autosave ab, damit er nicht doch noch zwischen diesem Aufruf und dem tatsächlichen
     * Verlassen feuert.
     */
    fun onDiscardConfirmed() {
        discardConfirmed = true
        pendingSaveJob?.cancel()
    }

    /** Ob [onDiscardConfirmed] für diese Sitzung schon aufgerufen wurde. */
    fun wasDiscarded(): Boolean = discardConfirmed

    /**
     * Beim Verlassen ohne sinnvolle Eingabe (ein neues, komplett unberührtes Dokument) oder nach
     * [onDiscardConfirmed] wird der betroffene Datensatz aufgeräumt; sonst wird eine noch nicht
     * geschriebene letzte Änderung blockierend nachgeholt. `viewModelScope` ist zu diesem Zeitpunkt
     * bereits abgebrochen (siehe `ViewModel.clear()`), daher blockierend statt über ihn.
     */
    override fun onCleared() {
        pendingSaveJob?.cancel()
        val state = _uiState.value
        runBlocking {
            when {
                state.isNew && (discardConfirmed || state.isUnmodifiedNewDocumentDraft()) -> deleteNewDocumentDraft()
                !state.isNew && discardConfirmed -> revertToOpenedDocument()
                state.isDirty -> runCatching { attemptPersist() }
            }
        }
    }

    /** Löscht das automatisch angelegte, noch nicht sinnvoll befüllte bzw. explizit verworfene neue Dokument (mit seinen Anhängen) wieder. */
    private suspend fun deleteNewDocumentDraft() {
        original?.id?.let { id -> runCatching { repository.delete(id) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }

    /** Setzt ein bestehendes Dokument auf [openedDocument] zurück. */
    private suspend fun revertToOpenedDocument() {
        val baseline = openedDocument ?: return
        persistMutex.withLock { runCatching { repository.save(baseline) } }
        savedStateHandle.remove<SavedState>(DRAFT_KEY)
    }
}

private fun VehicleDocument.toInput() = VehicleDocumentInput(kind = kind, title = title, expiryDate = expiryDate)

private const val DRAFT_KEY = "vehicle_document_draft"

/** Verzögerung zwischen einer Formularänderung eines Dokuments und ihrem automatischen Schreibzugriff. */
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
