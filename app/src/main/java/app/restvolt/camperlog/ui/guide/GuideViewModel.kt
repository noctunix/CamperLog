package app.restvolt.camperlog.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.guide.DemoTourSession
import app.restvolt.camperlog.domain.guide.GuideController
import kotlinx.coroutines.launch

/**
 * Hält den [GuideController] Activity-gebunden, damit der Fortschritt einer geführten Tour eine
 * Bildschirmdrehung oder einen anderen Konfigurationswechsel überlebt (vorher per `remember` in der
 * Navigation erzeugt, dort bei jeder Neuzusammensetzung verloren). Räumt beim Start einmalig über
 * [DemoTourSession.sweepOrphans] eine abgebrochene, nie beendete Demo-Tour eines vorherigen
 * Prozesslaufs auf - bedingungslos, weil dieses ViewModel nur einmal pro Prozess-Leben entsteht.
 */
class GuideViewModel(demoTourSession: DemoTourSession) : ViewModel() {

    val controller = GuideController(viewModelScope)

    init {
        viewModelScope.launch { demoTourSession.sweepOrphans() }
    }
}
