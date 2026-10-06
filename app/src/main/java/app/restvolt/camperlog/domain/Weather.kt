package app.restvolt.camperlog.domain

import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Host, den der Wetterabruf ausschließlich ansprechen darf (6.8, 9). */
const val OPEN_METEO_HOST = "api.open-meteo.com"

/** Zeitbudget für Verbindungsaufbau und Antwort des Wetterabrufs (6.8). */
const val WEATHER_TIMEOUT_MS = 8_000

/**
 * Rundet eine Koordinate auf 2 Nachkommastellen (~1 km), bevor sie den Dienst verlässt (6.8, 9):
 * das Wettermodellraster ist ohnehin gröber, und Open-Meteo speichert Koordinaten in Server-Logs.
 */
fun roundCoordinate(value: Double): Double = Math.round(value * 100.0) / 100.0

/**
 * Open-Meteo-Anfrage für den aktuellen Wetterstand (6.8): [latitude]/[longitude] werden vor dem
 * Aufruf mit [roundCoordinate] gerundet. `timeformat=unixtime` liefert `current.time` in Sekunden
 * seit Epoch, `wind_speed_unit=kmh` erspart eine Umrechnung.
 */
fun buildWeatherRequestUrl(latitude: Double, longitude: Double): URL {
    val lat = String.format(Locale.ROOT, "%.2f", roundCoordinate(latitude))
    val lon = String.format(Locale.ROOT, "%.2f", roundCoordinate(longitude))
    return URL(
        "https://$OPEN_METEO_HOST/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m" +
            "&timeformat=unixtime&wind_speed_unit=kmh",
    )
}

/** Ergebnis eines Wetterabrufs (6.8); [RateLimited] und [Error] zeigen denselben Hinweistext. */
sealed interface WeatherResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherResult
    data object Offline : WeatherResult
    data object RateLimited : WeatherResult
    data object Error : WeatherResult
}

/**
 * Einmalige Wetterabfrage zu Koordinaten (6.8). Die Android-Implementierung ruft Open-Meteo über
 * `HttpsURLConnection` auf; siehe [app.restvolt.camperlog.data.AndroidWeatherProvider].
 */
interface WeatherProvider {
    suspend fun fetchCurrent(latitude: Double, longitude: Double): WeatherResult
}

/** Wetterlage nach dem WMO-Code (6.8), für Anzeigetext und Symbol in `ui/Labels.kt`. */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    THUNDERSTORM,
}

/**
 * Ordnet einen WMO-Wettercode (`current.weather_code`) einer [WeatherCondition] zu (6.8).
 * Unbekannte Codes fallen auf [WeatherCondition.OVERCAST] zurück, statt nichts anzuzeigen.
 */
fun weatherCondition(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.OVERCAST
    45, 48 -> WeatherCondition.FOG
    66, 67 -> WeatherCondition.FREEZING_RAIN
    in 51..65, 80, 81, 82 -> WeatherCondition.RAIN
    71, 72, 73, 74, 75, 76, 77, 85, 86 -> WeatherCondition.SNOW
    in 95..99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.OVERCAST
}

/** Windrichtung als 8-Punkte-Kompass (6.8), für den Anzeigetext in `ui/Labels.kt`. */
enum class CompassDirection { N, NE, E, SE, S, SW, W, NW }

/** Ordnet eine Windrichtung in Grad (`current.wind_direction_10m`) einer [CompassDirection] zu. */
fun compassDirection(degrees: Int): CompassDirection {
    val normalized = ((degrees % 360) + 360) % 360
    return CompassDirection.entries[(normalized + 22) / 45 % 8]
}

/** Uhrzeit eines Wetter-Zeitpunkts in der Zeitzone des Geräts, z. B. `18:30` (6.6, 6.8). */
fun formatObservedTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = instant.atZone(zone).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}
