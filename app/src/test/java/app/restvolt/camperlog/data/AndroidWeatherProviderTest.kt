package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.OPEN_METEO_HOST
import app.restvolt.camperlog.domain.WEATHER_TIMEOUT_MS
import app.restvolt.camperlog.domain.WeatherResult
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

/** Fälschung von `HttpURLConnection`, ohne Netzwerk: siehe `AndroidWeatherProvider.openConnection`. */
private class FakeHttpUrlConnection(
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

private const val VALID_BODY = """{"current":{"time":1730890800,"temperature_2m":14.3,"weather_code":1,"wind_speed_10m":18,"wind_gusts_10m":35,"wind_direction_10m":270}}"""

class AndroidWeatherProviderTest {

    private fun provider(connection: (URL) -> HttpURLConnection) = AndroidWeatherProvider(userAgent = "CamperLog/1.7.0 (+https://github.com/noctunix/CamperLog)", openConnection = connection)

    @Test
    fun fetchCurrent_200_parsesTheSnapshotAndIgnoresUnknownKeys() = runTest {
        val bodyWithUnknownKey = """{"elevation":12,"current":{"time":1730890800,"temperature_2m":14.3,"weather_code":1,"wind_speed_10m":18,"wind_gusts_10m":35,"wind_direction_10m":270,"surprise_field":true}}"""
        val result = provider { url -> FakeHttpUrlConnection(url, fakedBody = bodyWithUnknownKey) }.fetchCurrent(68.0912, 13.1023)

        val success = result as WeatherResult.Success
        assertEquals(143, success.snapshot.temperatureDeciC)
        assertEquals(1, success.snapshot.weatherCode)
        assertEquals(18, success.snapshot.windKmh)
        assertEquals(35, success.snapshot.gustKmh)
        assertEquals(270, success.snapshot.windDirectionDeg)
        assertEquals(1730890800L, success.snapshot.observedAt.epochSecond)
    }

    @Test
    fun fetchCurrent_429_isRateLimited() = runTest {
        val result = provider { url -> FakeHttpUrlConnection(url, fakedResponseCode = 429) }.fetchCurrent(68.0912, 13.1023)

        assertEquals(WeatherResult.RateLimited, result)
    }

    @Test
    fun fetchCurrent_timeout_isError() = runTest {
        val result = provider { url -> FakeHttpUrlConnection(url, failWith = SocketTimeoutException("timeout")) }.fetchCurrent(68.0912, 13.1023)

        assertEquals(WeatherResult.Error, result)
    }

    @Test
    fun fetchCurrent_unknownHost_isOffline() = runTest {
        val result = provider { url -> FakeHttpUrlConnection(url, failWith = UnknownHostException("no dns")) }.fetchCurrent(68.0912, 13.1023)

        assertEquals(WeatherResult.Offline, result)
    }

    @Test
    fun fetchCurrent_malformedJson_isError() = runTest {
        val result = provider { url -> FakeHttpUrlConnection(url, fakedBody = "not json") }.fetchCurrent(68.0912, 13.1023)

        assertEquals(WeatherResult.Error, result)
    }

    @Test
    fun fetchCurrent_redirectResponse_isErrorRatherThanFollowed() = runTest {
        val result = provider { url -> FakeHttpUrlConnection(url, fakedResponseCode = 302) }.fetchCurrent(68.0912, 13.1023)

        assertEquals(WeatherResult.Error, result)
    }

    @Test
    fun fetchCurrent_onlyEverTargetsTheOpenMeteoHost() = runTest {
        var requestedHost: String? = null
        provider { url ->
            requestedHost = url.host
            FakeHttpUrlConnection(url, fakedBody = VALID_BODY)
        }.fetchCurrent(68.0912, 13.1023)

        assertEquals(OPEN_METEO_HOST, requestedHost)
    }

    @Test
    fun fetchCurrent_setsTimeoutsRedirectPolicyAndUserAgent() = runTest {
        var captured: FakeHttpUrlConnection? = null
        provider { url ->
            FakeHttpUrlConnection(url, fakedBody = VALID_BODY).also { captured = it }
        }.fetchCurrent(68.0912, 13.1023)

        val connection = requireNotNull(captured)
        assertEquals(WEATHER_TIMEOUT_MS, connection.connectTimeout)
        assertEquals(WEATHER_TIMEOUT_MS, connection.readTimeout)
        assertTrue(connection.getRequestProperty("User-Agent").startsWith("CamperLog/"))
        assertEquals(false, connection.instanceFollowRedirects)
    }
}
