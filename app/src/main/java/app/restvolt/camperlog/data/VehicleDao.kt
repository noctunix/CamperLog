package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Room-Zugriff auf die Tabellen `vehicles` und `repairs` sowie das Feld `current_vehicle_id` in `settings`. */
@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY (sale_date IS NOT NULL) ASC, name ASC, id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    fun observeAllByIdAsc(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleEntity?>

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun countVehicles(): Int

    @Query("SELECT COUNT(*) FROM tours WHERE vehicle_id = :vehicleId")
    suspend fun countToursForVehicle(vehicleId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM vehicles WHERE id = :id)")
    suspend fun vehicleExists(id: Long): Boolean

    @Query("SELECT id FROM vehicles ORDER BY id ASC LIMIT 1")
    suspend fun lowestVehicleId(): Long?

    @Query("SELECT current_vehicle_id FROM settings WHERE id = $SETTINGS_ID")
    fun observeCurrentVehicleId(): Flow<Long?>

    @Query("SELECT current_vehicle_id FROM settings WHERE id = $SETTINGS_ID")
    suspend fun getCurrentVehicleId(): Long?

    @Query("INSERT OR IGNORE INTO settings (id, main_currency) VALUES ($SETTINGS_ID, 'EUR')")
    suspend fun ensureSettingsRow()

    @Query("UPDATE settings SET current_vehicle_id = :vehicleId WHERE id = $SETTINGS_ID")
    suspend fun updateCurrentVehicleId(vehicleId: Long)

    /** Setzt das aktuelle Fahrzeug, ohne die Hauptwährung anzutasten. */
    @Transaction
    suspend fun setCurrentVehicleId(vehicleId: Long) {
        ensureSettingsRow()
        updateCurrentVehicleId(vehicleId)
    }

    @Query("SELECT * FROM repairs WHERE vehicle_id = :vehicleId ORDER BY date DESC, id DESC")
    fun observeRepairs(vehicleId: Long): Flow<List<RepairEntity>>

    @Insert
    suspend fun insertRepair(repair: RepairEntity): Long

    @Update
    suspend fun updateRepair(repair: RepairEntity)

    @Query("DELETE FROM repairs WHERE id = :id")
    suspend fun deleteRepairById(id: Long)
}

/**
 * Löst die aktuelle Fahrzeug-id auf: das in den Einstellungen gewählte Fahrzeug, sonst das mit der
 * kleinsten id. Gibt es noch kein Fahrzeug, wird eines mit leerem Namen angelegt.
 */
internal suspend fun VehicleDao.resolveCurrentVehicleId(now: Instant, newUuid: () -> String): Long {
    val currentId = getCurrentVehicleId()
    if (currentId != null && vehicleExists(currentId)) return currentId
    lowestVehicleId()?.let { return it }
    return insert(VehicleEntity(uuid = newUuid(), createdAtMillis = now.toEpochMilli(), updatedAtMillis = now.toEpochMilli()))
}
