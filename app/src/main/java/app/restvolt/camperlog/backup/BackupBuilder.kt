package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocumentRepository
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
    documents: VehicleDocumentRepository,
    diaryEntries: DiaryEntryRepository,
    checklistTemplates: ChecklistTemplateRepository,
    checklists: ChecklistRepository,
    tracks: TrackRepository,
    attachments: AttachmentRepository,
    exportedAt: Instant,
): Backup {
    // Demo-Daten des Tutorials (siehe DemoTourSession) gehören nie in die Sicherung: ein beim
    // Absturz zurückgelassener Rest soll nicht erst im nächtlichen Backup landen, bevor die nächste
    // App-Öffnung ihn aufräumt.
    val demoTourIds = repository.demoTourIds().toHashSet()
    val demoVehicleIds = vehicles.allVehicles().filter(Vehicle::isDemo).mapTo(HashSet(), Vehicle::id)
    val tours = repository.allTours().filterNot { it.isDemo }
    val allVehicles = vehicles.allVehicles().filterNot { it.isDemo }
    val allStations = stations.allStations().filterNot { it.vehicleId in demoVehicleIds || it.tourId in demoTourIds }
    val repairsByVehicle = vehicles.allRepairs().groupBy { it.vehicleId }
    val logEntriesByVehicle = logs.allEntries().groupBy { it.vehicleId }
    val allDocuments = documents.allDocuments()
    val allDiaryEntries = diaryEntries.allEntries()
    val allChecklistTemplates = checklistTemplates.allTemplates()
    val allChecklists = checklists.allChecklists()
    val allTrackPoints = tracks.allPoints()
    val vehicleUuidById = allVehicles.associate { it.id to it.uuid }
    val tourUuidById = tours.associate { it.id to it.uuid }
    val stationUuidById = allStations.associate { it.id to it.uuid }
    val repairUuidById = repairsByVehicle.values.flatten().associate { it.id to it.uuid }
    val logEntryUuidById = logEntriesByVehicle.values.flatten().associate { it.id to it.uuid }
    val documentUuidById = allDocuments.associate { it.id to it.uuid }
    val backup = Backup(
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
        documents = allDocuments.mapNotNull { document ->
            vehicleUuidById[document.vehicleId]?.let { BackupVehicleDocument(document, it) }
        },
        diaryEntries = allDiaryEntries.mapNotNull { entry ->
            tourUuidById[entry.tourId]?.let { BackupDiaryEntry(entry, it) }
        },
        checklistTemplates = allChecklistTemplates,
        checklists = allChecklists.mapNotNull { checklist ->
            vehicleUuidById[checklist.vehicleId]?.let { vehicleUuid ->
                BackupChecklist(checklist, vehicleUuid, checklist.tourId?.let { tourUuidById[it] })
            }
        },
        tracks = allTrackPoints.groupBy { it.tourId }.mapNotNull { (tourId, points) ->
            tourUuidById[tourId]?.let { BackupTrack(it, points) }
        },
        attachments = attachments.allAttachments().mapNotNull { attachment ->
            val ownerUuid = when (attachment.ownerType) {
                AttachmentOwnerType.STATION -> stationUuidById[attachment.ownerId]
                AttachmentOwnerType.REPAIR -> repairUuidById[attachment.ownerId]
                AttachmentOwnerType.LOG_ENTRY -> logEntryUuidById[attachment.ownerId]
                AttachmentOwnerType.VEHICLE_DOCUMENT -> documentUuidById[attachment.ownerId]
            }
            ownerUuid?.let { BackupAttachment(attachment, it) }
        },
    )
    val zipPaths = buildAttachmentZipPaths(backup)
    return backup.copy(attachments = backup.attachments.map { it.copy(zipPath = zipPaths.getValue(it.attachment.uuid)) })
}
