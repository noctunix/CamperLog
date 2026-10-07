package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Datenbankzeile einer Checklisten-Vorlage. */
@Entity(tableName = "checklist_templates", indices = [Index(value = ["uuid"], unique = true)])
data class ChecklistTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    val name: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Ein Punkt einer Vorlage in fester Reihenfolge ([position]); verschwindet mit ihrer Vorlage. */
@Entity(
    tableName = "checklist_template_items",
    primaryKeys = ["template_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ChecklistTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChecklistTemplateItemEntity(
    @ColumnInfo(name = "template_id") val templateId: Long,
    val position: Int,
    val text: String,
)

/** Vorlage mit ihren Punkten. */
data class ChecklistTemplateWithItems(
    @Embedded val template: ChecklistTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "template_id") val items: List<ChecklistTemplateItemEntity>,
)
