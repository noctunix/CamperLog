package app.restvolt.camperlog.domain

import kotlin.math.abs

/**
 * Statisches Hebelgesetz für eine Masse [massKg] im Abstand [positionCm] von der Vorderachse bei
 * Radstand [wheelbaseCm]: `ΔHinterachse = masse·position/radstand`, `ΔVorderachse = masse − ΔHinterachse`.
 * Bei einer Position hinter der Hinterachse (größer als der Radstand) kann [AxleDelta.frontKg] negativ werden.
 *
 * @param massKg Massenänderung in kg, auch negativ
 * @param positionCm Abstand von der Vorderachse in cm; negativ liegt vor der Vorderachse
 * @param wheelbaseCm Radstand in cm, muss größer als 0 sein
 * @return Änderung der Vorder- und Hinterachslast, zusammen immer gleich [massKg]
 */
fun axleDelta(massKg: Double, positionCm: Int, wheelbaseCm: Int): AxleDelta {
    require(wheelbaseCm > 0) { "Radstand muss größer als 0 sein" }
    val rearKg = massKg * positionCm / wheelbaseCm
    return AxleDelta(frontKg = massKg - rearKg, rearKg = rearKg)
}

/** Änderung der Achslasten durch eine einzelne Masse, siehe [axleDelta]. */
data class AxleDelta(val frontKg: Double, val rearKg: Double)

/**
 * Massenänderung eines Tanks bei Füllstand [levelDl] gegenüber seinem Füllstand bei der Wiegung.
 *
 * @param settings Dichte und Füllstand bei der Wiegung
 * @param levelDl Füllstand in dl
 * @return Massenänderung in kg, negativ bei weniger Inhalt als bei der Wiegung
 */
fun tankMassDeltaKg(settings: LoadTankSettings, levelDl: Int): Double =
    (levelDl - settings.weighingLevelDl) * settings.densityGramsPerLitre / 10_000.0

/** Prüfergebnis einer Last gegen ihr zulässiges Höchstgewicht. */
data class LoadLimitCheck(
    val valueKg: Double,
    val limitKg: Int,
    val reserveKg: Double,
    val reservePercent: Double,
    val ok: Boolean,
)

private fun checkLimit(valueKg: Double, limitKg: Int?): LoadLimitCheck? {
    if (limitKg == null) return null
    val reserveKg = limitKg - valueKg
    return LoadLimitCheck(
        valueKg = valueKg,
        limitKg = limitKg,
        reserveKg = reserveKg,
        reservePercent = reserveKg / limitKg * 100.0,
        ok = reserveKg >= 0,
    )
}

/** Warnung zu einer [LoadForecast]; die Texte dazu liefert die UI aus den String-Ressourcen. */
sealed interface LoadWarning {
    /** Keine Achslastwiegung erfasst: nur die Änderungen gegenüber der Wiegung sind bekannt, keine absoluten Werte. */
    data object NoWeighing : LoadWarning

    /** Radstand nicht erfasst: Gegenstände mit bekannter Position können nicht auf die Achsen verteilt werden. */
    data object NoWheelbase : LoadWarning

    /** [count] Gegenstände mit einer Massenänderung ungleich 0 ohne bekannte Position, zusammen [massKg]. */
    data class ItemsWithoutPosition(val count: Int, val massKg: Double) : LoadWarning

    /** Ein Tank mit einer Massenänderung ungleich 0 ohne bekannte Position. */
    data class TankWithoutPosition(val kind: LoadTankKind, val massKg: Double) : LoadWarning

    /** Die berechnete Vorderachslast ist nicht positiv; die Eingaben sind in sich widersprüchlich. */
    data object FrontAxleNonPositive : LoadWarning
}

/**
 * Hochrechnung der Gesamt- und Achslasten eines Profils gegenüber der dokumentierten Wiegung.
 *
 * @param deltaKg Summe aller Massenänderungen gegenüber der Wiegung
 * @param totalKg erwartetes Gesamtgewicht (Wiegung plus [deltaKg]), oder `null` ohne erfasste Wiegung
 * @param frontAxleKg erwartete Vorderachslast, oder `null`, wenn sie nicht berechnet werden kann
 *   (keine Achslastwiegung, fehlender Radstand oder eine Massenänderung ohne bekannte Position)
 * @param rearAxleKg erwartete Hinterachslast, unter denselben Bedingungen wie [frontAxleKg]
 * @param centerOfGravityCm Schwerpunkt in cm von der Vorderachse, nur wenn [frontAxleKg] bekannt ist
 * @param totalCheck Prüfung von [totalKg] gegen [Vehicle.grossWeightKg], falls beide bekannt sind
 * @param frontAxleCheck Prüfung von [frontAxleKg] gegen [Vehicle.allowedFrontAxleKg], falls beide bekannt sind
 * @param rearAxleCheck Prüfung von [rearAxleKg] gegen [Vehicle.allowedRearAxleKg], falls beide bekannt sind
 * @param warnings Hinweise auf unvollständige oder widersprüchliche Daten
 */
data class LoadForecast(
    val deltaKg: Double,
    val totalKg: Double?,
    val frontAxleKg: Double?,
    val rearAxleKg: Double?,
    val centerOfGravityCm: Double?,
    val totalCheck: LoadLimitCheck?,
    val frontAxleCheck: LoadLimitCheck?,
    val rearAxleCheck: LoadLimitCheck?,
    val warnings: List<LoadWarning>,
)

