package app.restvolt.camperlog.domain

import java.time.LocalDate

/** Eine Vignette, die vor [Tour.endDate] abläuft (siehe [expiringVignettes]). */
data class ExpiringVignette(
    val stationId: Long,
    val countryCode: String?,
    val validUntil: LocalDate,
)

/**
 * Vignetten der Tour, die vor [Tour.endDate] ablaufen. Je Land zählt nur die späteste Vignette:
 * eine später gekaufte Anschlussvignette deckt den Rest der Tour ab, dann entfällt die Warnung
 * für dieses Land. Vignetten ohne Land werden einzeln bewertet.
 */
fun expiringVignettes(tour: Tour, stations: List<Station>): List<ExpiringVignette> {
    val tourEnd = tour.endDate ?: return emptyList()
    val vignettes = stations.filter { it.type == StationType.TOLL && it.tollKind == TollKind.VIGNETTE && it.tollValidUntil != null }
    val (withCountry, withoutCountry) = vignettes.partition { it.tollCountry != null }

    val latestPerCountry = withCountry.groupBy { it.tollCountry }.values
        .map { group -> group.maxBy { it.tollValidUntil!! } }
        .filter { it.tollValidUntil!! < tourEnd }

    val expiringWithoutCountry = withoutCountry.filter { it.tollValidUntil!! < tourEnd }

    return (latestPerCountry + expiringWithoutCountry)
        .map { ExpiringVignette(it.id, it.tollCountry, it.tollValidUntil!!) }
        .sortedBy { it.validUntil }
}
