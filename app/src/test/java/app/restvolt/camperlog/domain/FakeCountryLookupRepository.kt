package app.restvolt.camperlog.domain

/** [CountryLookupRepository]-Fake: ordnet ([latitude], [longitude]) über [codeByCoordinate] zu; ohne Treffer `null`. */
class FakeCountryLookupRepository(private val codeByCoordinate: Map<Pair<Double, Double>, String> = emptyMap()) : CountryLookupRepository {
    override suspend fun countryAt(latitude: Double, longitude: Double): String? = codeByCoordinate[latitude to longitude]
}
