package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Art eines Beladungsgegenstands. [FIXTURE] steht für fest verbautes Zubehör (z. B. Markise,
 * Fahrradträger, Anhängerkupplung); dafür ist [inWeighing] in den Formular-Vorgaben standardmäßig an.
 */
enum class LoadCategory {
    FIXTURE,
    EQUIPMENT,
    SUPPLIES,
    PERSON,
    OTHER,
}

/**
 * Ein Gegenstand des Beladungsplans. [massGrams] ist die Masse eines Stücks, [quantity] die Anzahl
 * Stücke. [positionCm] ist der Abstand von der Vorderachse in cm (negativ = vor der Vorderachse,
 * größer als der Radstand = hinter der Hinterachse); `null`, solange die Position nicht bekannt ist.
 * [inWeighing] sagt, ob der Gegenstand bei der dokumentierten Achslastwiegung bereits im Fahrzeug war.
 */
data class LoadItem(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val name: String,
    val category: LoadCategory,
    val massGrams: Long,
    val quantity: Int = 1,
    val positionCm: Int? = null,
    val inWeighing: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Gesamtmasse eines Gegenstands (alle Stücke zusammen) in kg. */
val LoadItem.totalMassKg: Double get() = massGrams.toDouble() * quantity / 1000.0

/**
 * Welcher Tank gemeint ist; jede Art entspricht genau einem Kapazitätsfeld in [Vehicle]. Ein Tank
 * fließt nur in die Berechnung ein, wenn das Fahrzeug die zugehörige Kapazität erfasst hat.
 */
enum class LoadTankKind {
    FUEL,
    ADBLUE,
    FRESH_WATER,
    BOILER,
    GREY_WATER,
    CASSETTE,
}

/** Kapazität von [this] in dl laut [vehicle], oder `null`, wenn das Fahrzeug keine Kapazität erfasst hat. */
fun LoadTankKind.capacityDlOf(vehicle: Vehicle): Int? = when (this) {
    LoadTankKind.FUEL -> vehicle.fuelTankDl
    LoadTankKind.ADBLUE -> vehicle.adBlueTankDl
    LoadTankKind.FRESH_WATER -> vehicle.freshWaterTankDl
    LoadTankKind.BOILER -> vehicle.boilerDl
    LoadTankKind.GREY_WATER -> vehicle.greyWaterTankDl
    LoadTankKind.CASSETTE -> vehicle.cassetteDl
}

/** Dichte von Diesel in g/l, Vorgabewert für [LoadTankKind.FUEL], wenn das Fahrzeug nicht ausschließlich Benzin benötigt. */
const val DEFAULT_DIESEL_DENSITY_GRAMS_PER_LITRE = 835

/** Dichte von Benzin in g/l, Vorgabewert für [LoadTankKind.FUEL] bei einem reinen Benziner. */
const val DEFAULT_PETROL_DENSITY_GRAMS_PER_LITRE = 745

/** Dichte von AdBlue in g/l, Vorgabewert für [LoadTankKind.ADBLUE]. */
const val DEFAULT_AD_BLUE_DENSITY_GRAMS_PER_LITRE = 1090

/** Dichte von Wasser in g/l, Vorgabewert für die wasserbasierten Tankarten. */
const val DEFAULT_WATER_DENSITY_GRAMS_PER_LITRE = 1000

/** Vorgabe-Dichte von [this] in g/l, wenn noch keine [LoadTankSettings]-Zeile angelegt wurde. */
fun LoadTankKind.defaultDensityGramsPerLitre(vehicle: Vehicle): Int = when (this) {
    LoadTankKind.FRESH_WATER, LoadTankKind.BOILER, LoadTankKind.GREY_WATER, LoadTankKind.CASSETTE ->
        DEFAULT_WATER_DENSITY_GRAMS_PER_LITRE
    LoadTankKind.ADBLUE -> DEFAULT_AD_BLUE_DENSITY_GRAMS_PER_LITRE
    LoadTankKind.FUEL ->
        if (vehicle.requiredEnergyTypes.contains(EnergyType.PETROL) && !vehicle.requiredEnergyTypes.contains(EnergyType.DIESEL)) {
            DEFAULT_PETROL_DENSITY_GRAMS_PER_LITRE
        } else {
            DEFAULT_DIESEL_DENSITY_GRAMS_PER_LITRE
        }
}

/**
 * Lagerposition und Dichte eines Tanks sowie sein Füllstand bei der dokumentierten Achslastwiegung.
 * Fehlt eine Zeile zu einer vorhandenen Tankkapazität, gilt [defaultTankSettings].
 */
data class LoadTankSettings(
    val vehicleId: Long,
    val kind: LoadTankKind,
    val positionCm: Int? = null,
    val densityGramsPerLitre: Int,
    val weighingLevelDl: Int = 0,
)

/** Vorgabe-Einstellungen eines Tanks, solange keine eigene [LoadTankSettings]-Zeile existiert. */
fun defaultTankSettings(vehicleId: Long, kind: LoadTankKind, vehicle: Vehicle): LoadTankSettings =
    LoadTankSettings(
        vehicleId = vehicleId,
        kind = kind,
        positionCm = null,
        densityGramsPerLitre = kind.defaultDensityGramsPerLitre(vehicle),
        weighingLevelDl = 0,
    )

/** Ein Beladungsprofil (z. B. "Kurztrip", "Langreise"); speichert nur Abweichungen von der Wiegung. */
data class LoadProfile(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val name: String,
    val sortOrder: Int = 0,
)

/**
 * Ob ein Gegenstand in einem Profil aktiv ist. Fehlt diese Zeile zu einem Profil und Gegenstand,
 * gilt der Zustand bei der Wiegung ([LoadItem.inWeighing]).
 */
data class LoadProfileItemState(val profileId: Long, val itemId: Long, val active: Boolean)

/**
 * Füllstand eines Tanks in einem Profil. Fehlt diese Zeile zu einem Profil und einer Tankart, gilt
 * der Füllstand bei der Wiegung ([LoadTankSettings.weighingLevelDl]).
 */
data class LoadProfileTankLevel(val profileId: Long, val kind: LoadTankKind, val levelDl: Int)

/** Vollständiger Beladungsplan eines Fahrzeugs. */
data class LoadPlan(
    val vehicleId: Long,
    val items: List<LoadItem> = emptyList(),
    val tankSettings: List<LoadTankSettings> = emptyList(),
    val profiles: List<LoadProfile> = emptyList(),
    val itemStates: List<LoadProfileItemState> = emptyList(),
    val tankLevels: List<LoadProfileTankLevel> = emptyList(),
)

/** Einstellungen der Tankart [kind], oder [defaultTankSettings], solange keine eigene Zeile existiert. */
fun LoadPlan.tankSettingsOrDefault(vehicle: Vehicle, kind: LoadTankKind): LoadTankSettings =
    tankSettings.firstOrNull { it.kind == kind } ?: defaultTankSettings(vehicle.id, kind, vehicle)

/** Füllstand der Tankart [kind] im Profil [profileId] in dl; ohne eigene Zeile gilt der Füllstand bei der Wiegung. */
fun LoadPlan.tankLevelDl(vehicle: Vehicle, profileId: Long, kind: LoadTankKind): Int =
    tankLevels.firstOrNull { it.profileId == profileId && it.kind == kind }?.levelDl
        ?: tankSettingsOrDefault(vehicle, kind).weighingLevelDl

/** Zugriff auf den Beladungsplan eines Fahrzeugs. */
interface LoadPlanRepository {

    /** Liefert den Beladungsplan des Fahrzeugs mit [vehicleId], live aktualisiert. */
    fun observePlan(vehicleId: Long): Flow<LoadPlan>

    /** Legt [item] an, wenn seine id 0 ist, sonst wird er aktualisiert. */
    suspend fun saveItem(item: LoadItem): Long

    /** Löscht den Gegenstand mit [id] samt seinen Profil-Zuständen. */
    suspend fun deleteItem(id: Long)

    /** Legt einen zuvor gelöschten [item] mit seiner bisherigen id wieder an. */
    suspend fun restoreItem(item: LoadItem)

    /** Legt [profile] an, wenn seine id 0 ist, sonst wird es umbenannt. */
    suspend fun saveProfile(profile: LoadProfile): Long

    /** Legt eine Kopie des Profils mit [profileId] unter [newName] an, inklusive seiner Zustände und Füllstände. */
    suspend fun duplicateProfile(profileId: Long, newName: String): Long

    /** Löscht das Profil mit [id] samt seinen Zuständen und Füllständen. */
    suspend fun deleteProfile(id: Long)

    /** Setzt, ob der Gegenstand [itemId] im Profil [profileId] aktiv ist. */
    suspend fun setItemActive(profileId: Long, itemId: Long, active: Boolean)

    /** Setzt den Füllstand der Tankart [kind] im Profil [profileId] in dl. */
    suspend fun setTankLevel(profileId: Long, kind: LoadTankKind, levelDl: Int)

    /** Legt [settings] an, wenn dafür noch keine Zeile existiert, sonst wird sie ersetzt. */
    suspend fun saveTankSettings(settings: LoadTankSettings)

    /**
     * Liefert die id des ersten Profils des Fahrzeugs [vehicleId]; legt eines mit [defaultName] an,
     * falls noch keines existiert.
     *
     * @return die id eines vorhandenen oder neu angelegten Profils
     */
    suspend fun ensureProfile(vehicleId: Long, defaultName: String): Long
}
