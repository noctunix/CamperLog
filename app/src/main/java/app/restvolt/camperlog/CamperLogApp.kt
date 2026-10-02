package app.restvolt.camperlog

import android.app.Application
import app.restvolt.camperlog.data.CamperLogDatabase
import app.restvolt.camperlog.data.RoomTourRepository
import app.restvolt.camperlog.domain.TourRepository

/** Application-Klasse; hält die einzige Datenbank- und Repository-Instanz. */
class CamperLogApp : Application() {

    /** Gemeinsames Repository für alle Screens. */
    val repository: TourRepository by lazy {
        RoomTourRepository(CamperLogDatabase.open(this).tourDao())
    }
}
