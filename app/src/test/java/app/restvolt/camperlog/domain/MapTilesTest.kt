package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kachel-URL und Fehlerschwelle (6.9, 13, ROADMAP 1.8.0). */
class MapTilesTest {

    @Test
    fun tileUrl_buildsTheExactOsmTileTemplate() {
        assertEquals("https://tile.openstreetmap.org/7/65/42.png", tileUrl(TileCoord(zoom = 7, x = 65, y = 42)))
    }

    @Test
    fun tileUrl_onlyEverTargetsTheOsmTileHost() {
        val url = java.net.URL(tileUrl(TileCoord(5, 1, 1)))
        assertEquals(OSM_TILE_HOST, url.host)
        assertEquals("https", url.protocol)
    }

    @Test
    fun shouldShowTileFailureBanner_belowThresholdIsFalse() {
        assertFalse(shouldShowTileFailureBanner(0))
        assertFalse(shouldShowTileFailureBanner(TILE_FAILURE_BANNER_THRESHOLD - 1))
    }

    @Test
    fun shouldShowTileFailureBanner_atOrAboveThresholdIsTrue() {
        assertTrue(shouldShowTileFailureBanner(TILE_FAILURE_BANNER_THRESHOLD))
        assertTrue(shouldShowTileFailureBanner(TILE_FAILURE_BANNER_THRESHOLD + 10))
    }

    @Test
    fun tileCacheMegabytes_roundsToTheNearestMegabyte() {
        assertEquals(0, tileCacheMegabytes(400_000))
        assertEquals(1, tileCacheMegabytes(600_000))
        assertEquals(300, tileCacheMegabytes(TILE_HTTP_CACHE_MAX_BYTES))
    }
}
