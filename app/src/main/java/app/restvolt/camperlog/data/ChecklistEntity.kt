package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Datenbankzeile einer gestarteten Checkliste. */
@Entity(
    tableName = "checklists",
    indices = [Index(value = ["uuid"], unique = true), Index("vehicle_id"), Index("tour_id")],
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicle_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TourEntity::class, parentColumns = ["id"], childColumns = ["tour_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "tour_id") val tourId: Long?,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Ein Punkt einer Checkliste in fester Reihenfolge ([position]) mit seinem Haken; verschwindet mit ihrer Checkliste. */
@Entity(
    tableName = "checklist_items",
    primaryKeys = ["checklist_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChecklistItemEntity(
    @ColumnInfo(name = "checklist_id") val checklistId: Long,
    val position: Int,
    val text: String,
    val checked: Boolean,
)

/** Checkliste mit ihren Punkten. */
data class ChecklistWithItems(
    @Embedded val checklist: ChecklistEntity,
    @Relation(parentColumn = "id", entityColumn = "checklist_id") val items: List<ChecklistItemEntity>,
)
