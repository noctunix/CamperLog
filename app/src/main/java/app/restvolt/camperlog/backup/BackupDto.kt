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
    /** Einzeln als [VehicleDocumentDto] gelesen; fehlt in Sicherungen vor Formatversion 6. */
    val vehicleDocuments: List<JsonElement> = emptyList(),
    /** Einzeln als [AttachmentDto] gelesen; fehlt in Sicherungen vor Formatversion 6 oder in einer Sicherung ohne Dateien. */
    val attachments: List<JsonElement> = emptyList(),
    /** Einzeln als [DiaryEntryDto] gelesen; fehlt in Sicherungen vor Formatversion 8. */
    val diaryEntries: List<JsonElement> = emptyList(),
    /** Einzeln als [ChecklistTemplateDto] gelesen; fehlt in Sicherungen vor Formatversion 9. */
    val checklistTemplates: List<JsonElement> = emptyList(),
    /** Einzeln als [ChecklistDto] gelesen; fehlt in Sicherungen vor Formatversion 9. */
    val checklists: List<JsonElement> = emptyList(),
    /** Einzeln als [TrackDto] gelesen; fehlt in Sicherungen vor Formatversion 10. */
    val tracks: List<JsonElement> = emptyList(),
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
    /** `null` kennzeichnet ab Sicherungsformat 11 eine laufende Tour. */
    val endDate: String? = null,
    val destination: String,
    /** Freitext-Name der Tour; fehlt in Sicherungen vor Formatversion 12. */
    val name: String = "",
    /** URL-/dateinamensicherer Kurzname für Berichte und Exporte; fehlt in Sicherungen vor Formatversion 12. */
    val slug: String = "",
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
     * Nur in Sicherungen vor Formatversion 3: die alten Stellplatz-Felder der Tour, einzeln gelesen
     * und beim Import nach [app.restvolt.camperlog.domain.migrateLegacyPitch] in eine
     * Übernachtungs-Station umgewandelt. Neue Sicherungen lassen sie leer. Ein fehlendes oder
     * unbekanntes Feld wird beim Import `null`, statt die ganze Tour abzulehnen.
     */
    val pitchAssigned: Boolean? = null,
    val electricityFlatRate: String? = null,
    val lteQuality: String? = null,
    val pitchSlope: String? = null,
    val levelingBlocksUsed: Boolean? = null,
    /** ISO-3166-1-alpha-2-Codes; fehlt in Sicherungen vor Formatversion 7 (siehe [app.restvolt.camperlog.domain.Tour]). */
    val manualCountriesAdded: List<String> = emptyList(),
    /** ISO-3166-1-alpha-2-Codes; fehlt in Sicherungen vor Formatversion 7. */
    val manualCountriesRemoved: List<String> = emptyList(),
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
    /** Fehlt in Sicherungen vor Formatversion 15. */
    val saleOdometerKm: Int? = null,
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
    /** Fehlt in Sicherungen vor Formatversion 15. */
    val displacementCc: Int? = null,
    /** Name von [app.restvolt.camperlog.domain.TransmissionType]; fehlt in Sicherungen vor Formatversion 15. */
    val transmission: String? = null,
    val tireSize: String = "",
    val tirePressureFrontMbar: Int? = null,
    val tirePressureRearMbar: Int? = null,
    /** [app.restvolt.camperlog.domain.EnergyType]-Namen; fehlt in Sicherungen vor Formatversion 14. */
    val requiredEnergyTypes: List<String> = emptyList(),
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
    val nextLeakTestDate: String? = null,
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
    /** uuid der verknüpften Station; `null` ohne Verknüpfung oder in Sicherungen vor Formatversion 4. */
    val stationUuid: String? = null,
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
    val lteQuality: String? = null,
    val pitchSlope: String? = null,
    val levelingBlocksUsed: Boolean? = null,
    /**
     * Nur in Sicherungen vor Formatversion 5: die alte Strompauschale der Station, beim Import nach
     * [app.restvolt.camperlog.domain.migrateLegacyElectricityFlatRate] auf [electricityBilling]
     * umgelegt. Neue Sicherungen lassen sie leer. Ein unbekannter Wert wird beim Import `null`.
     */
    val electricityFlatRate: String? = null,
    val electricityBilling: String? = null,
    val electricityCurrency: String? = null,
    val electricityFlatAmount: String? = null,
    val electricityBaseFee: String? = null,
    val electricityPricePerKwh: String? = null,
    val electricityCoinPrice: String? = null,
    val electricityCoinsUsed: Int? = null,
    val electricityKwhPerCoin: String? = null,
    val electricityMeterStart: String? = null,
    val electricityMeterEnd: String? = null,
    val electricityKwhUsed: String? = null,
    val tollKind: String? = null,
    val tollPaymentMethod: String = "",
    val tollCountry: String? = null,
    val tollValidFrom: String? = null,
    val tollValidUntil: String? = null,
    val ferryBookingReference: String = "",
    val costs: List<StationCostDto> = emptyList(),
    val services: List<String> = emptyList(),
    val weather: WeatherDto? = null,
    val favorite: Boolean = false,
    /** Fehlt in Sicherungen vor Formatversion 13. */
    val rating: Int? = null,
    /** Fehlt in Sicherungen vor Formatversion 13. */
    val odometerKm: Int? = null,
    /** Fehlt in Sicherungen vor Formatversion 13. */
    val manualTemperatureDeciC: Int? = null,
    /** Fehlt in Sicherungen vor Formatversion 13. */
    val link: String? = null,
    val createdAt: String,
    val updatedAt: String,
    /** uuid des Fahrzeugs dieser Station; anders als bei Touren immer gesetzt. */
    val vehicleUuid: String,
    /** uuid der Tour dieser Station; `null` bei einer eigenständigen Station ohne Tour. */
    val tourUuid: String? = null,
)

