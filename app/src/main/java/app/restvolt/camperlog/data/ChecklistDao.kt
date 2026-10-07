package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabellen `checklists` und `checklist_items`. */
@Dao
interface ChecklistDao {

    @Transaction
    @Query("SELECT * FROM checklists WHERE tour_id = :tourId ORDER BY created_at DESC, id DESC")
    fun observeForTour(tourId: Long): Flow<List<ChecklistWithItems>>

    @Transaction
    @Query("SELECT * FROM checklists WHERE vehicle_id = :vehicleId AND tour_id IS NULL ORDER BY created_at DESC, id DESC")
    fun observeForVehicleWithoutTour(vehicleId: Long): Flow<List<ChecklistWithItems>>

    @Transaction
    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observeById(id: Long): Flow<ChecklistWithItems?>

    @Transaction
    @Query("SELECT * FROM checklists WHERE tour_id = :tourId")
    suspend fun getForTour(tourId: Long): List<ChecklistWithItems>

    @Transaction
    @Query("SELECT * FROM checklists")
    suspend fun getAll(): List<ChecklistWithItems>

    @Insert
    suspend fun insert(checklist: ChecklistEntity): Long

    @Update
    suspend fun update(checklist: ChecklistEntity)

    @Insert
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Query("DELETE FROM checklist_items WHERE checklist_id = :checklistId")
    suspend fun deleteItems(checklistId: Long)

    /** Legt [checklist] samt [items] an; die Punkte erhalten die neue id. */
    @Transaction
    suspend fun insertWithItems(checklist: ChecklistEntity, items: List<ChecklistItemEntity>): Long {
        val id = insert(checklist)
        insertItems(items.map { it.copy(checklistId = id) })
        return id
    }

    /** Aktualisiert [checklist] und ersetzt ihre Punkte vollständig durch [items]. */
    @Transaction
    suspend fun updateWithItems(checklist: ChecklistEntity, items: List<ChecklistItemEntity>) {
        update(checklist)
        deleteItems(checklist.id)
        insertItems(items)
    }

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Checklisten, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM checklists")
    suspend fun deleteAll()

    /** id und Änderungszeit aller Checklisten nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM checklists")
    suspend fun getVersions(): List<ChecklistVersionRow>
}

/** Stand einer gespeicherten Checkliste für den Import-Abgleich. */
data class ChecklistVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
