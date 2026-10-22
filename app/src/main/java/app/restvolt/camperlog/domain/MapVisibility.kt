package app.restvolt.camperlog.domain

import java.time.LocalTime

/** Koordinaten einer Station als [LatLon], oder `null` ohne Koordinaten. */
fun Station.toLatLon(): LatLon? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return LatLon(lat, lon)
}

/** Nur die Stationen mit Koordinaten, in der Reihenfolge von [stations]. */
fun stationsWithCoordinates(stations: List<Station>): List<Station> = stations.filter { it.latitude != null && it.longitude != null }

/**
 * Stationen mit Koordinaten, aufsteigend chronologisch sortiert: die gestrichelte Linie
 * verbindet aufeinanderfolgende verortete Stationen zeitlich, unabhängig von der Sortierung der
 * übergebenen Liste, z. B. dem neueste-zuerst-Stationen-Reiter.
 */
fun stationsForMap(stations: List<Station>): List<Station> =
    stationsWithCoordinates(stations).sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MAX }, { it.createdAt }))

/**
 * Ob eine Karte für [stations] angezeigt werden darf: nur wenn "Wetter & Karte" an ist
 * ([weatherMapEnabled]) und mindestens eine Station Koordinaten hat ODER ein GPS-Track
 * aufgezeichnet wurde ([hasTrack]). Der sichtbare Einstieg kann fehlende Voraussetzungen erklären.
 */
fun isMapAvailable(weatherMapEnabled: Boolean, stations: List<Station>, hasTrack: Boolean = false): Boolean =
    weatherMapEnabled && (stationsWithCoordinates(stations).isNotEmpty() || hasTrack)
