package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Datenbankzeile eines Fahrzeugs. Datumswerte sind ISO-Texte (`yyyy-MM-dd`); Geldbeträge liegen
 * als Paar aus Währungscode und Minor-Betrag vor und sind beide `NULL`, wenn kein Betrag erfasst ist.
 * [requiredEnergyTypes] speichert die [app.restvolt.camperlog.domain.EnergyType]-Werte kommagetrennt
 * als Namen; leer bedeutet "nicht konfiguriert".
 */
@Entity(tableName = "vehicles", indices = [Index(value = ["uuid"], unique = true)])
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    val name: String = "",
    @ColumnInfo(name = "license_plate") val licensePlate: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val vin: String = "",
    @ColumnInfo(name = "first_registration") val firstRegistration: String? = null,
    val notes: String = "",
    @ColumnInfo(name = "purchase_date") val purchaseDate: String? = null,
    @ColumnInfo(name = "purchase_price_currency") val purchasePriceCurrency: String? = null,
    @ColumnInfo(name = "purchase_price_minor") val purchasePriceMinor: Long? = null,
    @ColumnInfo(name = "purchase_odometer_km") val purchaseOdometerKm: Int? = null,
    @ColumnInfo(name = "sale_date") val saleDate: String? = null,
    @ColumnInfo(name = "sale_price_currency") val salePriceCurrency: String? = null,
    @ColumnInfo(name = "sale_price_minor") val salePriceMinor: Long? = null,
    @ColumnInfo(name = "sale_odometer_km") val saleOdometerKm: Int? = null,
    val insurer: String = "",
    @ColumnInfo(name = "insurance_policy_number") val insurancePolicyNumber: String = "",
    @ColumnInfo(name = "insurance_premium_per_year_currency") val insurancePremiumPerYearCurrency: String? = null,
    @ColumnInfo(name = "insurance_premium_per_year_minor") val insurancePremiumPerYearMinor: Long? = null,
    @ColumnInfo(name = "vehicle_tax_per_year_currency") val vehicleTaxPerYearCurrency: String? = null,
    @ColumnInfo(name = "vehicle_tax_per_year_minor") val vehicleTaxPerYearMinor: Long? = null,
    @ColumnInfo(name = "length_cm") val lengthCm: Int? = null,
    @ColumnInfo(name = "width_cm") val widthCm: Int? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Int? = null,
    @ColumnInfo(name = "gross_weight_kg") val grossWeightKg: Int? = null,
    @ColumnInfo(name = "measured_empty_weight_kg") val measuredEmptyWeightKg: Int? = null,
    @ColumnInfo(name = "breakdown_provider") val breakdownProvider: String = "",
    @ColumnInfo(name = "breakdown_membership_number") val breakdownMembershipNumber: String = "",
    @ColumnInfo(name = "breakdown_phone") val breakdownPhone: String = "",
    @ColumnInfo(name = "travel_protection_provider") val travelProtectionProvider: String = "",
    @ColumnInfo(name = "travel_protection_contract_number") val travelProtectionContractNumber: String = "",
    @ColumnInfo(name = "travel_protection_phone") val travelProtectionPhone: String = "",
    @ColumnInfo(name = "insurer_claims_phone") val insurerClaimsPhone: String = "",
    @ColumnInfo(name = "power_kw") val powerKw: Int? = null,
    @ColumnInfo(name = "displacement_cc") val displacementCc: Int? = null,
    val transmission: String? = null,
    @ColumnInfo(name = "tire_size") val tireSize: String = "",
    @ColumnInfo(name = "tire_pressure_front_mbar") val tirePressureFrontMbar: Int? = null,
    @ColumnInfo(name = "tire_pressure_rear_mbar") val tirePressureRearMbar: Int? = null,
    @ColumnInfo(name = "required_energy_types") val requiredEnergyTypes: String = "",
    @ColumnInfo(name = "fuel_tank_dl") val fuelTankDl: Int? = null,
    @ColumnInfo(name = "ad_blue_tank_dl") val adBlueTankDl: Int? = null,
    @ColumnInfo(name = "fresh_water_tank_dl") val freshWaterTankDl: Int? = null,
    @ColumnInfo(name = "grey_water_tank_dl") val greyWaterTankDl: Int? = null,
    @ColumnInfo(name = "boiler_dl") val boilerDl: Int? = null,
    @ColumnInfo(name = "cassette_dl") val cassetteDl: Int? = null,
    @ColumnInfo(name = "battery_capacity_ah") val batteryCapacityAh: Int? = null,
    @ColumnInfo(name = "solar_power_wp") val solarPowerWp: Int? = null,
    @ColumnInfo(name = "next_inspection_date") val nextInspectionDate: String? = null,
    @ColumnInfo(name = "next_gas_check_date") val nextGasCheckDate: String? = null,
    @ColumnInfo(name = "next_leak_test_date") val nextLeakTestDate: String? = null,
    @ColumnInfo(name = "last_oil_change_date") val lastOilChangeDate: String? = null,
    @ColumnInfo(name = "last_oil_change_odometer_km") val lastOilChangeOdometerKm: Int? = null,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "is_demo", defaultValue = "0") val isDemo: Boolean = false,
)

/** Datenbankzeile einer Reparatur, verknüpft mit ihrem Fahrzeug. */
@Entity(
    tableName = "repairs",
    indices = [Index("vehicle_id"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RepairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = "",
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val date: String,
    val description: String,
    @ColumnInfo(name = "odometer_km") val odometerKm: Int? = null,
    @ColumnInfo(name = "cost_currency") val costCurrency: String? = null,
    @ColumnInfo(name = "cost_minor") val costMinor: Long? = null,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
