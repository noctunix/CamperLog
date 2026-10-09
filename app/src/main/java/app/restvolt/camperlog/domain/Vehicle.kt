package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Energieart, die ein Fahrzeug benötigt. Eine leere [Vehicle.requiredEnergyTypes]-Menge bedeutet
 * "nicht konfiguriert" und zeigt im Formular wie bisher alle antriebsbezogenen Felder.
 */
enum class EnergyType {
    PETROL,
    DIESEL,
    ADBLUE,
    GAS,
    ELECTRICITY,
}

/** Ob [this] leer ist (nicht konfiguriert, zeigt alles) oder mindestens eine von [types] enthält. */
fun Set<EnergyType>.allowsAny(vararg types: EnergyType): Boolean = isEmpty() || types.any { it in this }

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
    val measuredEmptyWeightKg: Int? = null,
    val breakdownProvider: String = "",
    val breakdownMembershipNumber: String = "",
    val breakdownPhone: String = "",
    val travelProtectionProvider: String = "",
    val travelProtectionContractNumber: String = "",
    val travelProtectionPhone: String = "",
    val insurerClaimsPhone: String = "",
    val powerKw: Int? = null,
    val tireSize: String = "",
    val tirePressureFrontMbar: Int? = null,
    val tirePressureRearMbar: Int? = null,
    val requiredEnergyTypes: Set<EnergyType> = emptySet(),
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
    val nextLeakTestDate: LocalDate? = null,
    val lastOilChangeDate: LocalDate? = null,
    val lastOilChangeOdometerKm: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Ob das Fahrzeug verkauft ist. */
    val isSold: Boolean get() = saleDate != null

    /** Restzuladung = zulässiges Gesamtgewicht minus gewogenes Leergewicht; nur, wenn beide erfasst sind. */
    val remainingPayloadKg: Int?
        get() = if (grossWeightKg != null && measuredEmptyWeightKg != null) grossWeightKg - measuredEmptyWeightKg else null

    /** Ob die Karte „Panne & Unfall" angezeigt werden soll. */
    val hasBreakdownInfo: Boolean
        get() = listOf(
            breakdownProvider, breakdownMembershipNumber, breakdownPhone,
            travelProtectionProvider, travelProtectionContractNumber, travelProtectionPhone,
            insurerClaimsPhone,
        ).any(String::isNotBlank)
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

    /** Abgelehnt: das Fahrzeug hat noch Touren oder Stationen. */
    HAS_TOURS_OR_STATIONS,

    /** Abgelehnt: es ist das letzte verbliebene Fahrzeug. */
    LAST_VEHICLE,
}

/** Zugriff auf alle gespeicherten Fahrzeuge und ihre Reparaturen. */
interface VehicleRepository {

    /** Liefert alle Fahrzeuge, nicht verkaufte zuerst, dann nach Name. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** Liefert alle Fahrzeuge für den Sicherungs-Export, ohne festgelegte Reihenfolge. */
    suspend fun allVehicles(): List<Vehicle>

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

    /** Liefert alle Reparaturen aller Fahrzeuge, live aktualisiert, für die Volltextsuche. */
    fun observeAllRepairs(): Flow<List<Repair>>

    /** Liefert alle Reparaturen aller Fahrzeuge für den Sicherungs-Export. */
    suspend fun allRepairs(): List<Repair>

    /** Legt [repair] an, wenn ihre id 0 ist, sonst wird sie aktualisiert. */
    suspend fun saveRepair(repair: Repair): Long

    /** Löscht die Reparatur mit [id]. */
    suspend fun deleteRepair(id: Long)

    /** Legt eine zuvor gelöschte [repair] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restoreRepair(repair: Repair)

    /** Währung der zuletzt geänderten Reparatur mit erfassten Kosten, sonst `null`. */
    suspend fun lastUsedRepairCurrency(): Currency?
}
