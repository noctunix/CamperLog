package app.restvolt.camperlog.domain

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Kantenlänge einer OSM-Rasterkachel in Pixeln. */
const val MAP_TILE_SIZE = 256

/** Erlaubter Zoombereich der Karte. */
const val MAP_MIN_ZOOM = 2
const val MAP_MAX_ZOOM = 18

/** Anfangszoom bei genau einer Station, da dort keine Ausdehnung zum Einpassen existiert. */
const val MAP_SINGLE_POINT_ZOOM = 13.0

/** Begrenzt einen Zoomwert auf [MAP_MIN_ZOOM]..[MAP_MAX_ZOOM]. */
fun clampZoom(zoom: Double): Double = zoom.coerceIn(MAP_MIN_ZOOM.toDouble(), MAP_MAX_ZOOM.toDouble())

/** Ein geografischer Punkt (WGS84, Grad). */
data class LatLon(val latitude: Double, val longitude: Double)

/** Kamera der Karte: Mittelpunkt und stufenloser Zoom (für flüssiges Kneifzoomen). */
data class MapCamera(val center: LatLon, val zoom: Double)

/** Eine Kachel im Slippy-Map-Schema (`z/x/y`). */
data class TileCoord(val zoom: Int, val x: Int, val y: Int)

/** Seitenlänge der gesamten Weltkarte in Pixeln bei [zoom] (2^zoom Kacheln je [MAP_TILE_SIZE] Pixel). */
fun mapSizePx(zoom: Double): Double = MAP_TILE_SIZE * 2.0.pow(zoom)

/** Weltpixel-X einer Länge bei [zoom] (Web-Mercator, Kachelschema wie `tile.openstreetmap.org`). */
fun lonToWorldX(longitude: Double, zoom: Double): Double = (longitude + 180.0) / 360.0 * mapSizePx(zoom)

