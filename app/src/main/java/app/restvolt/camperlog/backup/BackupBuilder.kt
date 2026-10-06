package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Baut die vollständige [Backup] des aktuellen Stands; [exportedAt] wird unverändert übernommen.
 * Geteilt zwischen dem manuellen Export (`DataViewModel`) und dem täglichen Hintergrund-Check, der
 * die Sicherung ggf. automatisch in den Sicherungsordner schreibt.
 */
suspend fun buildBackup(
    repository: TourRepository,
    exchangeRates: ExchangeRateRepository,
    vehicles: VehicleRepository,
    logs: LogRepository,
    stations: StationRepository,
    exportedAt: Instant,
): Backup {
    val tours = repository.allTours()
    val allVehicles = vehicles.allVehicles()
    val allStations = stations.allStations()
    val repairsByVehicle = vehicles.allRepairs().groupBy { it.vehicleId }
    val logEntriesByVehicle = logs.allEntries().groupBy { it.vehicleId }
    val vehicleUuidById = allVehicles.associate { it.id to it.uuid }
    val tourUuidById = tours.associate { it.id to it.uuid }
    val stationUuidById = allStations.associate { it.id to it.uuid }
    return Backup(
        exportedAt = exportedAt,
        mainCurrency = exchangeRates.observeMainCurrency().first(),
        rates = exchangeRates.observeRates().first(),
        tours = tours,
        tourVehicleUuid = tours.mapNotNull { tour -> vehicleUuidById[tour.vehicleId]?.let { tour.uuid to it } }.toMap(),
        vehicles = allVehicles.map { vehicle ->
            BackupVehicle(
                vehicle = vehicle,
                repairs = repairsByVehicle[vehicle.id].orEmpty(),
                logEntries = logEntriesByVehicle[vehicle.id].orEmpty(),
            )
        },
        currentVehicleUuid = vehicles.observeCurrentVehicle().first().uuid,
        stations = allStations,
        stationVehicleUuid = allStations.mapNotNull { station -> vehicleUuidById[station.vehicleId]?.let { station.uuid to it } }.toMap(),
        stationTourUuid = allStations.mapNotNull { station ->
            station.tourId?.let { tourUuidById[it] }?.let { station.uuid to it }
        }.toMap(),
        logEntryStationUuid = logEntriesByVehicle.values.flatten().mapNotNull { entry ->
            entry.stationId?.let { stationUuidById[it] }?.let { entry.uuid to it }
        }.toMap(),
    )
}
