package app.restvolt.camperlog.domain

/** Trackpunkte werden vor dem Nachschlagen auf höchstens so viele, zeitlich gleichmäßig verteilte Stichproben reduziert. */
private const val MAX_TRACK_SAMPLES = 40

/**
 * Automatisch erkannte Länder einer Tour: aus den Koordinaten ihrer Stationen und – falls keine
 * Station an einer Stelle liegt – zusätzlich aus einer Stichprobe ihres aufgezeichneten GPS-Tracks
 * ([track], auf [MAX_TRACK_SAMPLES] Punkte heruntergesampelt) über [lookup] erkannte Länder,
 * vereinigt mit den Vignetten-Ländern ihrer Maut-Stationen ([TollKind.VIGNETTE]). Enthält weder
 * [Tour.manualCountriesAdded] noch [Tour.manualCountriesRemoved]; siehe [tourCountries].
 */
suspend fun autoDetectedCountries(
    stations: List<Station>,
    lookup: CountryLookupRepository,
    track: List<TrackPoint> = emptyList(),
): Set<String> {
    val stationCountries = stations.mapNotNull { station ->
        val latitude = station.latitude ?: return@mapNotNull null
        val longitude = station.longitude ?: return@mapNotNull null
        lookup.countryAt(latitude, longitude)
    }
    val trackCountries = sampleTrackPoints(track, MAX_TRACK_SAMPLES).mapNotNull { lookup.countryAt(it.latitude, it.longitude) }
    val vignette = stations
        .filter { it.type == StationType.TOLL && it.tollKind == TollKind.VIGNETTE }
        .mapNotNull { it.tollCountry }
    return (stationCountries + trackCountries + vignette).toSet()
}

/** Höchstens [max] gleichmäßig über [points] verteilte Punkte, unverändert falls schon kürzer. */
private fun sampleTrackPoints(points: List<TrackPoint>, max: Int): List<TrackPoint> {
    if (points.size <= max) return points
    val step = points.size.toDouble() / max
    return (0 until max).map { points[(it * step).toInt()] }
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
