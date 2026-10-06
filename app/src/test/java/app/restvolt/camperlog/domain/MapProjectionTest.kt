package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web-Mercator-Projektion und Einpassen: reine Mathematik, kein Android. */
class MapProjectionTest {

    @Test
    fun lonToWorldX_centerOfTheMapIsHalfTheWorldSize() {
        assertEquals(128.0, lonToWorldX(0.0, 0.0), 1e-9)
        assertEquals(0.0, lonToWorldX(-180.0, 0.0), 1e-9)
        assertEquals(256.0, lonToWorldX(180.0, 0.0), 1e-9)
    }

    @Test
    fun latToWorldY_equatorIsHalfTheWorldSize() {
        assertEquals(128.0, latToWorldY(0.0, 0.0), 1e-9)
    }

    @Test
    fun clampZoom_keepsValuesInsideTheRangeAndClampsOutside() {
        assertEquals(MAP_MIN_ZOOM.toDouble(), clampZoom(MAP_MIN_ZOOM - 5.0), 1e-9)
        assertEquals(MAP_MAX_ZOOM.toDouble(), clampZoom(MAP_MAX_ZOOM + 5.0), 1e-9)
        assertEquals(10.0, clampZoom(10.0), 1e-9)
    }

    /** Europäische Koordinaten, keine Datumsgrenze: Hin- und Rücktransformation muss den Punkt wiederfinden. */
    private val europeanPoints = listOf(
        LatLon(68.0912, 13.1023), // Lofoten
        LatLon(53.5511, 9.9937), // Hamburg
        LatLon(54.7937, 9.4464), // Flensburg
        LatLon(0.0, 0.0),
        LatLon(-34.6037, -58.3816), // Buenos Aires, zur Kontrolle auch außerhalb Europas
        LatLon(63.4305, 10.3951), // Trondheim
    )

    @Test
    fun lonLatRoundTrip_recoversTheOriginalCoordinateAtEveryZoomLevel() {
        for (zoom in MAP_MIN_ZOOM..MAP_MAX_ZOOM) {
            for (point in europeanPoints) {
                val x = lonToWorldX(point.longitude, zoom.toDouble())
                val y = latToWorldY(point.latitude, zoom.toDouble())
                assertEquals("lon at zoom $zoom", point.longitude, worldXToLon(x, zoom.toDouble()), 1e-6)
                assertEquals("lat at zoom $zoom", point.latitude, worldYToLat(y, zoom.toDouble()), 1e-6)
            }
        }
    }

    @Test
    fun panCamera_screenPositionOfTheOldCenterMovesByThePanAmount() {
        val camera = MapCamera(LatLon(54.0, 10.0), 8.0)
        val panned = panCamera(camera, 50.0, -30.0)

        val (x, y) = screenPosition(camera.center, panned, 1000, 1000)
        assertEquals(500.0 + 50.0, x, 1e-6)
        assertEquals(500.0 - 30.0, y, 1e-6)
    }

    @Test
    fun zoomCamera_addsDeltaAndClamps() {
        val camera = MapCamera(LatLon(0.0, 0.0), 10.0)
        assertEquals(11.0, zoomCamera(camera, 1.0).zoom, 1e-9)
        assertEquals(MAP_MAX_ZOOM.toDouble(), zoomCamera(camera, 100.0).zoom, 1e-9)
        assertEquals(MAP_MIN_ZOOM.toDouble(), zoomCamera(camera, -100.0).zoom, 1e-9)
    }

    @Test
    fun fitBounds_singlePointUsesTheFixedSingleStationZoom() {
        val point = LatLon(68.0912, 13.1023)
        val camera = fitBounds(listOf(point), 1000, 2000, 48)

        assertEquals(point, camera.center)
        assertEquals(MAP_SINGLE_POINT_ZOOM, camera.zoom, 1e-9)
    }

    @Test
    fun fitBounds_identicalPointsBehaveLikeASinglePoint() {
        val point = LatLon(54.7937, 9.4464)
        val camera = fitBounds(listOf(point, point, point), 800, 600, 48)

        assertEquals(point, camera.center)
        assertEquals(MAP_SINGLE_POINT_ZOOM, camera.zoom, 1e-9)
    }

    @Test
    fun fitBounds_zoomStaysWithinTheAllowedRange() {
        // Zwei Punkte quer durch Europa: Lofoten und Flensburg.
        val points = listOf(LatLon(68.0912, 13.1023), LatLon(54.7937, 9.4464))
        val camera = fitBounds(points, 360, 640, 48)

        assertTrue(camera.zoom >= MAP_MIN_ZOOM)
        assertTrue(camera.zoom <= MAP_MAX_ZOOM)
    }

    @Test
    fun fitBounds_fitsAllPointsWithinTheViewportMinusPadding() {
        val viewportWidth = 400
        val viewportHeight = 800
        val padding = 48
        val cases = listOf(
            listOf(LatLon(68.0912, 13.1023), LatLon(54.7937, 9.4464)), // Norwegen - Flensburg, weite Strecke
            listOf(LatLon(53.5511, 9.9937), LatLon(53.55, 10.0)), // zwei dicht beieinanderliegende Punkte in Hamburg
            listOf(LatLon(47.0, 8.0), LatLon(47.01, 8.02), LatLon(46.99, 7.98)), // Schweiz, mehrere Stationen
        )
        for (points in cases) {
            val camera = fitBounds(points, viewportWidth, viewportHeight, padding)
            for (point in points) {
                val (x, y) = screenPosition(point, camera, viewportWidth, viewportHeight)
                assertTrue("x=$x should be within [$padding, ${viewportWidth - padding}]", x >= padding - 1.0 && x <= viewportWidth - padding + 1.0)
                assertTrue("y=$y should be within [$padding, ${viewportHeight - padding}]", y >= padding - 1.0 && y <= viewportHeight - padding + 1.0)
            }
        }
    }

    @Test
    fun visibleTiles_coversTheViewportAroundTheCameraCenter() {
        val camera = MapCamera(LatLon(54.0, 10.0), 6.0)
        val tiles = visibleTiles(camera, 512, 512)

        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.zoom == 6 })
        val tilesPerAxis = 1 shl 6
        assertTrue(tiles.all { it.x in 0 until tilesPerAxis && it.y in 0 until tilesPerAxis })
    }

    @Test
    fun visibleTiles_wrapsXAroundTheAntimeridian() {
        val camera = MapCamera(LatLon(0.0, 179.9), 3.0)
        val tiles = visibleTiles(camera, 1024, 256)

        val tilesPerAxis = 1 shl 3
        assertTrue(tiles.all { it.x in 0 until tilesPerAxis })
    }
}
