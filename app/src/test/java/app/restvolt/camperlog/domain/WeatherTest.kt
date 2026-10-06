package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class WeatherTest {

    @Test
    fun roundCoordinate_roundsToTwoDecimals() {
        assertEquals(68.09, roundCoordinate(68.0912), 0.0)
        assertEquals(13.1, roundCoordinate(13.1023), 0.0)
        assertEquals(-5.35, roundCoordinate(-5.3499999), 0.0)
    }

    @Test
    fun buildWeatherRequestUrl_roundsCoordinatesAndUsesTheDocumentedQuery() {
        val url = buildWeatherRequestUrl(68.0912, 13.1023)

        assertEquals(OPEN_METEO_HOST, url.host)
        assertEquals("https", url.protocol)
        assertEquals(
            "/v1/forecast?latitude=68.09&longitude=13.10" +
                "&current=temperature_2m,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m" +
                "&timeformat=unixtime&wind_speed_unit=kmh",
            url.file,
        )
    }

    @Test
    fun buildWeatherRequestUrl_formatsNegativeCoordinatesWithoutLocaleGrouping() {
        val url = buildWeatherRequestUrl(-5.3, -170.5)

        assertEquals(true, url.file.contains("latitude=-5.30"))
        assertEquals(true, url.file.contains("longitude=-170.50"))
    }

    @Test
    fun weatherCondition_mapsEveryDocumentedWmoCode() {
        assertEquals(WeatherCondition.CLEAR, weatherCondition(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, weatherCondition(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, weatherCondition(2))
        assertEquals(WeatherCondition.OVERCAST, weatherCondition(3))
        assertEquals(WeatherCondition.FOG, weatherCondition(45))
        assertEquals(WeatherCondition.FOG, weatherCondition(48))
        assertEquals(WeatherCondition.RAIN, weatherCondition(51))
        assertEquals(WeatherCondition.RAIN, weatherCondition(65))
        assertEquals(WeatherCondition.FREEZING_RAIN, weatherCondition(66))
        assertEquals(WeatherCondition.FREEZING_RAIN, weatherCondition(67))
        assertEquals(WeatherCondition.RAIN, weatherCondition(80))
        assertEquals(WeatherCondition.RAIN, weatherCondition(82))
        assertEquals(WeatherCondition.SNOW, weatherCondition(71))
        assertEquals(WeatherCondition.SNOW, weatherCondition(77))
        assertEquals(WeatherCondition.SNOW, weatherCondition(85))
        assertEquals(WeatherCondition.SNOW, weatherCondition(86))
        assertEquals(WeatherCondition.THUNDERSTORM, weatherCondition(95))
        assertEquals(WeatherCondition.THUNDERSTORM, weatherCondition(99))
    }

    @Test
    fun weatherCondition_fallsBackToOvercastForUnknownCodes() {
        assertEquals(WeatherCondition.OVERCAST, weatherCondition(-1))
        assertEquals(WeatherCondition.OVERCAST, weatherCondition(100))
    }

    @Test
    fun compassDirection_mapsDegreesToTheNearestOfEightPoints() {
        assertEquals(CompassDirection.N, compassDirection(0))
        assertEquals(CompassDirection.N, compassDirection(359))
        assertEquals(CompassDirection.NE, compassDirection(45))
        assertEquals(CompassDirection.E, compassDirection(90))
        assertEquals(CompassDirection.SE, compassDirection(135))
        assertEquals(CompassDirection.S, compassDirection(180))
        assertEquals(CompassDirection.SW, compassDirection(225))
        assertEquals(CompassDirection.W, compassDirection(270))
        assertEquals(CompassDirection.NW, compassDirection(315))
    }

    @Test
    fun formatObservedTime_usesTheGivenZone() {
        val instant = Instant.parse("2026-07-04T18:30:00Z")

        assertEquals("18:30", formatObservedTime(instant, ZoneOffset.UTC))
        assertEquals("20:30", formatObservedTime(instant, ZoneOffset.ofHours(2)))
    }
}
