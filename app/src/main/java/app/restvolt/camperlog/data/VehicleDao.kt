package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Room-Zugriff auf die Tabellen `vehicles`, `vehicle_axles` und `repairs` sowie das Feld
 * `current_vehicle_id` in `settings`.
 */
@Dao
interface VehicleDao {

    @Transaction
    @Query("SELECT * FROM vehicles ORDER BY (sale_date IS NOT NULL) ASC, name ASC, id ASC")
    fun observeAll(): Flow<List<VehicleWithAxles>>

    @Transaction
    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    fun observeAllByIdAsc(): Flow<List<VehicleWithAxles>>

    @Transaction
    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleWithAxles?>

    @Transaction
    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun getAll(): List<VehicleWithAxles>

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Insert
    suspend fun insertAxles(axles: List<VehicleAxleEntity>)

    @Query("DELETE FROM vehicle_axles WHERE vehicle_id = :vehicleId")
    suspend fun deleteAxles(vehicleId: Long)

    /** Legt [vehicle] samt [axles] an; die Achsen erhalten die neue Fahrzeug-id. */
    @Transaction
    suspend fun insertWithAxles(vehicle: VehicleEntity, axles: List<VehicleAxleEntity>): Long {
        val id = insert(vehicle)
        insertAxles(axles.map { it.copy(vehicleId = id) })
        return id
    }

    /** Aktualisiert [vehicle] und ersetzt seine Achsen vollständig durch [axles]. */
    @Transaction
    suspend fun updateWithAxles(vehicle: VehicleEntity, axles: List<VehicleAxleEntity>) {
        update(vehicle)
        deleteAxles(vehicle.id)
        insertAxles(axles)
    }

    @Query(
        "INSERT INTO vehicle_axles (vehicle_id, position, tire_size, dual_tires, second_tire_size) " +
            "SELECT :vehicleId, :position, '', 0, '' " +
            "WHERE NOT EXISTS (SELECT 1 FROM vehicle_axles WHERE vehicle_id = :vehicleId AND position = :position)",
    )
    suspend fun insertDefaultAxleIfNone(vehicleId: Long, position: Int)

    /** Legt für [vehicleId] zwei leere Achsen an, falls es noch keine hat. */
    @Transaction
    suspend fun insertDefaultAxlesIfNone(vehicleId: Long) {
        insertDefaultAxleIfNone(vehicleId, 0)
        insertDefaultAxleIfNone(vehicleId, 1)
    }

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Fahrzeuge; Reparaturen und Bordbuch-Einträge müssen vorher gelöscht sein. */
    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    /** id und Änderungszeit aller Fahrzeuge nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM vehicles")
    suspend fun getVersions(): List<VehicleVersionRow>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun countVehicles(): Int

    @Query("SELECT COUNT(*) FROM tours WHERE vehicle_id = :vehicleId")
    suspend fun countToursForVehicle(vehicleId: Long): Int

    @Query("SELECT COUNT(*) FROM stations WHERE vehicle_id = :vehicleId")
    suspend fun countStationsForVehicle(vehicleId: Long): Int

    @Query("SELECT COUNT(*) FROM repairs WHERE vehicle_id = :vehicleId")
    suspend fun countRepairsForVehicle(vehicleId: Long): Int

    /** ids aller Reparaturen eines Fahrzeugs, um vor dem kaskadierenden Löschen des Fahrzeugs ihre Anhänge zu entfernen. */
    @Query("SELECT id FROM repairs WHERE vehicle_id = :vehicleId")
    suspend fun repairIdsForVehicle(vehicleId: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM vehicles WHERE id = :id)")
    suspend fun vehicleExists(id: Long): Boolean

    @Query("SELECT id FROM vehicles ORDER BY id ASC LIMIT 1")
    suspend fun lowestVehicleId(): Long?

    /** ids aller als Demo markierten Fahrzeuge, zum Aufräumen eines abgebrochenen Tutorials. */
    @Query("SELECT id FROM vehicles WHERE is_demo = 1")
    suspend fun demoVehicleIds(): List<Long>

    /**
     * Legt ein Fahrzeug mit leerem Namen an, falls es noch keines gibt. Eine einzige Anweisung,
     * damit gleichzeitige Aufrufe (mehrere Screens beim ersten Start) nicht mehrere Fahrzeuge anlegen.
     */
    @Query(
        "INSERT INTO vehicles (uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
            "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, breakdown_phone, " +
            "travel_protection_provider, travel_protection_contract_number, travel_protection_phone, " +
            "insurer_claims_phone, required_energy_types, created_at, updated_at) " +
            "SELECT :uuid, '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', :nowMillis, :nowMillis " +
            "WHERE NOT EXISTS (SELECT 1 FROM vehicles)",
    )
    suspend fun insertDefaultIfNone(uuid: String, nowMillis: Long)

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

    /** Alle Reparaturen aller Fahrzeuge für den Sicherungs-Export. */
    @Query("SELECT * FROM repairs ORDER BY vehicle_id ASC, date DESC, id DESC")
    suspend fun getAllRepairs(): List<RepairEntity>

    /** Alle Reparaturen aller Fahrzeuge, live aktualisiert, für die Volltextsuche. */
    @Query("SELECT * FROM repairs ORDER BY date DESC, id DESC")
    fun observeAllRepairs(): Flow<List<RepairEntity>>

    @Insert
    suspend fun insertRepair(repair: RepairEntity): Long

    @Update
    suspend fun updateRepair(repair: RepairEntity)

    @Query("DELETE FROM repairs WHERE id = :id")
    suspend fun deleteRepairById(id: Long)

    /** Löscht alle Reparaturen; die Fahrzeuge bleiben erhalten. */
    @Query("DELETE FROM repairs")
    suspend fun deleteAllRepairs()

    /** id und Änderungszeit aller Reparaturen nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM repairs")
    suspend fun getRepairVersions(): List<RepairVersionRow>

    @Query("SELECT cost_currency FROM repairs WHERE cost_currency IS NOT NULL ORDER BY updated_at DESC, id DESC LIMIT 1")
    suspend fun lastUsedRepairCurrency(): String?
}

/** Stand eines gespeicherten Fahrzeugs für den Import-Abgleich. */
data class VehicleVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Stand einer gespeicherten Reparatur für den Import-Abgleich. */
data class RepairVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/**
 * Löst die aktuelle Fahrzeug-id auf: das in den Einstellungen gewählte Fahrzeug, sonst das mit der
 * kleinsten id. Gibt es noch kein Fahrzeug, wird eines mit leerem Namen angelegt.
 */
internal suspend fun VehicleDao.resolveCurrentVehicleId(now: Instant, newUuid: () -> String): Long {
    val currentId = getCurrentVehicleId()
    if (currentId != null && vehicleExists(currentId)) return currentId
    lowestVehicleId()?.let { return it }
    insertDefaultIfNone(newUuid(), now.toEpochMilli())
    val id = checkNotNull(lowestVehicleId())
    insertDefaultAxlesIfNone(id)
    return id
}
