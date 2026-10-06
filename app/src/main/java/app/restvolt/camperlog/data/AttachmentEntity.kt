package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Datenbankzeile eines Anhangs. Kein Fremdschlüssel auf [ownerId]: je nach [ownerType] zeigt sie auf
 * `stations`, `repairs`, `log_entries` oder `vehicle_documents` (siehe
 * [app.restvolt.camperlog.domain.AttachmentRepository]).
 */
@Entity(tableName = "attachments", indices = [Index(value = ["uuid"], unique = true), Index("owner_type", "owner_id")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "taken_at") val takenAt: String? = null,
    val caption: String = "",
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)
