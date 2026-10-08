package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.TILE_MAX_STALE_SECONDS
import app.restvolt.camperlog.domain.TileCoord
import app.restvolt.camperlog.domain.TileLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Fälschung von `HttpURLConnection`, ohne Netzwerk: siehe `AndroidWeatherProviderTest`. */
private class FakeTileHttpUrlConnection(
    url: URL,
    private val fakedResponseCode: Int = HttpURLConnection.HTTP_OK,
    private val fakedBody: ByteArray = ByteArray(0),
    private val onResponseCode: () -> Unit = {},
    private val failWith: Exception? = null,
) : HttpURLConnection(url) {
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int {
        onResponseCode()
        failWith?.let { throw it }
        return fakedResponseCode
    }

    override fun getInputStream(): InputStream {
        failWith?.let { throw it }
        return ByteArrayInputStream(fakedBody)
    }
}

class AndroidTileLoaderTest {

    private val tile = TileCoord(zoom = 7, x = 65, y = 42)

    @Test
    fun load_200_returnsTheRawBytes() = runTest {
        val body = byteArrayOf(1, 2, 3, 4)
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0 (+https://github.com/noctunix/CamperLog)") { url ->
            FakeTileHttpUrlConnection(url, fakedBody = body)
        }

        val result = loader.load(tile)

        val success = result as TileLoadResult.Success
        assertTrue(success.pngBytes.contentEquals(body))
    }

    @Test
    fun load_notFound_isFailure() = runTest {
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0") { url ->
            FakeTileHttpUrlConnection(url, fakedResponseCode = 404)
        }

        assertEquals(TileLoadResult.Failure, loader.load(tile))
    }

    @Test
    fun load_oversizedBody_isFailure() = runTest {
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0") { url ->
            FakeTileHttpUrlConnection(url, fakedBody = ByteArray(MAX_TILE_RESPONSE_BYTES + 1))
        }

        assertEquals(TileLoadResult.Failure, loader.load(tile))
    }

    @Test
    fun load_ioException_isFailure() = runTest {
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0") { url ->
            FakeTileHttpUrlConnection(url, failWith = IOException("broken pipe"))
        }

        assertEquals(TileLoadResult.Failure, loader.load(tile))
    }

    @Test
    fun load_setsAnIdentifyingUserAgent() = runTest {
        var captured: FakeTileHttpUrlConnection? = null
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0 (+https://github.com/noctunix/CamperLog)") { url ->
            FakeTileHttpUrlConnection(url).also { captured = it }
        }

        loader.load(tile)

        assertEquals("CamperLog/1.7.0 (+https://github.com/noctunix/CamperLog)", captured!!.getRequestProperty("User-Agent"))
    }

    @Test
    fun load_sendsMaxStaleCacheControlAndNeverNoCache() = runTest {
        var captured: FakeTileHttpUrlConnection? = null
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0") { url ->
            FakeTileHttpUrlConnection(url).also { captured = it }
        }

        loader.load(tile)

        val cacheControl = captured!!.getRequestProperty("Cache-Control")
        assertEquals("max-stale=$TILE_MAX_STALE_SECONDS", cacheControl)
        assertFalse(cacheControl.contains("no-cache"))
    }

    @Test
    fun load_doesNotFollowRedirectsAutomatically() = runTest {
        var captured: FakeTileHttpUrlConnection? = null
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0") { url ->
            FakeTileHttpUrlConnection(url).also { captured = it }
        }

        loader.load(tile)

        assertFalse(captured!!.instanceFollowRedirects)
    }

    @Test
    fun load_limitsConcurrentRequestsToTheConfiguredMaximum() = runBlocking {
        val maxConcurrent = 2
        val active = AtomicInteger(0)
        val startedLatch = CountDownLatch(maxConcurrent)
        val releaseLatch = CountDownLatch(1)
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0", maxConcurrent = maxConcurrent) { url ->
            FakeTileHttpUrlConnection(
                url,
                onResponseCode = {
                    active.incrementAndGet()
                    startedLatch.countDown()
                    releaseLatch.await(2, TimeUnit.SECONDS)
                    active.decrementAndGet()
                },
            )
        }

        val jobs = (0 until 5).map { index ->
            async(Dispatchers.Default) { loader.load(TileCoord(5, index, index)) }
        }
        assertTrue("expected $maxConcurrent requests to start", startedLatch.await(2, TimeUnit.SECONDS))
        assertEquals(maxConcurrent, active.get())

        releaseLatch.countDown()
        jobs.forEach { it.await() }
    }

    @Test
    fun cancellingAQueuedRequest_neverCallsTheNetwork() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCallCount = AtomicInteger(0)
        val loader = AndroidTileLoader(userAgent = "CamperLog/1.7.0", maxConcurrent = 1) { url ->
            FakeTileHttpUrlConnection(
                url,
                onResponseCode = {
                    if (url.toString().endsWith("/0/0.png")) {
                        firstStarted.countDown()
                        releaseFirst.await(2, TimeUnit.SECONDS)
                    } else {
                        secondCallCount.incrementAndGet()
                    }
                },
            )
        }

        val firstJob = launch(Dispatchers.Default) { loader.load(TileCoord(5, 0, 0)) }
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

        // Die zweite Kachel ist noch nicht an der Reihe (maxConcurrent=1); ihr Abbruch simuliert
        // eine Kachel, die den sichtbaren Ausschnitt verlassen hat, bevor sie geladen wurde.
        val secondJob = launch(Dispatchers.Default) { loader.load(TileCoord(5, 1, 1)) }
        delay(50)
        secondJob.cancel()
        secondJob.join()

        releaseFirst.countDown()
        firstJob.join()

        assertEquals(0, secondCallCount.get())
    }
}
