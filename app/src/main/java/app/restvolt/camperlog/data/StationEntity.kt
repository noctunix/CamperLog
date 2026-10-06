package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Datenbankzeile einer Station. Datum und Uhrzeit sind ISO-Texte; typspezifische Felder und
 * Koordinaten sind `NULL`, wenn sie nicht gelten bzw. nicht erfasst wurden. [services] speichert
 * die genutzten [app.restvolt.camperlog.domain.StationService]-Werte kommagetrennt als Namen.
 */
@Entity(
    tableName = "stations",
    indices = [Index(value = ["uuid"], unique = true), Index("vehicle_id"), Index("tour_id"), Index("date")],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tour_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "tour_id") val tourId: Long?,
    val type: String,
    val date: String,
    val time: String?,
    val name: String,
    val place: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "coordinate_source") val coordinateSource: String?,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Int?,
    @ColumnInfo(name = "map_link") val mapLink: String?,
    val notes: String,
    val nights: Int?,
    @ColumnInfo(name = "site_kind") val siteKind: String?,
    @ColumnInfo(name = "pitch_assigned") val pitchAssigned: Boolean?,
    @ColumnInfo(name = "electricity_flat_rate") val electricityFlatRate: String?,
    @ColumnInfo(name = "lte_quality") val lteQuality: String?,
    @ColumnInfo(name = "pitch_slope") val pitchSlope: String?,
    @ColumnInfo(name = "leveling_blocks_used") val levelingBlocksUsed: Boolean?,
    val services: String,
    @ColumnInfo(name = "weather_temperature_deci_c") val weatherTemperatureDeciC: Int?,
    @ColumnInfo(name = "weather_code") val weatherCode: Int?,
    @ColumnInfo(name = "weather_wind_kmh") val weatherWindKmh: Int?,
    @ColumnInfo(name = "weather_gust_kmh") val weatherGustKmh: Int?,
    @ColumnInfo(name = "weather_wind_direction_deg") val weatherWindDirectionDeg: Int?,
    @ColumnInfo(name = "weather_observed_at") val weatherObservedAtMillis: Long?,
    val favorite: Boolean,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Stand einer gespeicherten Station für den Import-Abgleich. */
data class StationVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
