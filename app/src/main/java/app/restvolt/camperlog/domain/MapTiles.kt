package app.restvolt.camperlog.domain

/** Einziger erlaubter Kachel-Host der OSM-Nutzungsrichtlinie. */
const val OSM_TILE_HOST = "tile.openstreetmap.org"

/** Zeitbudget je Kachelabruf. */
const val TILE_TIMEOUT_MS = 10_000

/** Höchstzahl gleichzeitiger Kachelabrufe, gemäß der OSM-Nutzungsrichtlinie ("a few parallel connections"). */
const val TILE_CONCURRENCY_LIMIT = 4

/**
 * `max-stale`-Direktive in Sekunden für Kachelabrufe: 180 Tage, damit bereits geladene Kacheln
 * großzügig wiederverwendet werden, statt bei jedem Besuch neu geladen zu werden. Abgelaufene
 * Kacheln werden dadurch nicht übersprungen, sondern bedingt nachgefragt (conditional request)
 * – nie `no-cache`.
 */
const val TILE_MAX_STALE_SECONDS = 15_552_000L

/** Ausfallschwelle für das Offline-/Fehlerbanner. */
const val TILE_FAILURE_BANNER_THRESHOLD = 3

/** Größe des Kachel-HTTP-Caches auf der Festplatte. */
const val TILE_HTTP_CACHE_MAX_BYTES = 300L * 1024 * 1024

/** `https://tile.openstreetmap.org/{z}/{x}/{y}.png` */
fun tileUrl(tile: TileCoord): String = "https://$OSM_TILE_HOST/${tile.zoom}/${tile.x}/${tile.y}.png"

/** Größe in MB, gerundet, für die Anzeige "Kartenspeicher: N MB". */
fun tileCacheMegabytes(bytes: Long): Int = Math.round(bytes / (1024.0 * 1024.0)).toInt()

/** Ab wie vielen fehlgeschlagenen Kachelabrufen das Offline-/Fehlerbanner erscheint. */
fun shouldShowTileFailureBanner(failedTileCount: Int): Boolean = failedTileCount >= TILE_FAILURE_BANNER_THRESHOLD

/** Ergebnis eines einzelnen Kachelabrufs. */
sealed interface TileLoadResult {
    /** [pngBytes] ist der rohe Bildinhalt; das Dekodieren zu einem Bitmap passiert erst beim Zeichnen. */
    data class Success(val pngBytes: ByteArray) : TileLoadResult
    data object Failure : TileLoadResult
}

/**
 * Lädt einzelne Kartenkacheln. Die Android-Implementierung
 * ([app.restvolt.camperlog.data.AndroidTileLoader]) nutzt `HttpsURLConnection`; ein prozessweit
 * installierter `HttpResponseCache` ([app.restvolt.camperlog.data.TileHttpCache]) übernimmt die
 * Zwischenspeicherung transparent für jede Anfrage.
 */
interface TileLoader {
    suspend fun load(tile: TileCoord): TileLoadResult
}
