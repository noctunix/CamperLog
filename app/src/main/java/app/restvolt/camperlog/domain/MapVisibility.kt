package app.restvolt.camperlog.domain

import java.time.LocalTime

/** Koordinaten einer Station als [LatLon], oder `null` ohne Koordinaten (5.3). */
fun Station.toLatLon(): LatLon? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return LatLon(lat, lon)
}

/** Nur die Stationen mit Koordinaten, in der Reihenfolge von [stations] (5.3: Marker nur mit Koordinaten). */
fun stationsWithCoordinates(stations: List<Station>): List<Station> = stations.filter { it.latitude != null && it.longitude != null }

/**
 * Stationen mit Koordinaten, aufsteigend chronologisch sortiert (5.3: die gestrichelte Linie
 * verbindet aufeinanderfolgende verortete Stationen zeitlich, unabhängig von der Sortierung der
 * übergebenen Liste, z. B. dem neueste-zuerst-Stationen-Reiter).
 */
fun stationsForMap(stations: List<Station>): List<Station> =
    stationsWithCoordinates(stations).sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MAX }, { it.createdAt }))

/**
 * Ob eine Karte für [stations] angezeigt werden darf (5.3, 13): nur wenn "Wetter & Karte" an ist
 * ([weatherMapEnabled]) und mindestens eine Station Koordinaten hat. Sonst existiert der
 * Einstiegspunkt gar nicht erst.
 */
fun isMapAvailable(weatherMapEnabled: Boolean, stations: List<Station>): Boolean =
    weatherMapEnabled && stationsWithCoordinates(stations).isNotEmpty()
