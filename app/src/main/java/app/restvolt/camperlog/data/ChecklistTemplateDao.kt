package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabellen `checklist_templates` und `checklist_template_items`. */
@Dao
interface ChecklistTemplateDao {

    @Transaction
    @Query("SELECT * FROM checklist_templates ORDER BY name ASC, id ASC")
    fun observeAll(): Flow<List<ChecklistTemplateWithItems>>

    @Transaction
    @Query("SELECT * FROM checklist_templates ORDER BY name ASC, id ASC")
    suspend fun getAll(): List<ChecklistTemplateWithItems>

    @Insert
    suspend fun insert(template: ChecklistTemplateEntity): Long

    @Update
    suspend fun update(template: ChecklistTemplateEntity)

    @Insert
    suspend fun insertItems(items: List<ChecklistTemplateItemEntity>)

    @Query("DELETE FROM checklist_template_items WHERE template_id = :templateId")
    suspend fun deleteItems(templateId: Long)

    /** Legt [template] samt [items] an; die Punkte erhalten die neue id. */
    @Transaction
    suspend fun insertWithItems(template: ChecklistTemplateEntity, items: List<ChecklistTemplateItemEntity>): Long {
        val id = insert(template)
        insertItems(items.map { it.copy(templateId = id) })
        return id
    }

    /** Aktualisiert [template] und ersetzt ihre Punkte vollständig durch [items]. */
    @Transaction
    suspend fun updateWithItems(template: ChecklistTemplateEntity, items: List<ChecklistTemplateItemEntity>) {
        update(template)
        deleteItems(template.id)
        insertItems(items)
    }

    @Query("DELETE FROM checklist_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Vorlagen, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM checklist_templates")
    suspend fun deleteAll()

    /** id und Änderungszeit aller Vorlagen nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM checklist_templates")
    suspend fun getVersions(): List<ChecklistTemplateVersionRow>
}

/** Stand einer gespeicherten Vorlage für den Import-Abgleich. */
data class ChecklistTemplateVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
