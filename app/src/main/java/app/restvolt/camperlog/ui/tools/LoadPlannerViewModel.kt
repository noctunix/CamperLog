package app.restvolt.camperlog.ui.tools

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.LoadForecast
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadPlanRepository
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.forecastLoad
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zustand des Beladungsplaners: Fahrzeug, sein vollständiger Plan, das gewählte Profil und dessen
 * Hochrechnung. [vehicle] ist `null`, solange das Fahrzeug (noch) nicht geladen ist.
 */
data class LoadPlannerUiState(
    val vehicle: Vehicle? = null,
    val plan: LoadPlan = LoadPlan(vehicleId = 0),
    val selectedProfileId: Long = 0,
    val forecast: LoadForecast? = null,
)

/** Rückmeldungen, die der Beladungsplaner als Snackbar anzeigt. */
sealed interface LoadPlannerMessage {
    data class ItemDeleted(val item: LoadItem) : LoadPlannerMessage
}

/**
 * Hält den Beladungsplan des betrachteten Fahrzeugs aktuell. Ohne [requestedVehicleId] gilt das
 * aktuell gewählte Fahrzeug; mit einer id genau dieses Fahrzeug, unabhängig vom Fahrzeugwechsler.
 */
class LoadPlannerViewModel(
    private val vehicles: VehicleRepository,
    private val loadPlans: LoadPlanRepository,
    private val requestedVehicleId: Long?,
) : ViewModel() {

    private val selectedProfileByVehicle = MutableStateFlow<Map<Long, Long>>(emptyMap())

    private val vehicleFlow: Flow<Vehicle?> =
        requestedVehicleId?.let { vehicles.observeVehicle(it) } ?: vehicles.observeCurrentVehicle()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LoadPlannerUiState> = vehicleFlow.flatMapLatest { vehicle ->
        if (vehicle == null) {
            flowOf(LoadPlannerUiState())
        } else {
            combine(loadPlans.observePlan(vehicle.id), selectedProfileByVehicle) { plan, selectedMap ->
                val selectedProfileId = selectedMap[vehicle.id]?.takeIf { id -> plan.profiles.any { it.id == id } }
                    ?: plan.profiles.firstOrNull()?.id
                    ?: 0
                val forecast = if (selectedProfileId != 0L) forecastLoad(vehicle, plan, selectedProfileId) else null
                LoadPlannerUiState(vehicle, plan, selectedProfileId, forecast)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadPlannerUiState())

    private val _message = MutableStateFlow<LoadPlannerMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<LoadPlannerMessage?> = _message.asStateFlow()

    /** Legt beim ersten Öffnen ein Profil mit [defaultName] an, falls das Fahrzeug [vehicleId] noch keines hat. */
    fun ensureProfile(vehicleId: Long, defaultName: String) {
        viewModelScope.launch { loadPlans.ensureProfile(vehicleId, defaultName) }
    }

    /** Wählt das Profil [profileId] des Fahrzeugs [vehicleId] für die Anzeige aus. */
    fun selectProfile(vehicleId: Long, profileId: Long) {
        selectedProfileByVehicle.value = selectedProfileByVehicle.value + (vehicleId to profileId)
    }

    /** Legt ein neues Profil [name] für [vehicleId] an und wählt es aus. */
    fun addProfile(vehicleId: Long, name: String) {
        viewModelScope.launch {
            val id = loadPlans.saveProfile(LoadProfile(vehicleId = vehicleId, name = name))
            selectProfile(vehicleId, id)
        }
    }

    /** Benennt [profile] in [newName] um. */
    fun renameProfile(profile: LoadProfile, newName: String) {
        viewModelScope.launch { loadPlans.saveProfile(profile.copy(name = newName)) }
    }

    /** Dupliziert [profile] unter [newName] und wählt die Kopie aus. */
    fun duplicateProfile(profile: LoadProfile, newName: String) {
        viewModelScope.launch {
            val id = loadPlans.duplicateProfile(profile.id, newName)
            selectProfile(profile.vehicleId, id)
        }
    }

    /**
     * Löscht [profile], sofern es nicht das letzte Profil des Fahrzeugs ist; wählt danach ein
     * verbleibendes Profil aus.
     */
    fun deleteProfile(profile: LoadProfile) {
        val remaining = uiState.value.plan.profiles.filterNot { it.id == profile.id }
        if (remaining.isEmpty()) return
        viewModelScope.launch {
            loadPlans.deleteProfile(profile.id)
            selectProfile(profile.vehicleId, remaining.first().id)
        }
    }

    /** Setzt, ob [item] im Profil [profileId] aktiv ist. */
    fun setItemActive(profileId: Long, item: LoadItem, active: Boolean) {
        viewModelScope.launch { loadPlans.setItemActive(profileId, item.id, active) }
    }

    /** Setzt den Füllstand der Tankart [kind] im Profil [profileId] in dl. */
    fun setTankLevel(profileId: Long, kind: LoadTankKind, levelDl: Int) {
        viewModelScope.launch { loadPlans.setTankLevel(profileId, kind, levelDl) }
    }

    /** Legt die Einstellungen [settings] eines Tanks an oder ersetzt sie. */
    fun saveTankSettings(settings: LoadTankSettings) {
        viewModelScope.launch { loadPlans.saveTankSettings(settings) }
    }

    /**
     * Legt [item] an oder aktualisiert ihn. Ein neu angelegter, bei der Wiegung nicht vorhandener
     * Gegenstand wird dabei im Profil [currentProfileId] aktiviert, damit er sofort sichtbar wird.
     */
    fun saveItem(item: LoadItem, currentProfileId: Long) {
        viewModelScope.launch {
            val isNew = item.id == 0L
            val id = loadPlans.saveItem(item)
            if (isNew && !item.inWeighing && currentProfileId != 0L) {
                loadPlans.setItemActive(currentProfileId, id, true)
            }
        }
    }

    /** Löscht [item] und bietet über [LoadPlannerMessage.ItemDeleted] das Rückgängigmachen an. */
    fun deleteItem(item: LoadItem) {
        viewModelScope.launch {
            try {
                loadPlans.deleteItem(item.id)
                _message.value = LoadPlannerMessage.ItemDeleted(item)
            } catch (_: SQLException) {
                // Ein Löschfehler bleibt ohne Rückmeldung sichtbar: der Gegenstand steht weiter in der Liste.
            }
        }
    }

    /** Stellt einen über [deleteItem] entfernten Gegenstand unverändert wieder her. */
    fun restoreItem(item: LoadItem) {
        viewModelScope.launch { loadPlans.restoreItem(item) }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: LoadPlannerMessage) {
        _message.compareAndSet(shown, null)
    }
}
