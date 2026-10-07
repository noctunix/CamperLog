package app.restvolt.camperlog.domain

/** Offline-Zuordnung einer Koordinate zu ihrem Land (siehe [decodeCountryShapes], [countryAt]). */
interface CountryLookupRepository {

    /**
     * Ländercode zu ([latitude], [longitude]), oder `null` siehe [countryAt]. Lädt die gebündelte
     * Geometrie beim ersten Aufruf abseits des Hauptthreads und hält sie danach vor.
     */
    suspend fun countryAt(latitude: Double, longitude: Double): String?
}
