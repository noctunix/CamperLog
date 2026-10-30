package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Datenbankzeile eines Beladungsgegenstands ([app.restvolt.camperlog.domain.LoadItem]). */
@Entity(
    tableName = "load_items",
    indices = [Index(value = ["uuid"], unique = true), Index("vehicle_id")],
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LoadItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val name: String = "",
    val category: String,
    @ColumnInfo(name = "mass_grams") val massGrams: Long,
    val quantity: Int = 1,
    @ColumnInfo(name = "position_cm") val positionCm: Int? = null,
    @ColumnInfo(name = "in_weighing") val inWeighing: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Datenbankzeile eines Beladungsprofils ([app.restvolt.camperlog.domain.LoadProfile]). */
@Entity(
    tableName = "load_profiles",
    indices = [Index(value = ["uuid"], unique = true), Index("vehicle_id")],
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LoadProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val name: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

/**
 * Ob ein Gegenstand in einem Profil aktiv ist ([app.restvolt.camperlog.domain.LoadProfileItemState]).
 * Verschwindet mit ihrem Profil oder ihrem Gegenstand.
 */
@Entity(
    tableName = "load_profile_item_states",
    primaryKeys = ["profile_id", "item_id"],
    indices = [Index("item_id")],
    foreignKeys = [
        ForeignKey(entity = LoadProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LoadItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LoadProfileItemStateEntity(
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val active: Boolean,
)

/**
 * Füllstand eines Tanks in einem Profil ([app.restvolt.camperlog.domain.LoadProfileTankLevel]).
 * Verschwindet mit ihrem Profil.
 */
@Entity(
    tableName = "load_profile_tank_levels",
    primaryKeys = ["profile_id", "kind"],
    foreignKeys = [
        ForeignKey(entity = LoadProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LoadProfileTankLevelEntity(
    @ColumnInfo(name = "profile_id") val profileId: Long,
    val kind: String,
    @ColumnInfo(name = "level_dl") val levelDl: Int,
)

/** Lagerposition, Dichte und Wiege-Füllstand eines Tanks ([app.restvolt.camperlog.domain.LoadTankSettings]). */
@Entity(
    tableName = "load_tank_settings",
    primaryKeys = ["vehicle_id", "kind"],
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LoadTankSettingsEntity(
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val kind: String,
    @ColumnInfo(name = "position_cm") val positionCm: Int? = null,
    @ColumnInfo(name = "density_grams_per_litre") val densityGramsPerLitre: Int,
    @ColumnInfo(name = "weighing_level_dl") val weighingLevelDl: Int = 0,
)
