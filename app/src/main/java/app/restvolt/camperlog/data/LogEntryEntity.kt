package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Datenbankzeile eines Bordbuch-Eintrags; [stationId] folgt der Stationsverknüpfung (siehe [app.restvolt.camperlog.domain.LogEntry]). */
@Entity(
    tableName = "log_entries",
    indices = [Index(value = ["vehicle_id", "type", "date"]), Index(value = ["uuid"], unique = true), Index(value = ["station_id"])],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["station_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val type: String,
    val date: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "station_id") val stationId: Long? = null,
)
