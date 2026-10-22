package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Lokale Arbeitskopie eines Formulars; wird nicht in Sicherungen übernommen. */
@Entity(tableName = "form_drafts", primaryKeys = ["kind", "record_id"])
data class FormDraftEntity(
    val kind: String,
    @ColumnInfo(name = "record_id") val recordId: Long,
    val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
