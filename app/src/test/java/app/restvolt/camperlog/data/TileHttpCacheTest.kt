package app.restvolt.camperlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Settings "Kartenspeicher: N MB [Leeren]": Größe und Löschen auf Dateisystemebene. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TileHttpCacheTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val cacheDir get() = File(context.cacheDir, "tiles_http")

    @Test
    fun sizeBytes_sumsAllFilesInTheTileCacheDirectory() {
        cacheDir.mkdirs()
        File(cacheDir, "a").writeBytes(ByteArray(1000))
        File(cacheDir, "b").writeBytes(ByteArray(500))

        assertEquals(1500L, TileHttpCache.sizeBytes(context))
    }

    @Test
    fun sizeBytes_isZeroWhenNothingWasCachedYet() {
        cacheDir.deleteRecursively()

        assertEquals(0L, TileHttpCache.sizeBytes(context))
    }

    @Test
    fun clear_removesAllCachedTileFiles() {
        cacheDir.mkdirs()
        File(cacheDir, "a").writeBytes(ByteArray(1000))

        TileHttpCache.clear(context)

        assertEquals(0L, TileHttpCache.sizeBytes(context))
        assertTrue(cacheDir.listFiles()?.isEmpty() != false)
    }
}
