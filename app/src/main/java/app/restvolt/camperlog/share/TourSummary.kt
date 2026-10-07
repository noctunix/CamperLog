package app.restvolt.camperlog.share

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.costsByCategory
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.domain.period
import app.restvolt.camperlog.domain.supportedLocale
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.totalCosts
import app.restvolt.camperlog.ui.labelRes

/** Eine Zeile der Kostenaufschlüsselung einer [TourSummary] nach Kategorie. */
data class TourSummaryCostRow(val label: String, val amounts: String)

/**
 * Aufbereitete Zusammenfassung einer Tour, in der Sprache der übergebenen [android.content.res.Resources];
 * Grundlage für [tourShareText], das HTML- und das Markdown-Export. [categoryCosts] ist nach
 * Kategorie-Ordinal sortiert, [countryNames] nach Anzeigename, [wouldReturnNames] enthält nur
 * [StationType.OVERNIGHT]-Stationen mit [Station.favorite].
 */
data class TourSummary(
    val destination: String,
    val period: String,
    val tourType: String,
    val tripStats: String,
    val totalCosts: String,
    val categoryCosts: List<TourSummaryCostRow>,
    val countryNames: List<String>,
    val stopCount: Int,
    val wouldReturnNames: List<String>,
)

/** Baut [TourSummary] aus [tour], ihren [stations] und ihren bereinigten [countries] (siehe [app.restvolt.camperlog.domain.tourCountries]). */
fun tourSummary(res: Resources, tour: Tour, stations: List<Station>, countries: Set<String>): TourSummary {
    val locale = supportedLocale(res.configuration.locales[0])
    return TourSummary(
        destination = tour.destination,
        period = tour.period(locale),
        tourType = res.getString(tour.tourType.labelRes),
        tripStats = res.getString(
            R.string.share_trip_stats,
            res.getQuantityString(R.plurals.share_travel_days, tour.travelDays, tour.travelDays),
            res.getQuantityString(R.plurals.share_overnight_stays, tour.overnightStays, tour.overnightStays),
            res.getString(R.string.distance_km, tour.distanceKm),
        ),
        totalCosts = formatAmounts(tour.totalCosts(stations), locale),
        categoryCosts = stations.costsByCategory().entries.sortedBy { it.key.ordinal }
            .map { (category, amounts) -> TourSummaryCostRow(res.getString(category.labelRes), formatAmounts(amounts, locale)) },
        countryNames = countries.sortedBy { countryDisplayName(it, locale) }.map { countryDisplayName(it, locale) },
        stopCount = stations.size,
        wouldReturnNames = stations.filter { it.type == StationType.OVERNIGHT && it.favorite }
            .map { it.name.ifBlank { res.getString(it.type.labelRes) } },
    )
}
