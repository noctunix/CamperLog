package app.restvolt.camperlog.domain

import java.net.URL
import java.net.URLEncoder

/** Host, den die Ortssuche ausschließlich ansprechen darf. */
const val NOMINATIM_HOST = "nominatim.openstreetmap.org"

/** Zeitbudget für Verbindungsaufbau und Antwort der Ortssuche. */
const val PLACE_SEARCH_TIMEOUT_MS = 8_000

/** Nominatims Nutzungsrichtlinie erlaubt höchstens eine Anfrage pro Sekunde an die öffentliche Instanz. */
const val PLACE_SEARCH_MIN_INTERVAL_MS = 1_000L

/** Höchstzahl der Treffer je Suche. */
const val PLACE_SEARCH_RESULT_LIMIT = 5

/**
 * Nominatim-Suche nach [query] in der Sprache [language] ("de" oder "en", siehe [supportedLocale]),
 * ohne Adressdetails. Android-Implementierung siehe `app.restvolt.camperlog.data.AndroidPlaceSearchProvider`.
 */
fun buildPlaceSearchRequestUrl(query: String, language: String): URL {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    return URL(
        "https://$NOMINATIM_HOST/search?format=jsonv2&q=$encodedQuery&limit=$PLACE_SEARCH_RESULT_LIMIT" +
            "&accept-language=$language&addressdetails=0",
    )
}

/** Ein Treffer der Ortssuche. [category]/[type] sind Nominatims Klassifikation, z. B. "place"/"city". */
data class PlaceSearchHit(
    val displayName: String,
    val category: String?,
    val type: String?,
    val latitude: Double,
    val longitude: Double,
)

/** Ergebnis einer Ortssuche; [RateLimited] und [Error] zeigen denselben Hinweistext. */
sealed interface PlaceSearchResult {
    data class Success(val hits: List<PlaceSearchHit>) : PlaceSearchResult
    data object Offline : PlaceSearchResult
    data object RateLimited : PlaceSearchResult
    data object Error : PlaceSearchResult
}

/**
 * Ortssuche, ausschließlich auf einen expliziten Tastendruck hin (nie während der Eingabe).
 * Die Android-Implementierung ruft Nominatim über `HttpsURLConnection` auf; siehe
 * [app.restvolt.camperlog.data.AndroidPlaceSearchProvider].
 */
interface PlaceSearchProvider {
    suspend fun search(query: String, language: String): PlaceSearchResult
}
