package app.restvolt.camperlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationPermissionGate
import app.restvolt.camperlog.domain.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Verwaltet die Historie der Zuhause-Adressen in den Einstellungen, inklusive der GPS-Erfassung
 * für genau einen Eintrag zur Zeit ([startCapture]/[capturingForId]); ein gefundener Fix übernimmt
 * sich direkt in diesen Eintrag, analog zum Stationsformular ([app.restvolt.camperlog.ui.edit.EditStationViewModel]).
 */
class HomeLocationViewModel(
    private val repository: HomeLocationRepository,
    locationProvider: LocationProvider,
    locationPermissionGate: LocationPermissionGate,
) : ViewModel() {

    // Eagerly statt WhileSubscribed: die GPS-Übernahme in init liest unten .value auch dann noch
    // korrekt, wenn die Oberfläche währenddessen kurz die Sammlung verlässt (z. B. Systemdialog).
    val locations: StateFlow<List<HomeLocation>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)

    private val _capturingForId = MutableStateFlow<Long?>(null)

    /** id des Eintrags, für den gerade eine GPS-Suche läuft; `null` außerhalb einer solchen Suche. */
    val capturingForId: StateFlow<Long?> = _capturingForId.asStateFlow()

    init {
        viewModelScope.launch {
            locationCapture.state.collect { captureState ->
                if (captureState is LocationCaptureState.Found) {
                    val id = _capturingForId.value
                    val current = id?.let { targetId -> locations.value.firstOrNull { it.id == targetId } }
                    if (current != null) {
                        save(current.copy(latitude = captureState.fix.latitude, longitude = captureState.fix.longitude, coordinateSource = CoordinateSource.GPS))
                    }
                    _capturingForId.value = null
                    locationCapture.clear()
                }
            }
        }
    }

    /** Merkt sich [id] als Ziel der nächsten GPS-Suche; der eigentliche Tastendruck läuft weiter über [locationCapture]. */
    fun startCapture(id: Long) {
        _capturingForId.value = id
    }

    fun save(location: HomeLocation) {
        viewModelScope.launch { repository.save(location) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Legt sofort einen neuen, leeren Eintrag an, den der Nutzer dann befüllt. */
    fun addNew() {
        save(HomeLocation())
    }
}
