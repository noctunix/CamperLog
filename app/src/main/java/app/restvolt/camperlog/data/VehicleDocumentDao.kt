package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabelle `vehicle_documents`. */
@Dao
interface VehicleDocumentDao {

    @Query("SELECT * FROM vehicle_documents WHERE vehicle_id = :vehicleId ORDER BY (expiry_date IS NULL) ASC, expiry_date ASC, id ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<VehicleDocumentEntity>>

    @Query("SELECT * FROM vehicle_documents ORDER BY vehicle_id ASC, id ASC")
    suspend fun getAll(): List<VehicleDocumentEntity>

    @Query("SELECT id FROM vehicle_documents WHERE vehicle_id = :vehicleId")
    suspend fun idsForVehicle(vehicleId: Long): List<Long>

    @Insert
    suspend fun insert(document: VehicleDocumentEntity): Long

    @Update
    suspend fun update(document: VehicleDocumentEntity)

    @Query("DELETE FROM vehicle_documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Dokumente, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM vehicle_documents")
    suspend fun deleteAll()

    /** id und Änderungszeit aller Dokumente nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM vehicle_documents")
    suspend fun getVersions(): List<VehicleDocumentVersionRow>
}

/** Stand eines gespeicherten Dokuments für den Import-Abgleich. */
data class VehicleDocumentVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
