package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Room-Zugriff auf den Beladungsplan: Gegenstände, Profile, ihre Zustände und die Tankeinstellungen. */
@Dao
interface LoadDao {

    @Query("SELECT * FROM load_items WHERE vehicle_id = :vehicleId ORDER BY sort_order ASC, id ASC")
    fun observeItems(vehicleId: Long): Flow<List<LoadItemEntity>>

    @Query("SELECT * FROM load_profiles WHERE vehicle_id = :vehicleId ORDER BY sort_order ASC, id ASC")
    fun observeProfiles(vehicleId: Long): Flow<List<LoadProfileEntity>>

    @Query(
        "SELECT s.* FROM load_profile_item_states s " +
            "INNER JOIN load_profiles p ON p.id = s.profile_id WHERE p.vehicle_id = :vehicleId",
    )
    fun observeItemStates(vehicleId: Long): Flow<List<LoadProfileItemStateEntity>>

    @Query(
        "SELECT l.* FROM load_profile_tank_levels l " +
            "INNER JOIN load_profiles p ON p.id = l.profile_id WHERE p.vehicle_id = :vehicleId",
    )
    fun observeTankLevels(vehicleId: Long): Flow<List<LoadProfileTankLevelEntity>>

    @Query("SELECT * FROM load_tank_settings WHERE vehicle_id = :vehicleId")
    fun observeTankSettings(vehicleId: Long): Flow<List<LoadTankSettingsEntity>>

    /** Beladungsplan des Fahrzeugs [vehicleId], live aktualisiert. */
    fun observePlan(vehicleId: Long): Flow<LoadPlanRow> = combine(
        observeItems(vehicleId),
        observeProfiles(vehicleId),
        observeItemStates(vehicleId),
        observeTankLevels(vehicleId),
        observeTankSettings(vehicleId),
    ) { items, profiles, itemStates, tankLevels, tankSettings ->
        LoadPlanRow(items, profiles, itemStates, tankLevels, tankSettings)
    }

    @Insert
    suspend fun insertItem(item: LoadItemEntity): Long

    @Update
    suspend fun updateItem(item: LoadItemEntity)

    @Query("DELETE FROM load_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Insert
    suspend fun insertProfile(profile: LoadProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: LoadProfileEntity)

    @Query("DELETE FROM load_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM load_profiles WHERE vehicle_id = :vehicleId ORDER BY sort_order ASC, id ASC LIMIT 1")
    suspend fun firstProfile(vehicleId: Long): LoadProfileEntity?

    @Query("SELECT * FROM load_profiles WHERE id = :id")
    suspend fun profileById(id: Long): LoadProfileEntity?

    @Query("SELECT * FROM load_profile_item_states WHERE profile_id = :profileId")
    suspend fun itemStatesOf(profileId: Long): List<LoadProfileItemStateEntity>

    @Query("SELECT * FROM load_profile_tank_levels WHERE profile_id = :profileId")
    suspend fun tankLevelsOf(profileId: Long): List<LoadProfileTankLevelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemStates(states: List<LoadProfileItemStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTankLevels(levels: List<LoadProfileTankLevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setItemActive(state: LoadProfileItemStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTankLevel(level: LoadProfileTankLevelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTankSettings(settings: LoadTankSettingsEntity)

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM load_profiles WHERE vehicle_id = :vehicleId")
    suspend fun nextProfileSortOrder(vehicleId: Long): Int

    /** Stand der gespeicherten Gegenstände eines Fahrzeugs für den Sicherungs-Abgleich über ihre UUID. */
    @Query("SELECT uuid, id, updated_at FROM load_items WHERE vehicle_id = :vehicleId")
    suspend fun getItemVersions(vehicleId: Long): List<LoadItemVersionRow>

    /** id und UUID der gespeicherten Profile eines Fahrzeugs für den Sicherungs-Abgleich. */
    @Query("SELECT uuid, id FROM load_profiles WHERE vehicle_id = :vehicleId")
    suspend fun getProfileIds(vehicleId: Long): List<LoadProfileIdRow>

    @Query("DELETE FROM load_profile_item_states WHERE profile_id = :profileId")
    suspend fun deleteItemStatesForProfile(profileId: Long)

    @Query("DELETE FROM load_profile_tank_levels WHERE profile_id = :profileId")
    suspend fun deleteTankLevelsForProfile(profileId: Long)
}

/** Stand eines gespeicherten Gegenstands für den Import-Abgleich. */
data class LoadItemVersionRow(val uuid: String, val id: Long, @ColumnInfo(name = "updated_at") val updatedAtMillis: Long)

/** id und UUID eines gespeicherten Profils für den Import-Abgleich. */
data class LoadProfileIdRow(val uuid: String, val id: Long)

/** Alle Zeilen des Beladungsplans eines Fahrzeugs, wie sie Room liefert. */
data class LoadPlanRow(
    val items: List<LoadItemEntity>,
    val profiles: List<LoadProfileEntity>,
    val itemStates: List<LoadProfileItemStateEntity>,
    val tankLevels: List<LoadProfileTankLevelEntity>,
    val tankSettings: List<LoadTankSettingsEntity>,
)
