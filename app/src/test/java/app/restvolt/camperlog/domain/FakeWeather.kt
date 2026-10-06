package app.restvolt.camperlog.domain

/** [WeatherProvider]-Fake für Tests ohne echtes Netzwerk. */
class FakeWeatherProvider(private val result: WeatherResult) : WeatherProvider {
    var requests = mutableListOf<Pair<Double, Double>>()

    override suspend fun fetchCurrent(latitude: Double, longitude: Double): WeatherResult {
        requests.add(latitude to longitude)
        return result
    }
}
