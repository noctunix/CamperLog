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

/** Für eine Reise-App relevante OSM-Taxonomie-Kombinationen, mit eigenem Anzeigetext statt Nominatims rohem Code. */
enum class PlaceCategoryKind {
    CAMP_SITE, CARAVAN_SITE, HOTEL, GUEST_HOUSE, MOTEL, HOSTEL, ALPINE_HUT, VIEWPOINT, PICNIC_SITE,
    FUEL, RESTAURANT, CAFE, FAST_FOOD, PARKING, TOILETS, DRINKING_WATER, BANK, PHARMACY, HOSPITAL,
    FIRE_STATION, POLICE, SUPERMARKET, BAKERY, CONVENIENCE, PEAK, BEACH, CASTLE, MONUMENT, RUINS,
    CITY, TOWN, VILLAGE, HAMLET, SUBURB,
}

private val PLACE_CATEGORY_KINDS_BY_TAG: Map<Pair<String, String>, PlaceCategoryKind> = mapOf(
    ("tourism" to "camp_site") to PlaceCategoryKind.CAMP_SITE,
    ("tourism" to "caravan_site") to PlaceCategoryKind.CARAVAN_SITE,
    ("tourism" to "hotel") to PlaceCategoryKind.HOTEL,
    ("tourism" to "guest_house") to PlaceCategoryKind.GUEST_HOUSE,
    ("tourism" to "motel") to PlaceCategoryKind.MOTEL,
    ("tourism" to "hostel") to PlaceCategoryKind.HOSTEL,
    ("tourism" to "alpine_hut") to PlaceCategoryKind.ALPINE_HUT,
    ("tourism" to "viewpoint") to PlaceCategoryKind.VIEWPOINT,
    ("tourism" to "picnic_site") to PlaceCategoryKind.PICNIC_SITE,
    ("amenity" to "fuel") to PlaceCategoryKind.FUEL,
    ("amenity" to "restaurant") to PlaceCategoryKind.RESTAURANT,
    ("amenity" to "cafe") to PlaceCategoryKind.CAFE,
    ("amenity" to "fast_food") to PlaceCategoryKind.FAST_FOOD,
    ("amenity" to "parking") to PlaceCategoryKind.PARKING,
    ("amenity" to "toilets") to PlaceCategoryKind.TOILETS,
    ("amenity" to "drinking_water") to PlaceCategoryKind.DRINKING_WATER,
    ("amenity" to "bank") to PlaceCategoryKind.BANK,
    ("amenity" to "pharmacy") to PlaceCategoryKind.PHARMACY,
    ("amenity" to "hospital") to PlaceCategoryKind.HOSPITAL,
    ("amenity" to "fire_station") to PlaceCategoryKind.FIRE_STATION,
    ("amenity" to "police") to PlaceCategoryKind.POLICE,
    ("shop" to "supermarket") to PlaceCategoryKind.SUPERMARKET,
    ("shop" to "bakery") to PlaceCategoryKind.BAKERY,
    ("shop" to "convenience") to PlaceCategoryKind.CONVENIENCE,
    ("natural" to "peak") to PlaceCategoryKind.PEAK,
    ("natural" to "beach") to PlaceCategoryKind.BEACH,
    ("historic" to "castle") to PlaceCategoryKind.CASTLE,
    ("historic" to "monument") to PlaceCategoryKind.MONUMENT,
    ("historic" to "ruins") to PlaceCategoryKind.RUINS,
    ("place" to "city") to PlaceCategoryKind.CITY,
    ("place" to "town") to PlaceCategoryKind.TOWN,
    ("place" to "village") to PlaceCategoryKind.VILLAGE,
    ("place" to "hamlet") to PlaceCategoryKind.HAMLET,
    ("place" to "suburb") to PlaceCategoryKind.SUBURB,
)

/** Bekannte OSM-Kombination aus [category]/[type] als [PlaceCategoryKind], oder `null` für unbekannte Kombinationen. */
fun placeCategoryKind(category: String?, type: String?): PlaceCategoryKind? {
    if (category == null || type == null) return null
    return PLACE_CATEGORY_KINDS_BY_TAG[category to type]
}

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
