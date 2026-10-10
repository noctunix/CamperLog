package app.restvolt.camperlog

import android.app.Application
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.data.AndroidAttachmentFileStore
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.CamperLogDatabase
import app.restvolt.camperlog.data.RoomAttachmentRepository
import app.restvolt.camperlog.data.RoomBackupImporter
import app.restvolt.camperlog.data.RoomChecklistRepository
import app.restvolt.camperlog.data.RoomChecklistTemplateRepository
import app.restvolt.camperlog.data.RoomDiaryEntryRepository
import app.restvolt.camperlog.data.RoomExchangeRateRepository
import app.restvolt.camperlog.data.RoomLogRepository
import app.restvolt.camperlog.data.RoomStationRepository
import app.restvolt.camperlog.data.RoomTourRepository
import app.restvolt.camperlog.data.RoomTrackRepository
import app.restvolt.camperlog.data.RoomVehicleDocumentRepository
import app.restvolt.camperlog.data.RoomVehicleRepository
import app.restvolt.camperlog.data.TileHttpCache
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.guide.DemoTourContent
import app.restvolt.camperlog.domain.guide.DemoTourSession
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.ui.stations.StationsWhatsNewSettings

/** Application-Klasse; hält die einzige Datenbank- und Repository-Instanz. */
class CamperLogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TileHttpCache.install(this)
    }

    /** Gemeinsames Repository für alle Screens. */
    val repository: TourRepository by lazy { RoomTourRepository(database) }

    /** Wechselkurse und Hauptwährung. */
    val exchangeRates: ExchangeRateRepository by lazy { RoomExchangeRateRepository(database.exchangeRateDao()) }

    /** Fahrzeuge und ihre Reparaturen. */
    val vehicles: VehicleRepository by lazy { RoomVehicleRepository(database) }

    /** Bordbuch-Einträge. */
    val logbook: LogRepository by lazy { RoomLogRepository(database) }

    /** Stationen (Übernachtungen, Ver-/Entsorgung, Tanken, …). */
    val stations: StationRepository by lazy { RoomStationRepository(database, logbook) }

    /** Fahrzeugdokumente (Fahrzeugschein, Versicherung, Garantie, …). */
    val vehicleDocuments: VehicleDocumentRepository by lazy { RoomVehicleDocumentRepository(database) }

    /** Tagebucheinträge der Touren. */
    val diaryEntries: DiaryEntryRepository by lazy { RoomDiaryEntryRepository(database) }

    /** Checklisten-Vorlagen (Abfahrt, Ankunft, Einwintern, …). */
    val checklistTemplates: ChecklistTemplateRepository by lazy { RoomChecklistTemplateRepository(database) }

    /** Gestartete Checklisten einer Tour oder eines Fahrzeugs. */
    val checklists: ChecklistRepository by lazy { RoomChecklistRepository(database) }

    /** Aufgezeichnete Trackpunkte der Touren. */
    val tracks: TrackRepository by lazy { RoomTrackRepository(database) }

    /** Dateizugriff für [attachments]: Import von einer Content-Uri, Löschen, Aufräumen verwaister Dateien. */
    val attachmentFileStore: AttachmentFileStore by lazy { AndroidAttachmentFileStore(this) }

    /** Fotos und Dokumentdateien zu Stationen, Reparaturen, Bordbuch-Einträgen und Fahrzeugdokumenten. */
    val attachments: AttachmentRepository by lazy { RoomAttachmentRepository(database.attachmentDao(), attachmentFileStore) }

    /** Einspielen von JSON- oder ZIP-Sicherungen (Datenbankteil; das Verschieben von ZIP-Dateien übernimmt der Aufrufer). */
    val backupImporter: BackupImporter by lazy { RoomBackupImporter(database) }

    /** Simulierte Demo-Tour des Tutorials (siehe [DemoTourSession]). */
    val demoTourSession: DemoTourSession by lazy {
        DemoTourSession(
            repository,
            stations,
            tracks,
            vehicles,
            content = DemoTourContent(
                vehicleName = getString(R.string.demo_vehicle_name),
                destination = getString(R.string.demo_tour_destination),
                stationNames = listOf(
                    getString(R.string.demo_station_1_name),
                    getString(R.string.demo_station_2_name),
                    getString(R.string.demo_station_3_name),
                ),
            ),
        ) { tourId -> TrackRecordingService.stopIfTracking(this, tourId) }
    }

    private val database by lazy { CamperLogDatabase.open(this) { StationsWhatsNewSettings(this).pending = true } }
}
