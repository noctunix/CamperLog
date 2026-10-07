package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchTest {

    @Test
    fun buildPlaceSearchRequestUrl_usesTheDocumentedQuery() {
        val url = buildPlaceSearchRequestUrl("Lofoten", "de")

        assertEquals(NOMINATIM_HOST, url.host)
        assertEquals("https", url.protocol)
        assertEquals(
            "/search?format=jsonv2&q=Lofoten&limit=5&accept-language=de&addressdetails=0",
            url.file,
        )
    }

    @Test
    fun buildPlaceSearchRequestUrl_encodesSpacesAndSpecialCharacters() {
        val url = buildPlaceSearchRequestUrl("Camping Moskenes & Søn", "en")

        assertEquals(true, url.file.contains("q=Camping+Moskenes+%26+S%C3%B8n"))
        assertEquals(true, url.file.contains("accept-language=en"))
    }

    @Test
    fun buildPlaceSearchRequestUrl_limitsToFiveResults() {
        val url = buildPlaceSearchRequestUrl("Reine", "de")

        assertEquals(true, url.file.contains("limit=5"))
    }
}
