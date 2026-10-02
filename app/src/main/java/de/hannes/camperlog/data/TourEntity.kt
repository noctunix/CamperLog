package de.hannes.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Datenbankzeile einer Tour. Datumswerte sind ISO-Texte (`yyyy-MM-dd`), damit sie korrekt
 * sortieren und das Jahr per SQL ausgelesen werden kann. Enums werden über ihren Namen gespeichert.
 */
@Entity(tableName = "tours", indices = [Index("start_date")])
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    val destination: String,
    @ColumnInfo(name = "tour_type") val tourType: String,
    @ColumnInfo(name = "travel_days") val travelDays: Int,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Int,
    @ColumnInfo(name = "cost_cents") val costCents: Long,
    @ColumnInfo(name = "pitch_assigned") val pitchAssigned: Boolean,
    @ColumnInfo(name = "electricity_flat_rate") val electricityFlatRate: String,
    @ColumnInfo(name = "lte_quality") val lteQuality: String,
    @ColumnInfo(name = "pitch_slope") val pitchSlope: String,
    @ColumnInfo(name = "leveling_blocks_used") val levelingBlocksUsed: Boolean,
    val notes: String,
    @ColumnInfo(name = "map_link") val mapLink: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Ergebniszeile einer Summenabfrage. */
data class TotalsRow(
    val tours: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Long,
    @ColumnInfo(name = "travel_days") val travelDays: Long,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Long,
    @ColumnInfo(name = "cost_cents") val costCents: Long,
)

/** Ergebniszeile einer Summenabfrage pro Jahr. */
data class YearTotalsRow(
    val year: Int,
    val tours: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Long,
    @ColumnInfo(name = "travel_days") val travelDays: Long,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Long,
    @ColumnInfo(name = "cost_cents") val costCents: Long,
)
