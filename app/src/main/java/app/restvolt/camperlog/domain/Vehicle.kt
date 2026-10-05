package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Ein Wohnmobil. Nur [id], [uuid] und [name] sind nicht optional; ein leerer [name] wird in der
 * Oberfläche als „Mein Wohnmobil" angezeigt. [isSold] ergibt sich aus [saleDate].
 */
data class Vehicle(
    val id: Long = 0,
    val uuid: String = "",
    val name: String = "",
    val licensePlate: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val vin: String = "",
    val firstRegistration: LocalDate? = null,
    val notes: String = "",
    val purchaseDate: LocalDate? = null,
    val purchasePrice: Money? = null,
    val purchaseOdometerKm: Int? = null,
    val saleDate: LocalDate? = null,
    val salePrice: Money? = null,
    val insurer: String = "",
    val insurancePolicyNumber: String = "",
    val insurancePremiumPerYear: Money? = null,
    val vehicleTaxPerYear: Money? = null,
    val lengthCm: Int? = null,
    val widthCm: Int? = null,
    val heightCm: Int? = null,
    val grossWeightKg: Int? = null,
    val powerKw: Int? = null,
    val tireSize: String = "",
    val tirePressureFrontMbar: Int? = null,
    val tirePressureRearMbar: Int? = null,
    val fuelTankDl: Int? = null,
    val adBlueTankDl: Int? = null,
    val freshWaterTankDl: Int? = null,
    val greyWaterTankDl: Int? = null,
    val boilerDl: Int? = null,
    val cassetteDl: Int? = null,
    val batteryCapacityAh: Int? = null,
    val solarPowerWp: Int? = null,
    val nextInspectionDate: LocalDate? = null,
    val nextGasCheckDate: LocalDate? = null,
    val lastOilChangeDate: LocalDate? = null,
    val lastOilChangeOdometerKm: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Ob das Fahrzeug verkauft ist. */
    val isSold: Boolean get() = saleDate != null
}

/** Eine Reparatur an einem Fahrzeug. */
data class Repair(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val date: LocalDate,
    val description: String,
    val odometerKm: Int? = null,
    val cost: Money? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Ergebnis von [VehicleRepository.delete]. */
enum class VehicleDeleteResult {
    DELETED,

    /** Abgelehnt: das Fahrzeug hat noch Touren. */
    HAS_TOURS,

    /** Abgelehnt: es ist das letzte verbliebene Fahrzeug. */
    LAST_VEHICLE,
}

/** Zugriff auf alle gespeicherten Fahrzeuge und ihre Reparaturen. */
interface VehicleRepository {

    /** Liefert alle Fahrzeuge, nicht verkaufte zuerst, dann nach Name. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** Liefert das Fahrzeug mit [id] oder `null`, falls es nicht (mehr) existiert. */
    fun observeVehicle(id: Long): Flow<Vehicle?>

    /**
     * Liefert das aktuelle Fahrzeug: das in den Einstellungen gewählte, sonst das mit der
     * kleinsten id. Gibt es noch kein Fahrzeug, wird eines angelegt.
     */
    fun observeCurrentVehicle(): Flow<Vehicle>

    /** Macht das Fahrzeug mit [id] zum aktuellen. */
    suspend fun setCurrentVehicle(id: Long)

    /**
     * Legt [vehicle] an, wenn seine id 0 ist, sonst wird es aktualisiert.
     * Zeitstempel werden dabei vom Repository gesetzt.
     *
     * @return die id des gespeicherten Fahrzeugs
     */
    suspend fun save(vehicle: Vehicle): Long

    /** Löscht das Fahrzeug mit [id], sofern es nicht das letzte ist oder noch Touren hat. */
    suspend fun delete(id: Long): VehicleDeleteResult

    /** Liefert die Reparaturen eines Fahrzeugs, neueste zuerst. */
    fun observeRepairs(vehicleId: Long): Flow<List<Repair>>

    /** Legt [repair] an, wenn ihre id 0 ist, sonst wird sie aktualisiert. */
    suspend fun saveRepair(repair: Repair): Long

    /** Löscht die Reparatur mit [id]. */
    suspend fun deleteRepair(id: Long)

    /** Legt eine zuvor gelöschte [repair] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restoreRepair(repair: Repair)
}
