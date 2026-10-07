package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.CountryLookupRepository

/** Ordnet Koordinaten über [exact] zu; ohne Treffer `null`, wie über See. */
class FakeCountryLookupRepository(private val exact: Map<Pair<Double, Double>, String> = emptyMap()) : CountryLookupRepository {
    override suspend fun countryAt(latitude: Double, longitude: Double): String? = exact[latitude to longitude]
}
