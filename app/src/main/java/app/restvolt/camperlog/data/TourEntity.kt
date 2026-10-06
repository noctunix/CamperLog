package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Datenbankzeile einer Tour. Datumswerte sind ISO-Texte (`yyyy-MM-dd`), damit sie korrekt
 * sortieren und das Jahr per SQL ausgelesen werden kann. Enums werden über ihren Namen gespeichert.
 */
@Entity(
    tableName = "tours",
    indices = [Index("start_date"), Index(value = ["uuid"], unique = true), Index("vehicle_id")],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    /** Geräteübergreifend eindeutige Kennung der Tour (UUID als Text). */
    @ColumnInfo(defaultValue = "") val uuid: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    val destination: String,
    @ColumnInfo(name = "tour_type") val tourType: String,
    @ColumnInfo(name = "travel_days") val travelDays: Int,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Int,
    val notes: String,
    @ColumnInfo(name = "map_link") val mapLink: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/**
 * Kostenbetrag einer Tour in einer Währung. Pro Tour gibt es höchstens eine Zeile je Währung;
 * beim Löschen der Tour verschwinden ihre Kosten mit.
 */
@Entity(
    tableName = "tour_costs",
    primaryKeys = ["tour_id", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tour_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TourCostEntity(
    @ColumnInfo(name = "tour_id") val tourId: Long,
    /** ISO-4217-Code, z. B. `EUR`. */
    val currency: String,
    /** Betrag in der kleinsten Einheit der Währung, z. B. Cent. */
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    /** Reihenfolge der Zeile im Formular, beginnend bei 0. */
    val position: Int,
)

/** Tour mit ihren Kostenbeträgen; die Reihenfolge ergibt sich aus [TourCostEntity.position]. */
data class TourWithCosts(
    @Embedded val tour: TourEntity,
    @Relation(parentColumn = "id", entityColumn = "tour_id") val costs: List<TourCostEntity>,
)

/** Summe der Kosten in einer Währung. */
data class CostSumRow(
    val currency: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** Summe der Kosten in einer Währung für Touren, die im Jahr [year] beginnen. */
data class YearCostSumRow(
    val year: Int,
    val currency: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** Ergebniszeile einer Summenabfrage. */
data class TotalsRow(
    val tours: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Long,
    @ColumnInfo(name = "travel_days") val travelDays: Long,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Long,
)

/** Ergebniszeile einer Summenabfrage pro Jahr. */
data class YearTotalsRow(
    val year: Int,
    val tours: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Long,
    @ColumnInfo(name = "travel_days") val travelDays: Long,
    @ColumnInfo(name = "overnight_stays") val overnightStays: Long,
)
