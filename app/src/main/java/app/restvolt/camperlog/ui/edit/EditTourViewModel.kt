package app.restvolt.camperlog.ui.edit

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.CostInput
import app.restvolt.camperlog.domain.QUICK_CURRENCIES
import app.restvolt.camperlog.domain.RunningTourAlreadyExistsException
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourInput
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.derivedMetrics
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toTour
import app.restvolt.camperlog.domain.travelDaysBetween
import app.restvolt.camperlog.domain.validation
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Zustand des Formulars. Fehler werden erst nach dem ersten Speicherversuch angezeigt. */
data class EditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val input: TourInput = TourInput(),
    /** Fahrzeuge für das Auswahlfeld; die Oberfläche zeigt es nur ab zwei Einträgen. */
    val vehicles: List<Vehicle> = emptyList(),
    val errors: Map<TourField, TourError> = emptyMap(),
    /** Fehler je Kostenzeile, Schlüssel ist der Index in [TourInput.costs]. */
    val costErrors: Map<Int, TourError> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Der letzte Speicherversuch ist an der Datenbank gescheitert; Meldung steht noch aus. */
    val saveFailed: Boolean = false,
    /** Speichern würde eine zweite laufende Tour für dasselbe Fahrzeug erzeugen. */
    val runningTourConflict: Boolean = false,
    /** Nur bestehende Altdaten mit bereits gespeichertem Link dürfen diesen noch bearbeiten. */
    val showLegacyMapLink: Boolean = false,
    /** Zählt an der Validierung gescheiterte Speicherversuche; jede Erhöhung fokussiert das erste fehlerhafte Feld. */
    val rejectedSaves: Int = 0,
)

/** Ungespeicherte Formulareingaben, die ein Beenden des Prozesses im Hintergrund überstehen. */
@Serializable
internal data class TourDraft(
    val input: TourInput,
    val showErrors: Boolean,
    val autoTravelDays: String?,
)

/**
 * Lädt, validiert und speichert eine Tour. [tourId] 0 legt eine neue Tour an.
 * Geänderte Eingaben liegen zusätzlich in [savedStateHandle] und werden nach einem Neustart
 * des Prozesses statt der gespeicherten Tour angezeigt.
 *
 * @param stations zieht die Stationen der Tour mit, wenn sie das Fahrzeug wechselt
 * @param checklists zieht die Checklisten der Tour mit, wenn sie das Fahrzeug wechselt
 * @param locale liefert die aktuelle Sprache für Beträge; wird bei jedem Zugriff neu gelesen,
 *   damit ein Sprachwechsel bei laufendem ViewModel greift
 */
class EditTourViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val checklists: ChecklistRepository,
    tourId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val locale: () -> Locale = { app.restvolt.camperlog.domain.supportedLocale(Locale.getDefault()) },
    private val tracks: TrackRepository? = null,
) : ViewModel() {

    private val draft: TourDraft? = savedStateHandle.get<SavedState>(DRAFT_KEY)?.let { decodeFromSavedState(it) }

    private val _uiState = MutableStateFlow(
        EditUiState(isNew = tourId == 0L, isLoading = tourId != 0L).let { state ->
            if (draft == null) state else state.copy(input = draft.input, isDirty = true)
        },
    )
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var original: Tour? = null
    private var showErrors = draft?.showErrors ?: false
    private var autoTravelDays: String? = draft?.autoTravelDays

    init {
        if (showErrors) _uiState.update { it.withErrors() }
        viewModelScope.launch {
            vehicles.observeVehicles().collect { list -> _uiState.update { it.copy(vehicles = list) } }
        }
        if (tourId == 0L) {
            viewModelScope.launch {
                val currency = repository.lastUsedCurrency() ?: return@launch
                // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                _uiState.update {
                    if (it.isDirty) it else it.copy(input = it.input.copy(costs = listOf(CostInput(currency = currency))))
                }
            }
            viewModelScope.launch {
                val currentVehicleId = vehicles.observeCurrentVehicle().first().id
                // Nur vorbelegen, solange der Nutzer noch nichts eingegeben hat.
                _uiState.update {
                    if (it.isDirty) it else it.copy(input = it.input.copy(vehicleId = currentVehicleId))
                }
            }
        } else {
            viewModelScope.launch {
                val tour = repository.observeTour(tourId).first()
                original = tour
                _uiState.update {
                    // Ein wiederhergestellter Entwurf hat Vorrang vor dem gespeicherten Stand.
                    val input = if (it.isDirty) it.input else tour?.toInput(locale()) ?: it.input
                    it.copy(
                        isLoading = false,
                        notFound = tour == null,
                        input = input,
                        showLegacyMapLink = tour?.mapLink != null,
                    )
                }
            }
        }
    }

    /** Übernimmt eine Formularänderung. */
    fun onInputChange(transform: (TourInput) -> TourInput) {
        _uiState.update { state ->
            state.copy(input = transform(state.input), isDirty = true).withErrors()
        }
        saveDraft()
    }

    fun onCostAmountChange(index: Int, amount: String) = onCostChange(index) { it.copy(amount = amount) }

    fun onCostCurrencyChange(index: Int, currency: Currency) = onCostChange(index) { it.copy(currency = currency) }

    /** Hängt eine Kostenzeile mit der ersten noch freien Währung an, bevorzugt aus [QUICK_CURRENCIES]. */
    fun onAddCost() = onInputChange { input ->
        val used = input.costs.map(CostInput::currency).toSet()
        val next = (QUICK_CURRENCIES + ALL_CURRENCIES).firstOrNull { it !in used } ?: return@onInputChange input
        input.copy(costs = input.costs + CostInput(currency = next))
    }

    /** Entfernt eine Kostenzeile; die letzte bleibt stehen und wird nur geleert. */
    fun onRemoveCost(index: Int) = onInputChange { input ->
        val costs = input.costs.filterIndexed { i, _ -> i != index }
        input.copy(costs = costs.ifEmpty { listOf(CostInput(currency = input.costs[index].currency)) })
    }

    private fun onCostChange(index: Int, transform: (CostInput) -> CostInput) = onInputChange { input ->
        input.copy(costs = input.costs.mapIndexed { i, cost -> if (i == index) transform(cost) else cost })
    }

    fun onVehicleChange(vehicleId: Long) = onInputChange { it.copy(vehicleId = vehicleId) }

    fun onStartDateChange(date: LocalDate) {
        onInputChange { prefillTravelDays(it.copy(startDate = date)) }
        refreshDerivedMetrics()
    }

    fun onEndDateChange(date: LocalDate?) {
        onInputChange { prefillTravelDays(it.copy(endDate = date)) }
        if (date != null) refreshDerivedMetrics()
    }

    /** Validiert und speichert; bei Erfolg wird [EditUiState.isSaved] gesetzt. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val locale = locale()
        val validation = state.input.validation(locale)
        if (validation.errors.isNotEmpty()) {
            showErrors = true
            saveDraft()
            _uiState.update {
                it.copy(errors = validation.errors, costErrors = validation.costErrors, rejectedSaves = it.rejectedSaves + 1)
            }
            return
        }
        val tour = state.input.toTour(original, locale)
        _uiState.update {
            it.copy(
                isSaving = true,
                errors = emptyMap(),
                costErrors = emptyMap(),
                saveFailed = false,
                runningTourConflict = false,
            )
        }
        viewModelScope.launch {
            try {
                val id = repository.save(tour)
                val previousVehicleId = original?.vehicleId
                if (previousVehicleId != null && previousVehicleId != tour.vehicleId) {
                    stations.moveTourToVehicle(id, tour.vehicleId)
                    checklists.moveTourToVehicle(id, tour.vehicleId)
                }
                savedStateHandle.remove<SavedState>(DRAFT_KEY)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: RunningTourAlreadyExistsException) {
                _uiState.update { it.copy(isSaving = false, runningTourConflict = true) }
            } catch (_: SQLException) {
                // Eingaben bleiben erhalten, damit der Nutzer es erneut versuchen kann.
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Die Fehlermeldung zu [EditUiState.saveFailed] wurde angezeigt. */
    fun onSaveFailureShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    fun onRunningTourConflictShown() {
        _uiState.update { it.copy(runningTourConflict = false) }
    }

    private fun EditUiState.withErrors(): EditUiState {
        if (!showErrors) return copy(errors = emptyMap(), costErrors = emptyMap())
        val validation = input.validation(locale())
        return copy(errors = validation.errors, costErrors = validation.costErrors)
    }

    private fun saveDraft() {
        val input = _uiState.value.input
        savedStateHandle[DRAFT_KEY] = encodeToSavedState(TourDraft(input, showErrors, autoTravelDays))
    }

    /** Aktualisiert nach einer Datumswahl alle automatisch ableitbaren Kennzahlen. */
    private fun refreshDerivedMetrics() {
        viewModelScope.launch {
            val snapshot = _uiState.value.input
            val start = snapshot.startDate ?: return@launch
            val end = snapshot.endDate ?: return@launch
            if (end < start) return@launch
            val stationList = stations.observeForTour(original?.id ?: 0L).first()
            val trackPoints = tracks?.observeForTour(original?.id ?: 0L)?.first().orEmpty()
            val basis = original?.copy(startDate = start, endDate = end) ?: Tour(
                vehicleId = snapshot.vehicleId,
                startDate = start,
                endDate = end,
                destination = snapshot.destination,
                tourType = snapshot.tourType,
                travelDays = 0,
                overnightStays = 0,
                distanceKm = 0,
                costs = emptyList(),
                notes = "",
                mapLink = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val metrics = basis.derivedMetrics(stationList, trackPoints, end)
            if (_uiState.value.input.startDate != start || _uiState.value.input.endDate != end) return@launch
            autoTravelDays = metrics.travelDays.toString()
            onInputChange {
                it.copy(
                    travelDays = metrics.travelDays.toString(),
                    overnightStays = metrics.overnightStays.toString(),
                    distanceKm = metrics.distanceKm.toString(),
                )
            }
        }
    }

    /**
     * Füllt die Reisetage aus dem Zeitraum vor, solange der Nutzer sie nicht selbst geändert hat.
     */
    private fun prefillTravelDays(input: TourInput): TourInput {
        val start = input.startDate ?: return input
        val end = input.endDate ?: return input
        if (end < start) return input
        val untouched = input.travelDays.isBlank() || input.travelDays == autoTravelDays
        if (!untouched) return input
        val days = travelDaysBetween(start, end).toString()
        autoTravelDays = days
        return input.copy(travelDays = days)
    }
}

private const val DRAFT_KEY = "draft"
