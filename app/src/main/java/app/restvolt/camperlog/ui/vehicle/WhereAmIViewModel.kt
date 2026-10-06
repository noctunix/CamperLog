package app.restvolt.camperlog.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationPermissionGate
import app.restvolt.camperlog.domain.LocationProvider

/**
 * "Wo bin ich?" in der Panne-&-Unfall-Karte (6.12, 13.5 Nr. 1): derselbe einmalige GPS-Fix wie im
 * Stationsformular, hier aber nur zum Anzeigen, Kopieren und Teilen statt zum Speichern.
 */
class WhereAmIViewModel(locationProvider: LocationProvider, locationPermissionGate: LocationPermissionGate) : ViewModel() {
    val locationCapture = LocationCaptureController(locationProvider, locationPermissionGate, viewModelScope)
}
