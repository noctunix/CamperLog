package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Datenbankzeile eines Trackpunkts. [recordedAt] ist Epoch-Millisekunden; je Tour gibt es höchstens
 * einen Punkt pro Zeitpunkt, das macht Sicherungs-Import und doppelt gelieferte Fixes idempotent.
 */
@Entity(
    tableName = "track_points",
    indices = [Index(value = ["tour_id", "recorded_at"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tour_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tour_id") val tourId: Long,
    val segment: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Int?,
    @ColumnInfo(name = "altitude_m") val altitudeM: Int?,
)
