package app.restvolt.camperlog.domain

/** Einziger erlaubter Kachel-Host der OSM-Nutzungsrichtlinie (6.9, 13). */
const val OSM_TILE_HOST = "tile.openstreetmap.org"

/** Zeitbudget je Kachelabruf. */
const val TILE_TIMEOUT_MS = 10_000

/** Höchstzahl gleichzeitiger Kachelabrufe (6.9: "A few parallel connections (2-4)"). */
const val TILE_CONCURRENCY_LIMIT = 4

/**
 * `max-stale`-Direktive in Sekunden für Kachelabrufe (ROADMAP 1.8.0, Voltocol-Lektion WP-145):
 * 180 Tage, damit bereits geladene Kacheln großzügig wiederverwendet werden, statt bei jedem
 * Besuch neu geladen zu werden. Abgelaufene Kacheln werden dadurch nicht übersprungen, sondern
 * bedingt nachgefragt (conditional request) – nie `no-cache`.
 */
const val TILE_MAX_STALE_SECONDS = 15_552_000L

/** Ausfallschwelle für das Offline-/Fehlerbanner (ROADMAP 1.8.0: "~3 failed tiles"). */
const val TILE_FAILURE_BANNER_THRESHOLD = 3

/** Größe des Kachel-HTTP-Caches auf der Festplatte (ROADMAP 1.8.0: "~300 MB"). */
const val TILE_HTTP_CACHE_MAX_BYTES = 300L * 1024 * 1024

/** `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (6.9, 13). */
fun tileUrl(tile: TileCoord): String = "https://$OSM_TILE_HOST/${tile.zoom}/${tile.x}/${tile.y}.png"

/** Größe in MB, gerundet, für die Anzeige "Kartenspeicher: N MB" (6.11). */
fun tileCacheMegabytes(bytes: Long): Int = Math.round(bytes / (1024.0 * 1024.0)).toInt()

/** Ab wie vielen fehlgeschlagenen Kachelabrufen das Offline-/Fehlerbanner erscheint (6.9, ROADMAP 1.8.0). */
fun shouldShowTileFailureBanner(failedTileCount: Int): Boolean = failedTileCount >= TILE_FAILURE_BANNER_THRESHOLD

/** Ergebnis eines einzelnen Kachelabrufs. */
sealed interface TileLoadResult {
    /** [pngBytes] ist der rohe Bildinhalt; das Dekodieren zu einem Bitmap passiert erst beim Zeichnen. */
    data class Success(val pngBytes: ByteArray) : TileLoadResult
    data object Failure : TileLoadResult
}

/**
 * Lädt einzelne Kartenkacheln (6.9, 13). Die Android-Implementierung
 * ([app.restvolt.camperlog.data.AndroidTileLoader]) nutzt `HttpsURLConnection`; ein prozessweit
 * installierter `HttpResponseCache` ([app.restvolt.camperlog.data.TileHttpCache]) übernimmt die
 * Zwischenspeicherung transparent für jede Anfrage.
 */
interface TileLoader {
    suspend fun load(tile: TileCoord): TileLoadResult
}
