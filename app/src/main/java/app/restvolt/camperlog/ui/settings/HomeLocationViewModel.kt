package app.restvolt.camperlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationPermissionGate
import app.restvolt.camperlog.domain.LocationProvider
import kotlinx.coroutines.launch

/**
 * GPS-Erfassung für die Zuhause-Koordinate in den Einstellungen; übernimmt einen gefundenen Fix
 * direkt in [settings], analog zum Stationsformular ([app.restvolt.camperlog.ui.edit.EditStationViewModel]).
 */
class HomeLocationViewModel(
    private val settings: HomeLocationSettings,
    locationProvider: LocationProvider,
    locationPermissionGate: LocationPermissionGate,
) : ViewModel() {

    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)

    init {
        viewModelScope.launch {
            locationCapture.state.collect { captureState ->
                if (captureState is LocationCaptureState.Found) {
                    settings.setLocation(captureState.fix.latitude, captureState.fix.longitude, CoordinateSource.GPS)
                    locationCapture.clear()
                }
            }
        }
    }
}
