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
 * Neue Tanken-Station für einen Ein-Klick-Bordbuch-Eintrag an [date]. Jeder Tap legt eine eigene
 * Station an (mehrere Tankstopps pro Tag sind möglich); der Bordbuch-Abgleich erzeugt den
 * verknüpften [LogType.FUELED]-Eintrag.
 *
 * @param vehicleId das Fahrzeug der Station
 * @param date der Tag der Erfassung
 * @param tourId die Tour der Station, falls eine am Tag läuft
 * @return die zu speichernde Station
 */
fun quickFuelStation(vehicleId: Long, date: LocalDate, tourId: Long?): Station = Station(
    vehicleId = vehicleId,
    tourId = tourId,
    type = StationType.FUEL,
    date = date,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

/** Ob [station] außer ihrem Typ keinen eigenen Inhalt hat (Dienste, Name, Ort, Notizen, Kosten, Koordinaten, Liter). */
private val Station.isBare: Boolean
    get() = services.isEmpty() && name.isBlank() && place.isBlank() && notes.isBlank() && costs.isEmpty() &&
        latitude == null && longitude == null && fuelLiters == null

/**
 * Macht den mit [station] verknüpften Bordbuch-Eintrag [entry] rückgängig. Bei einem
 * Versorgungsdienst wird der Dienst entfernt und der Abgleich löscht den Eintrag selbst; bei
 * [LogType.FUELED] wird der Eintrag direkt gelöscht. Bleibt die Station danach ganz ohne Inhalt,
 * wird die leere Hülle gelöscht (ein reines Löschen der Station würde den Eintrag nur entknüpfen).
 *
 * @param stations Zugriff auf die Stationen
 * @param logs Zugriff auf das Bordbuch
 * @param station die Station vor dem Rückgängigmachen
 * @param entry der zu entfernende, mit [station] verknüpfte Eintrag
 */
suspend fun removeLinkedLogEntry(stations: StationRepository, logs: LogRepository, station: Station, entry: LogEntry) {
    val service = quickSupplyService(entry.type)
    if (service == null) {
        logs.delete(entry.id)
        if (station.isBare) stations.delete(station.id)
        return
    }
    val updated = stationAfterRemovingQuickSupply(station, service)
    stations.save(updated ?: station.copy(services = station.services - service))
    if (updated == null) stations.delete(station.id)
}

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