@Serializable
internal data class StationCostDto(
    val category: String,
    val currency: String,
    val amount: String,
    val note: String = "",
)

@Serializable
internal data class VehicleDocumentDto(
    val uuid: String,
    val vehicleUuid: String,
    val kind: String,
    val title: String = "",
    val expiryDate: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class AttachmentDto(
    val uuid: String,
    /** Name von [app.restvolt.camperlog.domain.AttachmentOwnerType], z. B. `STATION`. */
    val ownerType: String,
    /** uuid des Eintrags, zu dem der Anhang gehört (Station, Reparatur, Bordbuch-Eintrag oder Fahrzeugdokument je [ownerType]). */
    val ownerUuid: String,
    /** Interner Dateiname `<uuid>.<ext>` im Anhangs-Ordner des Geräts; in einer Sicherung ohne Dateien trotzdem vorhanden. */
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val caption: String = "",
    val createdAt: String,
    /** Menschenlesbarer Pfad der Anhangsdatei in der ZIP-Sicherung, siehe `buildAttachmentZipPaths`. */
    val zipPath: String = "",
)

@Serializable
internal data class DiaryEntryDto(
    val uuid: String,
    /** uuid der Tour dieses Eintrags. */
    val tourUuid: String,
    val date: String,
    val text: String = "",
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class ChecklistTemplateDto(
    val uuid: String,
    val name: String = "",
    val items: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class ChecklistItemDto(
    val text: String = "",
    val checked: Boolean = false,
)

@Serializable
internal data class ChecklistDto(
    val uuid: String,
    val vehicleUuid: String,
    /** uuid der Tour dieser Checkliste; `null` ohne Tourbezug (z. B. Einwintern). */
    val tourUuid: String? = null,
    val title: String = "",
    val items: List<ChecklistItemDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Aufgezeichneter Track einer Tour, nach Segmenten gruppiert. Jeder Punkt ist bewusst ein kompakter
 * Text `"zeitpunktMillis,breite,länge,genauigkeitM,höheM"` (die beiden letzten dürfen leer sein),
 * damit lange Aufzeichnungen trotz eingerückter JSON-Ausgabe die Größengrenze der Sicherung nicht sprengen.
 */
@Serializable
internal data class TrackDto(
    val tourUuid: String,
    val segments: List<TrackSegmentDto> = emptyList(),
)

@Serializable
internal data class TrackSegmentDto(
    val segment: Int,
    val points: List<String> = emptyList(),
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
