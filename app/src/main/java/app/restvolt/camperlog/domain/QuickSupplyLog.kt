package app.restvolt.camperlog.domain

import java.time.Instant
import java.time.LocalDate

/** [LogType] → [StationService], die Umkehrung von [SYNCED_SERVICE_LOG_TYPES]. */
private val SYNCED_LOG_TYPE_SERVICES: Map<LogType, StationService> =
    SYNCED_SERVICE_LOG_TYPES.entries.associate { (service, type) -> type to service }

/** [StationService] zu einem per Bordbuch-Kachel erfassten [type]; `null` für Dieselheizung/Gasheizung ohne Stations-Entsprechung. */
fun quickSupplyService(type: LogType): StationService? = SYNCED_LOG_TYPE_SERVICES[type]

/**
 * Station für einen Ein-Klick-Bordbuch-Eintrag von [service] an [date]: [existing] erweitert um den
 * Dienst, oder eine neue Ver-/Entsorgungs-Station, wenn an diesem Tag noch keine existiert.
 *
 * @param existing die Ver-/Entsorgungs-Station des Fahrzeugs an [date], falls schon vorhanden
 * @param vehicleId das Fahrzeug der neuen Station, nur relevant ohne [existing]
 * @param service der erfasste Dienst
 * @param date der Tag der Erfassung, nur relevant ohne [existing]
 * @param tourId die Tour der neuen Station, nur relevant ohne [existing]
 * @return die zu speichernde Station
 */
fun quickSupplyStation(existing: Station?, vehicleId: Long, service: StationService, date: LocalDate, tourId: Long?): Station =
    existing?.copy(services = existing.services + service) ?: Station(
        vehicleId = vehicleId,
        tourId = tourId,
        type = StationType.SUPPLY,
        date = date,
        services = setOf(service),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

/**
 * Station nach dem Entfernen von [service] (Rückgängig einer Bordbuch-Kachel): [station] ohne den
 * Dienst, oder `null`, wenn dadurch weder Dienste noch sonstige Inhalte (Name, Ort, Notizen, Kosten,
 * Koordinaten) übrig bleiben – dann wird die Station komplett gelöscht statt als leere Hülle bestehen zu bleiben.
 *
 * @param station die Station vor dem Entfernen
 * @param service der zu entfernende Dienst
 * @return die zu speichernde Station, oder `null`, wenn sie stattdessen gelöscht werden soll
 */
fun stationAfterRemovingQuickSupply(station: Station, service: StationService): Station? {
    val updated = station.copy(services = station.services - service)
    val hasOtherContent = updated.name.isNotBlank() || updated.place.isNotBlank() || updated.notes.isNotBlank() ||
        updated.costs.isNotEmpty() || updated.latitude != null || updated.longitude != null
    return updated.takeIf { updated.services.isNotEmpty() || hasOtherContent }
}
