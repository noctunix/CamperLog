package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.TILE_CONCURRENCY_LIMIT
import app.restvolt.camperlog.domain.TILE_MAX_STALE_SECONDS
import app.restvolt.camperlog.domain.TILE_TIMEOUT_MS
import app.restvolt.camperlog.domain.TileCoord
import app.restvolt.camperlog.domain.TileLoadResult
import app.restvolt.camperlog.domain.TileLoader
import app.restvolt.camperlog.domain.tileUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * [TileLoader] über `HttpsURLConnection` (6.9, 13): identifizierende User-Agent-Kennung,
 * `Cache-Control: max-stale=…` statt `no-cache` (ROADMAP 1.8.0: großzügige Wiederverwendung
 * zwischengespeicherter Kacheln), nie automatisch verfolgte Weiterleitungen. Die tatsächliche
 * Zwischenspeicherung übernimmt der prozessweit installierte `HttpResponseCache`
 * ([TileHttpCache]) transparent für jede `HttpURLConnection`; diese Klasse begrenzt nur, wie
 * viele Anfragen gleichzeitig unterwegs sind ([TILE_CONCURRENCY_LIMIT]). [openConnection] ist für
 * Tests mit einer eigenen `HttpURLConnection`-Fälschung austauschbar.
 */
class AndroidTileLoader(
    private val userAgent: String,
    maxConcurrent: Int = TILE_CONCURRENCY_LIMIT,
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpsURLConnection },
) : TileLoader {

    private val semaphore = Semaphore(maxConcurrent)

    override suspend fun load(tile: TileCoord): TileLoadResult = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            try {
                val connection = openConnection(URL(tileUrl(tile)))
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TILE_TIMEOUT_MS
                connection.readTimeout = TILE_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Cache-Control", "max-stale=$TILE_MAX_STALE_SECONDS")
                connection.requestMethod = "GET"
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        TileLoadResult.Success(connection.inputStream.use { it.readBytes() })
                    } else {
                        TileLoadResult.Failure
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: IOException) {
                TileLoadResult.Failure
            }
        }
    }
}
