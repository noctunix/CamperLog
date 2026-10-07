package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    @Test
    fun normalizeForSearch_stripsDiacriticsCaseAndEszett() {
        assertEquals("muritz", normalizeForSearch("Müritz"))
        assertEquals("sud", normalizeForSearch("SÜD"))
        assertEquals("grossglockner", normalizeForSearch("Großglockner"))
    }

    @Test
    fun matchesSearch_findsDiacriticInsensitiveWord() {
        assertTrue(matchesSearch("Muritz", listOf("Wir waren an der Müritz")))
        assertTrue(matchesSearch("SÜD", listOf("Camping im Süden")))
        assertTrue(matchesSearch("gross", listOf("Großglockner")))
    }

    @Test
    fun matchesSearch_isCaseInsensitive() {
        assertTrue(matchesSearch("gardasee", listOf("Gardasee")))
        assertTrue(matchesSearch("GARDASEE", listOf("gardasee")))
    }

    @Test
    fun matchesSearch_requiresEveryWordButAnyField() {
        assertTrue(matchesSearch("ruhiger platz", listOf("Ruhiger", "Schöner Platz am See")))
        assertFalse(matchesSearch("ruhiger strand", listOf("Ruhiger", "Schöner Platz am See")))
    }

    @Test
    fun matchesSearch_blankOrShortQueryNeverMatches() {
        assertFalse(matchesSearch("", listOf("Gardasee")))
        assertFalse(matchesSearch("   ", listOf("Gardasee")))
    }

    @Test
    fun matchesSearch_ignoresBlankFields() {
        assertFalse(matchesSearch("gardasee", listOf("", "   ")))
    }

    @Test
    fun buildSearchSnippet_returnsNullWithoutMatch() {
        assertNull(buildSearchSnippet("gardasee", "Schöner Platz am Ostsee"))
    }

    @Test
    fun buildSearchSnippet_returnsNullForBlankQueryOrField() {
        assertNull(buildSearchSnippet("", "Gardasee"))
        assertNull(buildSearchSnippet("garda", ""))
    }

    @Test
    fun buildSearchSnippet_shortFieldHasNoEllipsesAndBoldsTheMatch() {
        val snippet = checkNotNull(buildSearchSnippet("garda", "Schöner Platz am Gardasee", contextChars = 40))

        assertEquals("Schöner Platz am Gardasee", snippet.text)
        assertEquals(listOf(17..21), snippet.boldRanges)
        assertEquals("Garda", snippet.text.substring(snippet.boldRanges.single().first, snippet.boldRanges.single().last + 1))
    }

    @Test
    fun buildSearchSnippet_addsEllipsesWhenFieldExceedsContextOnBothSides() {
        val prefix = "x".repeat(60)
        val suffix = "y".repeat(60)
        val field = "$prefix GARDASEE $suffix"

        val snippet = checkNotNull(buildSearchSnippet("gardasee", field, contextChars = 10))

        assertTrue("sollte mit Auslassungspunkten beginnen: ${snippet.text}", snippet.text.startsWith("…"))
        assertTrue("sollte mit Auslassungspunkten enden: ${snippet.text}", snippet.text.endsWith("…"))
        val boldRange = snippet.boldRanges.single()
        assertEquals("GARDASEE", snippet.text.substring(boldRange.first, boldRange.last + 1))
    }

    @Test
    fun buildSearchSnippet_noEllipsisWhenMatchIsAtTheVeryStartOrEnd() {
        val startSnippet = checkNotNull(buildSearchSnippet("garda", "Gardasee ist schön", contextChars = 5))
        assertFalse(startSnippet.text.startsWith("…"))

        val endSnippet = checkNotNull(buildSearchSnippet("SCHÖN", "Ein Urlaub, der schön war", contextChars = 5))
        assertTrue(endSnippet.text.endsWith("war"))
    }

    @Test
    fun buildSearchSnippet_boldsEveryMatchingWordWithinTheWindow() {
        val snippet = checkNotNull(buildSearchSnippet("ruhig platz", "Ein ruhiger Platz direkt am See", contextChars = 40))

        assertEquals(2, snippet.boldRanges.size)
        val texts = snippet.boldRanges.map { snippet.text.substring(it.first, it.last + 1) }
        assertEquals(listOf("ruhig", "Platz"), texts)
    }

    @Test
    fun buildSearchSnippet_matchesAcrossDiacriticsAndCase() {
        val snippet = checkNotNull(buildSearchSnippet("MURITZ", "Wir waren an der Müritz im Sommer"))
        val boldRange = snippet.boldRanges.single()
        assertEquals("Müritz", snippet.text.substring(boldRange.first, boldRange.last + 1))
    }
}
