package app.restvolt.camperlog.domain

import java.text.Normalizer
import java.util.Locale

/** Ab dieser Länge (nach Trimmen) liefert die Suche Treffer; darunter zeigt die Oberfläche einen Hinweis statt Ergebnissen. */
const val SEARCH_MIN_QUERY_LENGTH = 2

/** Zeichen vor und nach dem ersten Treffer in [buildSearchSnippet]. */
private const val SEARCH_SNIPPET_CONTEXT_CHARS = 40

/**
 * Normalisiert [text] für den diakritik- und schreibweisenunabhängigen Vergleich: Kleinschreibung,
 * ß zu "ss", Unicode-NFD-Zerlegung und Entfernen der dabei entstehenden Kombinationszeichen. So wird
 * "Müritz" zu "muritz" und "SÜD" zu "sud", und beide finden sich gegenseitig.
 */
fun normalizeForSearch(text: String): String {
    val folded = text.lowercase(Locale.ROOT).replace("ß", "ss")
    return Normalizer.normalize(folded, Normalizer.Form.NFD).filterNot(::isCombiningMark)
}

private fun isCombiningMark(char: Char): Boolean = when (Character.getType(char)) {
    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

private fun searchWords(query: String): List<String> =
    normalizeForSearch(query).split(Regex("\\s+")).filter(String::isNotBlank)

/**
 * Ob [query] zu [fields] passt: Jedes Wort aus [query] (getrennt durch Leerraum) muss nach
 * [normalizeForSearch] in mindestens einem Feld aus [fields] vorkommen (UND über Wörter, ODER über
 * Felder). Eine leere oder zu kurze Suche (siehe [SEARCH_MIN_QUERY_LENGTH]) liefert nie einen Treffer.
 */
fun matchesSearch(query: String, fields: List<String>): Boolean {
    val words = searchWords(query)
    if (words.isEmpty()) return false
    val normalizedFields = fields.filter(String::isNotBlank).map(::normalizeForSearch)
    if (normalizedFields.isEmpty()) return false
    return words.all { word -> normalizedFields.any { it.contains(word) } }
}

/** Ausschnitt eines Treffers für die Ergebniszeile: [text] mit Auslassungspunkten an gekürzten Rändern, [boldRanges] die hervorzuhebenden Treffer darin. */
data class SearchSnippet(val text: String, val boldRanges: List<IntRange>)

/**
 * Baut aus [field] einen Ausschnitt um den ersten Treffer eines Worts aus [query]: bis zu
 * [contextChars] Zeichen davor und danach, mit Auslassungspunkten an gekürzten Rändern.
 * [SearchSnippet.boldRanges] sind alle Treffer innerhalb des Ausschnitts.
 *
 * @return `null`, wenn [field] kein Wort aus [query] enthält oder leer ist
 */
fun buildSearchSnippet(query: String, field: String, contextChars: Int = SEARCH_SNIPPET_CONTEXT_CHARS): SearchSnippet? {
    val words = searchWords(query)
    if (words.isEmpty() || field.isBlank()) return null

    val sourceIndexByNormalizedIndex = mutableListOf<Int>()
    val normalizedBuilder = StringBuilder()
    for ((sourceIndex, rawChar) in field.withIndex()) {
        val folded = rawChar.lowercaseChar().let { if (it == 'ß') "ss" else it.toString() }
        for (char in Normalizer.normalize(folded, Normalizer.Form.NFD)) {
            if (!isCombiningMark(char)) {
                normalizedBuilder.append(char)
                sourceIndexByNormalizedIndex.add(sourceIndex)
            }
        }
    }
    val normalized = normalizedBuilder.toString()

    val normalizedMatchRanges = words.flatMap { word -> normalized.allOccurrences(word) }
    if (normalizedMatchRanges.isEmpty()) return null
    val firstMatch = normalizedMatchRanges.minBy { it.first }

    fun sourceEndOf(normalizedIndexInclusive: Int): Int =
        if (normalizedIndexInclusive < sourceIndexByNormalizedIndex.size) sourceIndexByNormalizedIndex[normalizedIndexInclusive] + 1 else field.length

    val matchSourceStart = sourceIndexByNormalizedIndex[firstMatch.first]
    val matchSourceEnd = sourceEndOf(firstMatch.last)
    val windowStart = (matchSourceStart - contextChars).coerceAtLeast(0)
    val windowEnd = (matchSourceEnd + contextChars).coerceAtMost(field.length)

    val prefixEllipsis = if (windowStart > 0) "…" else ""
    val suffixEllipsis = if (windowEnd < field.length) "…" else ""
    val text = prefixEllipsis + field.substring(windowStart, windowEnd) + suffixEllipsis
    val offset = prefixEllipsis.length - windowStart

    val boldRanges = normalizedMatchRanges.mapNotNull { range ->
        val start = sourceIndexByNormalizedIndex[range.first]
        val end = sourceEndOf(range.last)
        if (end <= windowStart || start >= windowEnd) return@mapNotNull null
        val clippedStart = start.coerceAtLeast(windowStart)
        val clippedEnd = end.coerceAtMost(windowEnd)
        (clippedStart + offset) until (clippedEnd + offset)
    }.sortedBy { it.first }

    return SearchSnippet(text, boldRanges)
}

/** Alle Vorkommen von [needle] in [this], als inklusive Indexbereiche, nicht überlappend. */
private fun String.allOccurrences(needle: String): List<IntRange> {
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = indexOf(needle, from)
        if (index < 0) break
        ranges.add(index..(index + needle.length - 1))
        from = index + needle.length
    }
    return ranges
}
