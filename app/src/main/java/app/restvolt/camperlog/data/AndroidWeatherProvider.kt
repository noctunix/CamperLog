package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.OPEN_METEO_HOST
import app.restvolt.camperlog.domain.WEATHER_TIMEOUT_MS
import app.restvolt.camperlog.domain.WeatherProvider
import app.restvolt.camperlog.domain.WeatherResult
import app.restvolt.camperlog.domain.WeatherSnapshot
import app.restvolt.camperlog.domain.buildWeatherRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class OpenMeteoResponse(val current: CurrentWeather)

@Serializable
private data class CurrentWeather(
    val time: Long,
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed10m: Double,
    @SerialName("wind_gusts_10m") val windGusts10m: Double? = null,
    @SerialName("wind_direction_10m") val windDirection10m: Int? = null,
)

/**
 * [WeatherProvider] über `HttpsURLConnection`: keine zusätzliche Abhängigkeit, dafür
 * fester Host, kurze Zeitbudgets und eine identifizierende User-Agent-Kennung. Weiterleitungen
 * werden nicht automatisch verfolgt, damit die Antwort nie von einem anderen Host als
 * [OPEN_METEO_HOST] stammen kann. [openConnection] ist für Tests mit einer eigenen
 * `HttpURLConnection`-Fälschung austauschbar.
 */
class AndroidWeatherProvider(
    private val userAgent: String,
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpsURLConnection },
) : WeatherProvider {

    override suspend fun fetchCurrent(latitude: Double, longitude: Double): WeatherResult = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(buildWeatherRequestUrl(latitude, longitude))
            connection.instanceFollowRedirects = false
            connection.connectTimeout = WEATHER_TIMEOUT_MS
            connection.readTimeout = WEATHER_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.requestMethod = "GET"
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> parse(connection.inputStream.use { it.readTextAtMost(MAX_JSON_RESPONSE_BYTES) })
                    429 -> WeatherResult.RateLimited
                    else -> WeatherResult.Error
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: UnknownHostException) {
            WeatherResult.Offline
        } catch (_: IOException) {
            WeatherResult.Error
        } catch (_: SerializationException) {
            WeatherResult.Error
        }
    }

    private fun parse(body: String): WeatherResult {
        val current = json.decodeFromString(OpenMeteoResponse.serializer(), body).current
        return WeatherResult.Success(
            WeatherSnapshot(
                temperatureDeciC = Math.round(current.temperature2m * 10.0).toInt(),
                weatherCode = current.weatherCode,
                windKmh = current.windSpeed10m.roundToInt(),
                gustKmh = current.windGusts10m?.roundToInt(),
                windDirectionDeg = current.windDirection10m,
                observedAt = Instant.ofEpochSecond(current.time),
            ),
        )
    }
}
