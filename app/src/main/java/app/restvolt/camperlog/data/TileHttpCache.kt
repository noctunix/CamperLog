package app.restvolt.camperlog.data

import android.content.Context
import android.net.http.HttpResponseCache
import app.restvolt.camperlog.domain.TILE_HTTP_CACHE_MAX_BYTES
import java.io.File
import java.io.IOException

/**
 * Prozessweiter `HttpResponseCache` für Kartenkacheln (6.9, ROADMAP 1.8.0): ein einziges,
 * dauerhaftes Verzeichnis unter `cacheDir`, das Neustarts überlebt (anders als ein reiner
 * In-Memory-Cache pro Bildschirmaufruf, siehe die Voltocol-Lektion in der ROADMAP). Betrifft nur
 * Kacheln, weil nichts anderes in der App gleich große, wiederkehrende Anfragen stellt.
 */
object TileHttpCache {
    private const val DIR_NAME = "tiles_http"

    /** Installiert den Cache einmal beim App-Start; ein zweiter Aufruf ist wirkungslos, da schon installiert. */
    fun install(context: Context) {
        if (HttpResponseCache.getInstalled() != null) return
        try {
            HttpResponseCache.install(File(context.cacheDir, DIR_NAME), TILE_HTTP_CACHE_MAX_BYTES)
        } catch (_: IOException) {
            // Ohne Cache funktioniert der Kachelabruf weiter, nur ohne Zwischenspeicherung.
        }
    }

    /** Aktuelle Größe des Kachel-Caches auf der Festplatte in Byte, für die Anzeige in den Einstellungen (6.11). */
    fun sizeBytes(context: Context): Long = File(context.cacheDir, DIR_NAME).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Löscht den Kachel-Cache (6.11: "Kartenspeicher … [Leeren]"; beim Ausschalten des Schalters, 6.11). */
    fun clear(context: Context) {
        try {
            HttpResponseCache.getInstalled()?.delete()
        } catch (_: IOException) {
            // Löschen auf Dateisystemebene unten holt das in jedem Fall nach.
        }
        File(context.cacheDir, DIR_NAME).deleteRecursively()
    }
}
