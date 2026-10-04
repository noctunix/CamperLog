package app.restvolt.camperlog

import android.app.Application
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.data.CamperLogDatabase
import app.restvolt.camperlog.data.RoomBackupImporter
import app.restvolt.camperlog.data.RoomExchangeRateRepository
import app.restvolt.camperlog.data.RoomTourRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.TourRepository

/** Application-Klasse; hält die einzige Datenbank- und Repository-Instanz. */
class CamperLogApp : Application() {

    /** Gemeinsames Repository für alle Screens. */
    val repository: TourRepository by lazy { RoomTourRepository(database.tourDao()) }

    /** Wechselkurse und Hauptwährung. */
    val exchangeRates: ExchangeRateRepository by lazy { RoomExchangeRateRepository(database.exchangeRateDao()) }

    /** Einspielen von JSON-Sicherungen. */
    val backupImporter: BackupImporter by lazy { RoomBackupImporter(database) }

    private val database by lazy { CamperLogDatabase.open(this) }
}
