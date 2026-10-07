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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/** Zustand des Dokumentformulars. Der Titel-Fehler erscheint erst nach dem ersten Speicherversuch. */
data class VehicleDocumentEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: VehicleDocumentInput = VehicleDocumentInput(),
    val titleError: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** die gespeicherte id, sobald [isSaved]: der Anhangs-Streifen braucht sie, um Dateien anzuhängen. */
    val savedDocumentId: Long = 0,
    val saveFailed: Boolean = false,
)

@Serializable
internal data class VehicleDocumentDraft(val input: VehicleDocumentInput, val showErrors: Boolean)

/**
 * Lädt, validiert und speichert ein Fahrzeugdokument. [documentId] 0 legt ein neues Dokument an;
 * seine Dateien lassen sich erst nach dem ersten Speichern anhängen (siehe
 * [app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection]) - die Navigation (`Navigation.kt`)
 * führt nach dem ersten Speichern eines neuen Dokuments daher zu dessen Detailseite statt zurück zum
 * Datenblatt, damit sich dort gleich Dateien anhängen lassen, ohne einen Zwischenzustand im Formular
 * selbst zu brauchen ([VehicleDocumentEditUiState.savedDocumentId] trägt die dafür nötige id).
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
        VehicleDocumentEditUiState(isNew = documentId == 0L, isLoading = documentId != 0L, savedDocumentId = documentId).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<VehicleDocumentEditUiState> = _uiState.asStateFlow()

    private var original: VehicleDocument? = null
    private var showErrors = draft?.showErrors ?: false

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        if (documentId != 0L) {
            viewModelScope.launch {
                val document = repository.observeForVehicle(vehicleId).first().firstOrNull { it.id == documentId }
                original = document
                _uiState.update {
                    val input = if (it.isDirty) it.input else document?.toInput() ?: it.input
                    it.copy(isLoading = false, notFound = document == null, input = input)
                }
            }
        }
    }

    fun onInputChange(transform: (VehicleDocumentInput) -> VehicleDocumentInput) {
        _uiState.update { state -> state.copy(input = transform(state.input), isDirty = true).withErrors() }
        saveDraft()
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        if (state.input.title.isBlank()) {
            showErrors = true
            saveDraft()
            _uiState.update { it.copy(titleError = true) }
            return
        }
        val document = VehicleDocument(
            id = state.savedDocumentId,
            uuid = original?.uuid.orEmpty(),
            vehicleId = vehicleId,
            kind = state.input.kind,
            title = state.input.title,
            expiryDate = state.input.expiryDate,
            createdAt = original?.createdAt ?: now(),
            updatedAt = now(),
        )
        _uiState.update { it.copy(isSaving = true, titleError = false, saveFailed = false) }
        viewModelScope.launch {
            try {
                val id = repository.save(document)
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true, savedDocumentId = id) }
            } catch (_: SQLException) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    private fun VehicleDocumentEditUiState.withErrors(): VehicleDocumentEditUiState =
        if (!showErrors) copy(titleError = false) else copy(titleError = input.title.isBlank())

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(VehicleDocumentDraft(input, showErrors))
    }
}

private fun VehicleDocument.toInput() = VehicleDocumentInput(kind = kind, title = title, expiryDate = expiryDate)

private const val DRAFT_KEY = "vehicle_document_draft"
