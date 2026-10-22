package app.restvolt.camperlog.domain

/** Kostenkategorie einer Station, mit stabilem Exportwert [csvValue]. */
enum class CostCategory(val csvValue: String) {
    PITCH("stellplatz"),
    ELECTRICITY("strom"),
    SUPPLY("ver_entsorgung"),
    FUEL("tanken_laden"),
    TOLL("maut"),
    FERRY("faehre"),
    FOOD("essen"),
    OTHER("sonstiges"),
    WIFI("wlan"),
}

/** Vorgabe-Kategorie der ersten Kostenzeile je Stationsart. */
val StationType.defaultCostCategory: CostCategory
    get() = when (this) {
        StationType.OVERNIGHT -> CostCategory.PITCH
        StationType.SUPPLY -> CostCategory.SUPPLY
        StationType.FUEL -> CostCategory.FUEL
        StationType.TOLL -> CostCategory.TOLL
        StationType.FERRY -> CostCategory.FERRY
        StationType.FOOD -> CostCategory.FOOD
        StationType.SIGHT, StationType.OTHER -> CostCategory.OTHER
    }

/**
 * Ein manuell erfasster Kostenposten einer Station. Je Station höchstens ein Eintrag je
 * ([category], Währung von [amount]), wie bei den Tourkosten je Währung.
 */
data class StationCost(
    val category: CostCategory,
    val amount: Money,
    val note: String = "",
)

/**
 * Alle Kosten dieser Station: manuelle Posten sowie Strom- und WLAN-Beträge, sofern diese als Aufwand
 * markiert sind. Abgeleitete Posten bleiben durch ihre Kategorie als Herkunft erkennbar.
 */
fun Station.effectiveCosts(): List<StationCost> {
    val derived = buildList {
        electricityCost(this@effectiveCosts)?.let { add(StationCost(CostCategory.ELECTRICITY, it)) }
        wifiCost?.takeIf { wifiCostIsExpense }?.let { add(StationCost(CostCategory.WIFI, it)) }
    }
    if (derived.isEmpty()) return costs
    return (costs + derived)
        .groupBy { it.category to it.amount.currency }
        .map { (key, group) -> StationCost(key.first, Money(group.map(StationCost::amount).sumMinor(), key.second), group.first().note) }
}

/** Summe der [effectiveCosts] mehrerer Stationen je Währung, sortiert nach Code. */
fun Iterable<Station>.stationCostTotals(): List<Money> = flatMap { it.effectiveCosts() }.map(StationCost::amount).sumByCurrency()

/** Kosten mehrerer Stationen je Kategorie, über alle Währungen; für eine Aufschlüsselung in der Oberfläche. */
fun Iterable<Station>.costsByCategory(): Map<CostCategory, List<Money>> =
    flatMap { it.effectiveCosts() }.groupBy(StationCost::category) { it.amount }.mapValues { (_, amounts) -> amounts.sumByCurrency() }

/** Kosten dieser Tour: ihre manuellen [Tour.costs] plus die Kosten aller [stations] der Tour, je Währung. */
fun Tour.totalCosts(stations: List<Station>): List<Money> = (costs + stations.stationCostTotals()).sumByCurrency()
