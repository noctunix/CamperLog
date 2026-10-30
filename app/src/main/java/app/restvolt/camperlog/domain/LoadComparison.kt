package app.restvolt.camperlog.domain

/** Ob ein Gegenstand in genau einem der beiden verglichenen Profile aktiv ist. */
data class LoadItemDifference(val item: LoadItem, val activeInA: Boolean, val activeInB: Boolean)

/** Unterschiedlicher Füllstand eines Tanks zwischen den beiden verglichenen Profilen. */
data class LoadTankDifference(val kind: LoadTankKind, val levelDlA: Int, val levelDlB: Int, val massDeltaKg: Double)

/** Vergleich zweier Hochrechnungen desselben Fahrzeugplans; siehe [compareProfiles]. */
data class LoadProfileComparison(
    val forecastA: LoadForecast,
    val forecastB: LoadForecast,
    val totalDeltaKg: Double?,
    val frontAxleDeltaKg: Double?,
    val rearAxleDeltaKg: Double?,
    val itemDifferences: List<LoadItemDifference>,
    val tankDifferences: List<LoadTankDifference>,
)

/**
 * Vergleicht die Hochrechnungen der Profile [profileA] und [profileB] desselben Plans.
 *
 * @param vehicle Fahrzeug, zu dem [plan] gehört
 * @param plan Beladungsplan mit beiden Profilen
 * @param profileA erstes Profil
 * @param profileB zweites Profil
 * @return beide Hochrechnungen, ihre Differenzen (`null`, wenn eine Seite unbekannt ist) sowie die
 *   Gegenstände und Tanks, deren Zustand zwischen den Profilen abweicht
 */
fun compareProfiles(vehicle: Vehicle, plan: LoadPlan, profileA: Long, profileB: Long): LoadProfileComparison {
    val forecastA = forecastLoad(vehicle, plan, profileA)
    val forecastB = forecastLoad(vehicle, plan, profileB)

    fun isActive(item: LoadItem, profileId: Long): Boolean =
        plan.itemStates.firstOrNull { it.profileId == profileId && it.itemId == item.id }?.active ?: item.inWeighing

    val itemDifferences = plan.items.mapNotNull { item ->
        val activeInA = isActive(item, profileA)
        val activeInB = isActive(item, profileB)
        if (activeInA == activeInB) null else LoadItemDifference(item, activeInA, activeInB)
    }

    val tankDifferences = LoadTankKind.entries.mapNotNull { kind ->
        kind.capacityDlOf(vehicle) ?: return@mapNotNull null
        val levelA = plan.tankLevelDl(vehicle, profileA, kind)
        val levelB = plan.tankLevelDl(vehicle, profileB, kind)
        if (levelA == levelB) return@mapNotNull null
        val settings = plan.tankSettingsOrDefault(vehicle, kind)
        val massDeltaKg = tankMassDeltaKg(settings, levelB) - tankMassDeltaKg(settings, levelA)
        LoadTankDifference(kind, levelA, levelB, massDeltaKg)
    }

    return LoadProfileComparison(
        forecastA = forecastA,
        forecastB = forecastB,
        totalDeltaKg = diffOrNull(forecastA.totalKg, forecastB.totalKg),
        frontAxleDeltaKg = diffOrNull(forecastA.frontAxleKg, forecastB.frontAxleKg),
        rearAxleDeltaKg = diffOrNull(forecastA.rearAxleKg, forecastB.rearAxleKg),
        itemDifferences = itemDifferences,
        tankDifferences = tankDifferences,
    )
}

private fun diffOrNull(a: Double?, b: Double?): Double? = if (a != null && b != null) b - a else null
