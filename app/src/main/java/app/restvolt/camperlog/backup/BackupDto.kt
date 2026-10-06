package app.restvolt.camperlog.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * JSON-Abbild des Sicherungsformats. Alle Werte sind bewusst einfache Texte und Zahlen, damit
 * die Prüfung in Backup.kt die Kontrolle über Fehler behält und die Datei menschenlesbar bleibt:
 * Datum ISO-8601, Zeitpunkte als UTC-Instant, Beträge als Dezimaltext mit Punkt,
 * Währungen als ISO-4217-Code, Auswahlwerte als Enum-Name.
 */

@Serializable
internal data class BackupDto(
    val format: String,
    val schemaVersion: Int,
    val exportedAt: String,
    val mainCurrency: String,
    val exchangeRates: List<RateDto>,
    /** Einzeln als [TourDto] gelesen, damit Fehler einer Tour zugeordnet werden können. */
    val tours: List<JsonElement>,
    /** Einzeln als [VehicleDto] gelesen; fehlt in Sicherungen der Formatversion 1. */
    val vehicles: List<JsonElement> = emptyList(),
    /** uuid des aktuellen Fahrzeugs; fehlt in Sicherungen der Formatversion 1. */
    val currentVehicle: String? = null,
    /** Einzeln als [StationDto] gelesen; fehlt in Sicherungen vor Formatversion 3. */
    val stations: List<JsonElement> = emptyList(),
)

@Serializable
internal data class RateDto(
    val currency: String,
    val perEuro: String,
    val date: String,
    val source: String = "",
)

@Serializable
internal data class CostDto(
    val currency: String,
    val amount: String,
)

@Serializable
internal data class TourDto(
    val uuid: String,
    val startDate: String,
    val endDate: String,
    val destination: String,
    val tourType: String,
    val travelDays: Int,
    val overnightStays: Int,
    val distanceKm: Int,
    val costs: List<CostDto> = emptyList(),
    val notes: String = "",
    val mapLink: String? = null,
    val createdAt: String,
    val updatedAt: String,
    /** uuid des Fahrzeugs dieser Tour; `null` bedeutet beim Import „aktuelles Fahrzeug" (Formatversion 1). */
    val vehicleUuid: String? = null,
    /**
     * Nur in Sicherungen vor Formatversion 3: die alten Stellplatz-Felder der Tour, zusammen
     * gelesen und beim Import nach [app.restvolt.camperlog.domain.migrateLegacyPitch] in eine
     * Übernachtungs-Station umgewandelt (3.4). Neue Sicherungen lassen sie leer.
     */
    val pitchAssigned: Boolean? = null,
    val electricityFlatRate: String? = null,
    val lteQuality: String? = null,
    val pitchSlope: String? = null,
    val levelingBlocksUsed: Boolean? = null,
)

@Serializable
internal data class VehicleDto(
    val uuid: String,
    val name: String = "",
    val licensePlate: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val vin: String = "",
    val firstRegistration: String? = null,
    val notes: String = "",
    val purchaseDate: String? = null,
    val purchasePrice: CostDto? = null,
    val purchaseOdometerKm: Int? = null,
    val saleDate: String? = null,
    val salePrice: CostDto? = null,
    val insurer: String = "",
    val insurancePolicyNumber: String = "",
    val insurancePremiumPerYear: CostDto? = null,
    val vehicleTaxPerYear: CostDto? = null,
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
    val fuelTankDl: Int? = null,
    val adBlueTankDl: Int? = null,
    val freshWaterTankDl: Int? = null,
    val greyWaterTankDl: Int? = null,
    val boilerDl: Int? = null,
    val cassetteDl: Int? = null,
    val batteryCapacityAh: Int? = null,
    val solarPowerWp: Int? = null,
    val nextInspectionDate: String? = null,
    val nextGasCheckDate: String? = null,
    val lastOilChangeDate: String? = null,
    val lastOilChangeOdometerKm: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    /** Einzeln als [RepairDto] gelesen, damit Fehler einer Reparatur zugeordnet werden können. */
    val repairs: List<JsonElement> = emptyList(),
    /** Einzeln als [LogEntryDto] gelesen, damit Fehler eines Bordbuch-Eintrags zugeordnet werden können. */
    val logEntries: List<JsonElement> = emptyList(),
)

@Serializable
internal data class RepairDto(
    val uuid: String,
    val date: String,
    val description: String,
    val odometerKm: Int? = null,
    val cost: CostDto? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class LogEntryDto(
    val uuid: String,
    val type: String,
    val date: String,
    val createdAt: String,
)

@Serializable
internal data class StationDto(
    val uuid: String,
    val type: String,
    val date: String,
    val time: String? = null,
    val name: String = "",
    val place: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateSource: String? = null,
    val accuracyM: Int? = null,
    val mapLink: String? = null,
    val notes: String = "",
    val nights: Int? = null,
    val siteKind: String? = null,
    val pitchAssigned: Boolean? = null,
    val electricityFlatRate: String? = null,
    val lteQuality: String? = null,
    val pitchSlope: String? = null,
    val levelingBlocksUsed: Boolean? = null,
    val services: List<String> = emptyList(),
    val weather: WeatherDto? = null,
    val favorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    /** uuid des Fahrzeugs dieser Station; anders als bei Touren immer gesetzt. */
    val vehicleUuid: String,
    /** uuid der Tour dieser Station; `null` bei einer eigenständigen Station ohne Tour. */
    val tourUuid: String? = null,
)

@Serializable
internal data class WeatherDto(
    val temperatureDeciC: Int,
    val weatherCode: Int,
    val windKmh: Int,
    val gustKmh: Int? = null,
    val windDirectionDeg: Int? = null,
    val observedAt: String,
)