/** Weltpixel-Y einer Breite bei [zoom] (Web-Mercator); Breiten werden auf den gültigen Mercator-Bereich begrenzt. */
fun latToWorldY(latitude: Double, zoom: Double): Double {
    val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(clamped)
    val sinLat = kotlin.math.sin(latRad)
    return (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * mapSizePx(zoom)
}

/** Umkehrung von [lonToWorldX]. */
fun worldXToLon(x: Double, zoom: Double): Double = x / mapSizePx(zoom) * 360.0 - 180.0

/** Umkehrung von [latToWorldY]. */
fun worldYToLat(y: Double, zoom: Double): Double {
    val fraction = y / mapSizePx(zoom)
    val arg = PI * (1 - 2 * fraction)
    val sinh = (exp(arg) - exp(-arg)) / 2.0
    return Math.toDegrees(atan(sinh))
}

/**
 * Bildschirmposition (Pixel, Ursprung oben links) von [point], wenn die Karte auf [camera]
 * zentriert ist und der Anzeigebereich [viewportWidthPx]x[viewportHeightPx] groß ist.
 */
fun screenPosition(point: LatLon, camera: MapCamera, viewportWidthPx: Int, viewportHeightPx: Int): Pair<Double, Double> {
    val centerX = lonToWorldX(camera.center.longitude, camera.zoom)
    val centerY = latToWorldY(camera.center.latitude, camera.zoom)
    val pointX = lonToWorldX(point.longitude, camera.zoom)
    val pointY = latToWorldY(point.latitude, camera.zoom)
    return (viewportWidthPx / 2.0 + (pointX - centerX)) to (viewportHeightPx / 2.0 + (pointY - centerY))
}

/** Verschiebt [camera] um ([dxPx], [dyPx]) Bildschirmpixel (Ziehen/Pan); der Zoom bleibt unverändert. */
fun panCamera(camera: MapCamera, dxPx: Double, dyPx: Double): MapCamera {
    val centerX = lonToWorldX(camera.center.longitude, camera.zoom) - dxPx
    val centerY = latToWorldY(camera.center.latitude, camera.zoom) - dyPx
    return camera.copy(center = LatLon(worldYToLat(centerY, camera.zoom), worldXToLon(centerX, camera.zoom)))
}

/** Ändert den Zoom von [camera] um [deltaZoom] Stufen, begrenzt auf [MAP_MIN_ZOOM]..[MAP_MAX_ZOOM]. */
fun zoomCamera(camera: MapCamera, deltaZoom: Double): MapCamera = camera.copy(zoom = clampZoom(camera.zoom + deltaZoom))

/**
 * Kamera, die alle [points] mit [paddingPx] Rand in den Anzeigebereich einpasst. Ein einzelner
 * Punkt (oder mehrere identische Punkte) ergibt [MAP_SINGLE_POINT_ZOOM], da es dort keine
 * Ausdehnung zum Einpassen gibt.
 */
fun fitBounds(points: List<LatLon>, viewportWidthPx: Int, viewportHeightPx: Int, paddingPx: Int): MapCamera {
    require(points.isNotEmpty()) { "fitBounds benötigt mindestens einen Punkt" }
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    if (points.size == 1 || (minLat == maxLat && minLon == maxLon)) {
        return MapCamera(LatLon(minLat, minLon), MAP_SINGLE_POINT_ZOOM)
    }

    var zoom = MAP_MAX_ZOOM
    while (zoom > MAP_MIN_ZOOM) {
        val width = lonToWorldX(maxLon, zoom.toDouble()) - lonToWorldX(minLon, zoom.toDouble())
        val height = latToWorldY(minLat, zoom.toDouble()) - latToWorldY(maxLat, zoom.toDouble())
        if (width + 2 * paddingPx <= viewportWidthPx && height + 2 * paddingPx <= viewportHeightPx) break
        zoom--
    }

    val centerX = (lonToWorldX(minLon, zoom.toDouble()) + lonToWorldX(maxLon, zoom.toDouble())) / 2
    val centerY = (latToWorldY(minLat, zoom.toDouble()) + latToWorldY(maxLat, zoom.toDouble())) / 2
    val center = LatLon(worldYToLat(centerY, zoom.toDouble()), worldXToLon(centerX, zoom.toDouble()))
    return MapCamera(center, zoom.toDouble())
}

/**
 * Sichtbare Kacheln für [camera] im Anzeigebereich [viewportWidthPx]x[viewportHeightPx], ohne
 * Vorlademarge. Der Kachel-Zoom ist der gerundete
 * Kamerazoom; die X-Koordinate wird am Datumswechsel umlaufend normalisiert, die Y-Koordinate an
 * den Polen gekappt.
 */
fun visibleTiles(camera: MapCamera, viewportWidthPx: Int, viewportHeightPx: Int): List<TileCoord> {
    val zoom = camera.zoom.roundToInt().coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
    val centerX = lonToWorldX(camera.center.longitude, zoom.toDouble())
    val centerY = latToWorldY(camera.center.latitude, zoom.toDouble())
    val tilesPerAxis = 1 shl zoom

    val minTileX = floor((centerX - viewportWidthPx / 2.0) / MAP_TILE_SIZE).toInt()
    val maxTileX = floor((centerX + viewportWidthPx / 2.0) / MAP_TILE_SIZE).toInt()
    val minTileY = floor((centerY - viewportHeightPx / 2.0) / MAP_TILE_SIZE).toInt().coerceIn(0, tilesPerAxis - 1)
    val maxTileY = floor((centerY + viewportHeightPx / 2.0) / MAP_TILE_SIZE).toInt().coerceIn(0, tilesPerAxis - 1)

    val tiles = mutableListOf<TileCoord>()
    for (x in minTileX..maxTileX) {
        val wrappedX = ((x % tilesPerAxis) + tilesPerAxis) % tilesPerAxis
        for (y in minTileY..maxTileY) {
            tiles += TileCoord(zoom, wrappedX, y)
        }
    }
    return tiles
}
