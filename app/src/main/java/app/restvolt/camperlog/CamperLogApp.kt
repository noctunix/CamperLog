package app.restvolt.camperlog

import android.app.Application
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.data.CamperLogDatabase
import app.restvolt.camperlog.data.RoomBackupImporter
import app.restvolt.camperlog.data.RoomExchangeRateRepository
import app.restvolt.camperlog.data.RoomLogRepository
import app.restvolt.camperlog.data.RoomStationRepository
import app.restvolt.camperlog.data.RoomTourRepository
import app.restvolt.camperlog.data.RoomVehicleRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleRepository

/** Application-Klasse; hält die einzige Datenbank- und Repository-Instanz. */
class CamperLogApp : Application() {

    /** Gemeinsames Repository für alle Screens. */
    val repository: TourRepository by lazy { RoomTourRepository(database.tourDao(), database.vehicleDao()) }

    /** Wechselkurse und Hauptwährung. */
    val exchangeRates: ExchangeRateRepository by lazy { RoomExchangeRateRepository(database.exchangeRateDao()) }

    /** Fahrzeuge und ihre Reparaturen. */
    val vehicles: VehicleRepository by lazy { RoomVehicleRepository(database.vehicleDao()) }

    /** Bordbuch-Einträge. */
    val logbook: LogRepository by lazy { RoomLogRepository(database.logDao()) }

    /** Stationen (Übernachtungen, Ver-/Entsorgung, Tanken, …). */
    val stations: StationRepository by lazy { RoomStationRepository(database.stationDao()) }

    /** Einspielen von JSON-Sicherungen. */
    val backupImporter: BackupImporter by lazy { RoomBackupImporter(database) }

    private val database by lazy { CamperLogDatabase.open(this) }
}
