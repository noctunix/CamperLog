package app.restvolt.camperlog.domain

/** [PlaceSearchProvider]-Fake für Tests ohne echtes Netzwerk. */
class FakePlaceSearchProvider(private val result: PlaceSearchResult) : PlaceSearchProvider {
    var requests = mutableListOf<Pair<String, String>>()

    override suspend fun search(query: String, language: String): PlaceSearchResult {
        requests.add(query to language)
        return result
    }
}
