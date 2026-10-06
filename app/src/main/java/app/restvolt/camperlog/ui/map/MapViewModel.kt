package app.restvolt.camperlog.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.TileCoord
import app.restvolt.camperlog.domain.TileLoadResult
import app.restvolt.camperlog.domain.TileLoader
import app.restvolt.camperlog.domain.shouldShowTileFailureBanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ladezustand einer einzelnen Kachel im Zustand von [MapViewModel]. */
sealed interface TileState {
    data object Loading : TileState
    data class Loaded(val pngBytes: ByteArray) : TileState
    data object Failed : TileState
}

/** Höchstzahl im Session-Zwischenspeicher gehaltener Kacheln. */
private const val TILE_MEMORY_CACHE_CAPACITY = 256

/**
 * Lädt die aktuell sichtbaren Kacheln über [tileLoader]: begrenzt auf die per
 * [setVisibleTiles] gemeldete Sichtfläche, bricht Anfragen für Kacheln ab, die nicht mehr sichtbar
 * sind, und hält bereits geladene Kacheln in einem LRU-Zwischenspeicher für die laufende Sitzung.
 * Das Banner nach [TileState.Failed]-Häufung ([shouldShowTileFailureBanner]) bleibt bestehen, bis
 * [retryFailedTiles] erneut versucht.
 */
class MapViewModel(private val tileLoader: TileLoader) : ViewModel() {

    private val cache = LruTileCache(TILE_MEMORY_CACHE_CAPACITY)
    private val jobs = mutableMapOf<TileCoord, Job>()
    private var currentVisible: Set<TileCoord> = emptySet()

    private val _tiles = MutableStateFlow<Map<TileCoord, TileState>>(emptyMap())
    val tiles: StateFlow<Map<TileCoord, TileState>> = _tiles.asStateFlow()

    private val _failedTileCount = MutableStateFlow(0)
    val showFailureBanner: StateFlow<Boolean> = _failedTileCount
        .map(::shouldShowTileFailureBanner)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Meldet die aktuell sichtbare Kachelmenge. Kacheln, die den Bildschirm verlassen haben, werden
     * abgebrochen; neue, noch unbekannte Kacheln werden angefragt.
     */
    fun setVisibleTiles(visible: Set<TileCoord>) {
        currentVisible = visible
        val obsolete = jobs.keys - visible
        for (tile in obsolete) jobs.remove(tile)?.cancel()

        val toLoad = visible.filter { it !in jobs && cache.get(it) == null }
        for (tile in toLoad) {
            _tiles.update { it + (tile to TileState.Loading) }
            jobs[tile] = viewModelScope.launch {
                when (val result = tileLoader.load(tile)) {
                    is TileLoadResult.Success -> {
                        cache.put(tile, result.pngBytes)
                        _tiles.update { it + (tile to TileState.Loaded(result.pngBytes)) }
                    }
                    TileLoadResult.Failure -> {
                        _failedTileCount.update { it + 1 }
                        _tiles.update { it + (tile to TileState.Failed) }
                    }
                }
                jobs.remove(tile)
            }
        }
    }

    /** "Erneut" auf dem Fehlerbanner: setzt den Zähler zurück und fragt fehlgeschlagene Kacheln neu an. */
    fun retryFailedTiles() {
        _failedTileCount.value = 0
        val failed = _tiles.value.filterValues { it is TileState.Failed }.keys
        _tiles.update { current -> current - failed }
        setVisibleTiles(currentVisible)
    }

    override fun onCleared() {
        for (job in jobs.values) job.cancel()
        jobs.clear()
    }
}

/** Kleiner LRU-Zwischenspeicher für Kachelbytes der laufenden Sitzung, ohne Android-Abhängigkeit. */
private class LruTileCache(private val capacity: Int) {
    private val entries = LinkedHashMap<TileCoord, ByteArray>(capacity, 0.75f, true)

    @Synchronized
    fun get(key: TileCoord): ByteArray? = entries[key]

    @Synchronized
    fun put(key: TileCoord, value: ByteArray) {
        entries[key] = value
        if (entries.size > capacity) {
            val oldest = entries.keys.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
    }
}
