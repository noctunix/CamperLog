package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.PLACE_SEARCH_MIN_INTERVAL_MS
import app.restvolt.camperlog.domain.PLACE_SEARCH_TIMEOUT_MS
import app.restvolt.camperlog.domain.PlaceSearchHit
import app.restvolt.camperlog.domain.PlaceSearchProvider
import app.restvolt.camperlog.domain.PlaceSearchResult
import app.restvolt.camperlog.domain.buildPlaceSearchRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

private val json = Json { ignoreUnknownKeys = true }

/**
 * [PlaceSearchProvider] über `HttpsURLConnection`, wie [AndroidWeatherProvider]: fester Host, kurze
 * Zeitbudgets und eine identifizierende User-Agent-Kennung. Ein `Mutex` serialisiert [search], damit
 * [now]/[sleep] vor jeder Anfrage mindestens [PLACE_SEARCH_MIN_INTERVAL_MS] seit der letzten abwarten,
 * wie es Nominatims Nutzungsrichtlinie für die öffentliche Instanz verlangt; in Tests austauschbar,
 * um das ohne echtes Warten zu prüfen.
 */
class AndroidPlaceSearchProvider(
    private val userAgent: String,
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpsURLConnection },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : PlaceSearchProvider {

    private val mutex = Mutex()
    private var lastRequestAt: Long? = null

    override suspend fun search(query: String, language: String): PlaceSearchResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            lastRequestAt?.let { last ->
                val elapsed = now() - last
                if (elapsed < PLACE_SEARCH_MIN_INTERVAL_MS) sleep(PLACE_SEARCH_MIN_INTERVAL_MS - elapsed)
            }
            lastRequestAt = now()
            try {
                val connection = openConnection(buildPlaceSearchRequestUrl(query, language))
                connection.instanceFollowRedirects = false
                connection.connectTimeout = PLACE_SEARCH_TIMEOUT_MS
                connection.readTimeout = PLACE_SEARCH_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                connection.requestMethod = "GET"
                try {
                    when (connection.responseCode) {
                        HttpURLConnection.HTTP_OK -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                        429 -> PlaceSearchResult.RateLimited
                        else -> PlaceSearchResult.Error
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: UnknownHostException) {
                PlaceSearchResult.Offline
            } catch (_: IOException) {
                PlaceSearchResult.Error
            } catch (_: SerializationException) {
                PlaceSearchResult.Error
            }
        }
    }

    /** Ein Eintrag ohne gültiges `lat`/`lon`/`display_name` wird übersprungen statt die ganze Suche scheitern zu lassen. */
    private fun parse(body: String): PlaceSearchResult {
        val hits = json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            runCatching {
                val entry = element.jsonObject
                PlaceSearchHit(
                    displayName = entry.getValue("display_name").jsonPrimitive.content,
                    category = (entry["category"] as? JsonPrimitive)?.contentOrNull,
                    type = (entry["type"] as? JsonPrimitive)?.contentOrNull,
                    latitude = entry.getValue("lat").jsonPrimitive.content.toDouble(),
                    longitude = entry.getValue("lon").jsonPrimitive.content.toDouble(),
                )
            }.getOrNull()
        }
        return PlaceSearchResult.Success(hits)
    }
}
