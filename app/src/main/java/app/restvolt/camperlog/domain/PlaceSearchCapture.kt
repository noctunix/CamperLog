package app.restvolt.camperlog.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Zustand der Ortssuche im Stationsformular, ausgelöst durch einen Tastendruck. */
sealed interface PlaceSearchState {

    /** Bereit für eine Suche. */
    data object Ready : PlaceSearchState

    /** Die Suche läuft. */
    data object Loading : PlaceSearchState

    /** Treffer liegen vor. */
    data class Results(val hits: List<PlaceSearchHit>) : PlaceSearchState

    /** Die Suche war erfolgreich, ergab aber keine Treffer. */
    data object Empty : PlaceSearchState

    /** Keine Internetverbindung (`UnknownHostException`). */
    data object Offline : PlaceSearchState

    /** Dienst nicht erreichbar, auch bei HTTP 429 ([PlaceSearchResult.RateLimited]); zeigt denselben Text. */
    data object ServiceUnavailable : PlaceSearchState
}

/**
 * Steuert die Ortssuche; eingebettet in ein ViewModel über dessen `viewModelScope`, damit eine
 * laufende Suche einen Konfigurationswechsel überlebt.
 */
class PlaceSearchController(private val provider: PlaceSearchProvider, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<PlaceSearchState>(PlaceSearchState.Ready)
    val state: StateFlow<PlaceSearchState> = _state.asStateFlow()

    private var job: Job? = null

    /** Explizite Suche nach Tastendruck auf "Suchen"; eine leere Eingabe tut nichts. */
    fun search(query: String, language: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        job?.cancel()
        job = scope.launch {
            _state.value = PlaceSearchState.Loading
            _state.value = when (val result = provider.search(trimmed, language)) {
                is PlaceSearchResult.Success -> if (result.hits.isEmpty()) PlaceSearchState.Empty else PlaceSearchState.Results(result.hits)
                PlaceSearchResult.Offline -> PlaceSearchState.Offline
                PlaceSearchResult.RateLimited, PlaceSearchResult.Error -> PlaceSearchState.ServiceUnavailable
            }
        }
    }

    /** Setzt den Zustand zurück, z. B. nach einem Treffer oder beim Schließen der Suche. */
    fun reset() {
        job?.cancel()
        _state.value = PlaceSearchState.Ready
    }
}
