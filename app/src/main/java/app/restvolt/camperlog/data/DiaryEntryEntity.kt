package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Datenbankzeile eines Tagebucheintrags. */
@Entity(
    tableName = "diary_entries",
    indices = [Index("tour_id"), Index(value = ["tour_id", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tour_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "tour_id") val tourId: Long,
    val date: String,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
