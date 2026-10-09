package app.restvolt.camperlog.domain.guide

/**
 * Eine vollständige geführte Tour. [version] erhöht sich, wenn sich der Ablauf inhaltlich ändert,
 * damit eine bereits abgeschlossene ältere Version erneut angezeigt wird.
 *
 * @throws IllegalArgumentException wenn [steps] leer ist oder Schritt-IDs doppelt vorkommen.
 */
data class TourDefinition(
    val id: String,
    val version: Int,
    val steps: List<TourStep>,
) {
    init {
        require(steps.isNotEmpty()) { "Tour '$id' braucht mindestens einen Schritt." }
        val duplicateIds = steps.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) { "Tour '$id' hat doppelte Schritt-IDs: $duplicateIds" }
    }
}
