package app.restvolt.camperlog.domain

/**
 * Automatisch erkannte Länder einer Tour: aus den Koordinaten ihrer Stationen über [lookup]
 * erkannte Länder, vereinigt mit den Vignetten-Ländern ihrer Maut-Stationen ([TollKind.VIGNETTE]).
 * Enthält weder [Tour.manualCountriesAdded] noch [Tour.manualCountriesRemoved]; siehe [tourCountries].
 */
suspend fun autoDetectedCountries(stations: List<Station>, lookup: CountryLookupRepository): Set<String> {
    val detected = stations.mapNotNull { station ->
        val latitude = station.latitude ?: return@mapNotNull null
        val longitude = station.longitude ?: return@mapNotNull null
        lookup.countryAt(latitude, longitude)
    }
    val vignette = stations
        .filter { it.type == StationType.TOLL && it.tollKind == TollKind.VIGNETTE }
        .mapNotNull { it.tollCountry }
    return (detected + vignette).toSet()
}

/** Länder einer Tour: [autoDetected] zuzüglich [manuallyAdded] und abzüglich [manuallyRemoved]. */
fun tourCountries(autoDetected: Set<String>, manuallyAdded: Set<String>, manuallyRemoved: Set<String>): Set<String> =
    (autoDetected + manuallyAdded) - manuallyRemoved

/**
 * Länder je Jahr der Startdaten von [tours] (siehe [tourCountries]); [stationsByTourId] ordnet
 * jeder Tour ihre Stationen zu (Stationen ohne Tour zählen nicht mit).
 */
suspend fun tourCountriesByYear(
    tours: List<Tour>,
    stationsByTourId: Map<Long, List<Station>>,
    lookup: CountryLookupRepository,
): Map<Int, Set<String>> {
    val result = HashMap<Int, Set<String>>()
    for (tour in tours) {
        val detected = autoDetectedCountries(stationsByTourId[tour.id].orEmpty(), lookup)
        val countries = tourCountries(detected, tour.manualCountriesAdded, tour.manualCountriesRemoved)
        result[tour.year] = result[tour.year].orEmpty() + countries
    }
    return result
}

/** Vereinigung aller Werte von [tourCountriesByYear]'s Ergebnis, für die Gesamtanzeige. */
fun Map<Int, Set<String>>.totalCountries(): Set<String> = values.flatten().toSet()