private fun Boolean.toUnit(): Int = if (this) 1 else 0

private data class LeverLoad(val massKg: Double, val positionCm: Int)

/**
 * Rechnet ein Beladungsprofil gegenüber der dokumentierten Wiegung hoch. Achslasten bleiben `null`,
 * wenn eine Massenänderung ungleich 0 keine Position hat oder der Radstand fehlt (keine 50:50-Annahme).
 *
 * @param vehicle Fahrzeug mit Wiegung, Radstand, Tankkapazitäten und zulässigen Lasten
 * @param plan Beladungsplan des Fahrzeugs
 * @param profileId Profil, dessen Abweichungen gelten; ohne eigene Zeilen gilt der gewogene Zustand
 * @return die Hochrechnung samt Grenzprüfungen und Warnungen
 */
fun forecastLoad(vehicle: Vehicle, plan: LoadPlan, profileId: Long): LoadForecast {
    val warnings = mutableListOf<LoadWarning>()

    val baselineFrontKg = vehicle.measuredFrontAxleKg?.toDouble()
    val baselineRearKg = vehicle.measuredRearAxleKg?.toDouble()
    val hasAxleBaseline = baselineFrontKg != null && baselineRearKg != null
    val baselineTotalKg: Double? = when {
        hasAxleBaseline -> baselineFrontKg + baselineRearKg
        vehicle.measuredEmptyWeightKg != null -> vehicle.measuredEmptyWeightKg.toDouble()
        else -> null
    }
    if (baselineTotalKg == null) warnings += LoadWarning.NoWeighing

    val leverLoads = mutableListOf<LeverLoad>()
    var deltaTotalKg = 0.0
    var unknownItemCount = 0
    var unknownItemMassKg = 0.0
    var hasUnpositionedDelta = false

    for (item in plan.items) {
        val active = plan.itemStates.firstOrNull { it.profileId == profileId && it.itemId == item.id }?.active ?: item.inWeighing
        val deltaUnits = active.toUnit() - item.inWeighing.toUnit()
        if (deltaUnits == 0) continue
        val deltaMassKg = deltaUnits * item.totalMassKg
        deltaTotalKg += deltaMassKg
        val position = item.positionCm
        if (position == null) {
            unknownItemCount++
            unknownItemMassKg += abs(deltaMassKg)
            hasUnpositionedDelta = true
        } else {
            leverLoads += LeverLoad(deltaMassKg, position)
        }
    }
    if (unknownItemCount > 0) warnings += LoadWarning.ItemsWithoutPosition(unknownItemCount, unknownItemMassKg)

    for (kind in LoadTankKind.entries) {
        kind.capacityDlOf(vehicle) ?: continue
        val settings = plan.tankSettings.firstOrNull { it.kind == kind } ?: defaultTankSettings(vehicle.id, kind, vehicle)
        val profileLevelDl = plan.tankLevels.firstOrNull { it.profileId == profileId && it.kind == kind }?.levelDl
            ?: settings.weighingLevelDl
        val deltaLevelDl = profileLevelDl - settings.weighingLevelDl
        if (deltaLevelDl == 0) continue
        val deltaMassKg = tankMassDeltaKg(settings, profileLevelDl)
        deltaTotalKg += deltaMassKg
        val position = settings.positionCm
        if (position == null) {
            warnings += LoadWarning.TankWithoutPosition(kind, abs(deltaMassKg))
            hasUnpositionedDelta = true
        } else {
            leverLoads += LeverLoad(deltaMassKg, position)
        }
    }

    val totalKg = baselineTotalKg?.plus(deltaTotalKg)

    val wheelbase = vehicle.wheelbaseCm
    val missingWheelbase = leverLoads.isNotEmpty() && (wheelbase == null || wheelbase <= 0)
    if (missingWheelbase) warnings += LoadWarning.NoWheelbase

    var frontAxleKg: Double? = null
    var rearAxleKg: Double? = null
    if (hasAxleBaseline && !hasUnpositionedDelta && !missingWheelbase) {
        var deltaFrontKg = 0.0
        var deltaRearKg = 0.0
        for (load in leverLoads) {
            val delta = axleDelta(load.massKg, load.positionCm, checkNotNull(wheelbase))
            deltaFrontKg += delta.frontKg
            deltaRearKg += delta.rearKg
        }
        frontAxleKg = baselineFrontKg + deltaFrontKg
        rearAxleKg = baselineRearKg + deltaRearKg
        if (frontAxleKg <= 0) warnings += LoadWarning.FrontAxleNonPositive
    }

    val centerOfGravityCm = if (rearAxleKg != null && totalKg != null && wheelbase != null && totalKg > 0) {
        rearAxleKg * wheelbase / totalKg
    } else {
        null
    }

    return LoadForecast(
        deltaKg = deltaTotalKg,
        totalKg = totalKg,
        frontAxleKg = frontAxleKg,
        rearAxleKg = rearAxleKg,
        centerOfGravityCm = centerOfGravityCm,
        totalCheck = totalKg?.let { checkLimit(it, vehicle.grossWeightKg) },
        frontAxleCheck = frontAxleKg?.let { checkLimit(it, vehicle.allowedFrontAxleKg) },
        rearAxleCheck = rearAxleKg?.let { checkLimit(it, vehicle.allowedRearAxleKg) },
        warnings = warnings,
    )
}
