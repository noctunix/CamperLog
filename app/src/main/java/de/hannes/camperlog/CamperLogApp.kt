package de.hannes.camperlog

import android.app.Application
import de.hannes.camperlog.data.CamperLogDatabase
import de.hannes.camperlog.data.RoomTourRepository
import de.hannes.camperlog.domain.TourRepository

/** Application-Klasse; hält die einzige Datenbank- und Repository-Instanz. */
class CamperLogApp : Application() {

    /** Gemeinsames Repository für alle Screens. */
    val repository: TourRepository by lazy {
        RoomTourRepository(CamperLogDatabase.open(this).tourDao())
    }
}
