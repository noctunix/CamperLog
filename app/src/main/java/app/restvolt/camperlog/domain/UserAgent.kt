package app.restvolt.camperlog.domain

/**
 * Identifizierender User-Agent für jede Anfrage an Open-Meteo oder an OSM-Kacheln (6.8, 6.9, 13):
 * ein generischer Bibliotheks-User-Agent kann laut OSM-Kachelrichtlinie ohne Vorwarnung blockiert
 * werden.
 */
fun camperLogUserAgent(versionName: String): String = "CamperLog/$versionName (+https://github.com/noctunix/CamperLog)"
