package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Datenbankzeile einer Station. Datum und Uhrzeit sind ISO-Texte; typspezifische Felder und
 * Koordinaten sind `NULL`, wenn sie nicht gelten bzw. nicht erfasst wurden. [services] speichert
 * die genutzten [app.restvolt.camperlog.domain.StationService]-Werte kommagetrennt als Namen.
 * Die Stromabrechnung teilt sich [electricityCurrency]; Beträge liegen als kleinste Einheit vor,
 * kWh/Zählerstände und der Preis je kWh als Dezimaltext (mehr Nachkommastellen als die Währung).
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
    @ColumnInfo(name = "lte_quality") val lteQuality: String?,
    @ColumnInfo(name = "pitch_slope") val pitchSlope: String?,
    @ColumnInfo(name = "leveling_blocks_used") val levelingBlocksUsed: Boolean?,
    @ColumnInfo(name = "electricity_billing") val electricityBilling: String? = null,
    @ColumnInfo(name = "electricity_currency") val electricityCurrency: String? = null,
    @ColumnInfo(name = "electricity_flat_amount_minor") val electricityFlatAmountMinor: Long? = null,
    @ColumnInfo(name = "electricity_base_fee_minor") val electricityBaseFeeMinor: Long? = null,
    @ColumnInfo(name = "electricity_price_per_kwh") val electricityPricePerKwh: String? = null,
    @ColumnInfo(name = "electricity_coin_price_minor") val electricityCoinPriceMinor: Long? = null,
    @ColumnInfo(name = "electricity_coins_used") val electricityCoinsUsed: Int? = null,
    @ColumnInfo(name = "electricity_kwh_per_coin") val electricityKwhPerCoin: String? = null,
    @ColumnInfo(name = "electricity_meter_start") val electricityMeterStart: String? = null,
    @ColumnInfo(name = "electricity_meter_end") val electricityMeterEnd: String? = null,
    @ColumnInfo(name = "electricity_kwh_used") val electricityKwhUsed: String? = null,
    @ColumnInfo(name = "toll_kind") val tollKind: String? = null,
    @ColumnInfo(name = "toll_payment_method") val tollPaymentMethod: String = "",
    @ColumnInfo(name = "toll_country") val tollCountry: String? = null,
    @ColumnInfo(name = "toll_valid_from") val tollValidFrom: String? = null,
    @ColumnInfo(name = "toll_valid_until") val tollValidUntil: String? = null,
    @ColumnInfo(name = "ferry_booking_reference") val ferryBookingReference: String = "",
    val services: String,
    @ColumnInfo(name = "weather_temperature_deci_c") val weatherTemperatureDeciC: Int?,
    @ColumnInfo(name = "weather_code") val weatherCode: Int?,
    @ColumnInfo(name = "weather_wind_kmh") val weatherWindKmh: Int?,
    @ColumnInfo(name = "weather_gust_kmh") val weatherGustKmh: Int?,
    @ColumnInfo(name = "weather_wind_direction_deg") val weatherWindDirectionDeg: Int?,
    @ColumnInfo(name = "weather_observed_at") val weatherObservedAtMillis: Long?,
    val favorite: Boolean,
    val rating: Int? = null,
    @ColumnInfo(name = "odometer_km") val odometerKm: Int? = null,
    @ColumnInfo(name = "manual_temperature_deci_c") val manualTemperatureDeciC: Int? = null,
    val link: String? = null,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Stand einer gespeicherten Station für den Import-Abgleich. */
data class StationVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/**
 * Ein Kostenbetrag einer Station in einer Kategorie und Währung. Pro Station gibt es höchstens eine
 * Zeile je (Kategorie, Währung); beim Löschen der Station verschwinden ihre Kosten mit.
 */
@Entity(
    tableName = "station_costs",
    primaryKeys = ["station_id", "category", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["station_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StationCostEntity(
    @ColumnInfo(name = "station_id") val stationId: Long,
    val category: String,
    /** ISO-4217-Code, z. B. `EUR`. */
    val currency: String,
    /** Betrag in der kleinsten Einheit der Währung, z. B. Cent. */
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val note: String,
    /** Reihenfolge der Zeile im Formular, beginnend bei 0. */
    val position: Int,
)

/** Station mit ihren Kostenbeträgen; die Reihenfolge ergibt sich aus [StationCostEntity.position]. */
data class StationWithCosts(
    @Embedded val station: StationEntity,
    @Relation(parentColumn = "id", entityColumn = "station_id") val costs: List<StationCostEntity>,
)
