package app.restvolt.camperlog.ui.data

import androidx.lifecycle.ViewModel
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository

/** Datenverwaltung: Export und Import der gespeicherten Touren. */
class DataViewModel(private val repository: TourRepository) : ViewModel() {

    /** Alle Touren in chronologischer Reihenfolge für den CSV-Export. */
    suspend fun toursForExport(): List<Tour> = repository.allTours()
}
