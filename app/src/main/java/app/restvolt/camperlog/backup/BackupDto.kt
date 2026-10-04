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
    val pitchAssigned: Boolean,
    val electricityFlatRate: String,
    val lteQuality: String,
    val pitchSlope: String,
    val levelingBlocksUsed: Boolean,
    val notes: String = "",
    val mapLink: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
