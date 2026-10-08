package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.NOMINATIM_HOST
import app.restvolt.camperlog.domain.PLACE_SEARCH_TIMEOUT_MS
import app.restvolt.camperlog.domain.PlaceSearchHit
import app.restvolt.camperlog.domain.PlaceSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Fälschung von `HttpURLConnection`, ohne Netzwerk: siehe `AndroidWeatherProviderTest`. */
private class FakeNominatimHttpUrlConnection(
    url: URL,
    private val fakedResponseCode: Int = HttpURLConnection.HTTP_OK,
    private val fakedBody: String = "",
    private val failWith: Exception? = null,
) : HttpURLConnection(url) {
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int {
        failWith?.let { throw it }
        return fakedResponseCode
    }

    override fun getInputStream(): InputStream {
        failWith?.let { throw it }
        return ByteArrayInputStream(fakedBody.toByteArray())
    }
}

private const val VALID_BODY =
    """[{"place_id":1,"lat":"68.0912155","lon":"13.1023456","display_name":"Reine, Moskenes, Nordland, Norway","category":"place","type":"village"}]"""

class AndroidPlaceSearchProviderTest {

    private fun provider(
        now: () -> Long = { 0L },
        sleep: suspend (Long) -> Unit = {},
        connection: (URL) -> HttpURLConnection,
    ) = AndroidPlaceSearchProvider(
        userAgent = "CamperLog/1.7.0 (+https://github.com/noctunix/CamperLog)",
        openConnection = connection,
        now = now,
        sleep = sleep,
    )

    @Test
    fun search_200_parsesTheHitsAndIgnoresUnknownKeys() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = VALID_BODY) }.search("Reine", "de")

        val success = result as PlaceSearchResult.Success
        assertEquals(
            listOf(PlaceSearchHit("Reine, Moskenes, Nordland, Norway", "place", "village", 68.0912155, 13.1023456)),
            success.hits,
        )
    }

    @Test
    fun search_entryWithoutCoordinates_isSkippedRatherThanFailingTheWholeSearch() = runTest {
        val body = """[
            {"lat":"not-a-number","lon":"13.1023456","display_name":"Broken entry"},
            {"lat":"68.0912155","lon":"13.1023456","display_name":"Reine"}
        ]"""
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = body) }.search("Reine", "de")

        val success = result as PlaceSearchResult.Success
        assertEquals(listOf("Reine"), success.hits.map { it.displayName })
    }

    @Test
    fun search_entryWithoutDisplayName_isSkipped() = runTest {
        val body = """[
            {"lat":"68.0912155","lon":"13.1023456"},
            {"lat":"68.0912155","lon":"13.1023456","display_name":"Reine"}
        ]"""
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = body) }.search("Reine", "de")

        val success = result as PlaceSearchResult.Success
        assertEquals(listOf("Reine"), success.hits.map { it.displayName })
    }

    @Test
    fun search_emptyArray_isSuccessWithNoHits() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = "[]") }.search("Nonexistentplace", "de")

        assertEquals(PlaceSearchResult.Success(emptyList()), result)
    }

    @Test
    fun search_429_isRateLimited() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedResponseCode = 429) }.search("Reine", "de")

        assertEquals(PlaceSearchResult.RateLimited, result)
    }

    @Test
    fun search_timeout_isError() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, failWith = SocketTimeoutException("timeout")) }.search("Reine", "de")

        assertEquals(PlaceSearchResult.Error, result)
    }

    @Test
    fun search_unknownHost_isOffline() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, failWith = UnknownHostException("no dns")) }.search("Reine", "de")

        assertEquals(PlaceSearchResult.Offline, result)
    }

    @Test
    fun search_oversizedBody_isError() = runTest {
        // Gültiges JSON, nur mit Leerraum über das Limit aufgebläht: der Abbruch kommt vom Limit.
        val body = VALID_BODY + " ".repeat(MAX_JSON_RESPONSE_BYTES)
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = body) }.search("Reine", "de")

        assertEquals(PlaceSearchResult.Error, result)
    }

    @Test
    fun search_malformedJson_isError() = runTest {
        val result = provider { url -> FakeNominatimHttpUrlConnection(url, fakedBody = "not json") }.search("Reine", "de")

        assertEquals(PlaceSearchResult.Error, result)
    }

    @Test
    fun search_onlyEverTargetsTheNominatimHost() = runTest {
        var requestedHost: String? = null
        provider { url ->
            requestedHost = url.host
            FakeNominatimHttpUrlConnection(url, fakedBody = VALID_BODY)
        }.search("Reine", "de")

        assertEquals(NOMINATIM_HOST, requestedHost)
    }

    @Test
    fun search_setsTimeoutsRedirectPolicyAndUserAgent() = runTest {
        var captured: FakeNominatimHttpUrlConnection? = null
        provider { url ->
            FakeNominatimHttpUrlConnection(url, fakedBody = VALID_BODY).also { captured = it }
        }.search("Reine", "de")

        val connection = requireNotNull(captured)
        assertEquals(PLACE_SEARCH_TIMEOUT_MS, connection.connectTimeout)
        assertEquals(PLACE_SEARCH_TIMEOUT_MS, connection.readTimeout)
        assertTrue(connection.getRequestProperty("User-Agent").startsWith("CamperLog/"))
        assertEquals(false, connection.instanceFollowRedirects)
    }

    @Test
    fun search_withinOneSecondOfThePreviousRequest_waitsForTheRemainder() = runTest {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val search = provider(
            connection = { url -> FakeNominatimHttpUrlConnection(url, fakedBody = VALID_BODY) },
            now = { now },
            sleep = { sleeps.add(it) },
        )

        search.search("Reine", "de")
        now = 1_200L
        search.search("Reine", "de")

        assertEquals(listOf(800L), sleeps)
    }

    @Test
    fun search_atLeastOneSecondAfterThePreviousRequest_doesNotWait() = runTest {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val search = provider(
            connection = { url -> FakeNominatimHttpUrlConnection(url, fakedBody = VALID_BODY) },
            now = { now },
            sleep = { sleeps.add(it) },
        )

        search.search("Reine", "de")
        now = 2_500L
        search.search("Reine", "de")

        assertEquals(emptyList<Long>(), sleeps)
    }
}
