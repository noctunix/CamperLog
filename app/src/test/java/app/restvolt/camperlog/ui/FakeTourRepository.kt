package app.restvolt.camperlog.ui

import android.database.sqlite.SQLiteException
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.YearTotals
import app.restvolt.camperlog.domain.sumByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Currency

/**
 * Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomTourRepositoryTest.
 * Wie Room ordnet es Touren ohne Fahrzeug ([Tour.vehicleId] 0) dem Fahrzeug [currentVehicleId] zu.
 */
class FakeTourRepository(
    initial: List<Tour> = emptyList(),
    private val currentVehicleId: () -> Long = { 1L },
) : TourRepository {

    private val state = MutableStateFlow(initial.map { it.withVehicle() })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val tours: List<Tour> get() = state.value

    override fun observeTours(): Flow<List<Tour>> =
        state.map { list -> list.sortedWith(compareByDescending<Tour> { it.startDate }.thenByDescending { it.id }) }

    override fun observeTour(id: Long): Flow<Tour?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun allTours(): List<Tour> {
        if (failExportRead) throw SQLiteException("simulierter Lesefehler")
        return state.value.sortedBy { it.startDate }
    }

    override suspend fun hasTours(): Boolean = state.value.isNotEmpty()

    /** Simuliert eine volle oder defekte Datenbank: Schreibzugriffe werfen dann eine [SQLiteException]. */
    var failWrites = false

    /** Lässt [allTours] (den CSV-Export) mit einer [SQLiteException] scheitern. */
    var failExportRead = false

    private fun checkWritable() {
        if (failWrites) throw SQLiteException("simulierter Schreibfehler")
    }

    override suspend fun save(tour: Tour): Long {
        checkWritable()
        val now = Instant.EPOCH
        return if (tour.id == 0L) {
            val id = nextId++
            state.value += tour.withVehicle().copy(id = id, createdAt = now, updatedAt = now)
            id
        } else {
            state.value = state.value.map { if (it.id == tour.id) tour.copy(updatedAt = now) else it }
            tour.id
        }
    }

    override suspend fun delete(id: Long) {
        checkWritable()
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(tour: Tour) {
        checkWritable()
        state.value += tour
    }

    /** Wie die Room-Abfrage: letzte Währung der zuletzt geänderten Tour mit Kosten. */
    override suspend fun lastUsedCurrency(): Currency? = state.value
        .filter { it.costs.isNotEmpty() }
        .maxWithOrNull(compareBy<Tour> { it.updatedAt }.thenBy { it.id })
        ?.costs?.last()?.currency

    private fun Tour.withVehicle() = if (vehicleId == 0L) copy(vehicleId = currentVehicleId()) else this

    private fun List<Tour>.filterByVehicle(vehicleId: Long?) = filter { vehicleId == null || it.vehicleId == vehicleId }

    override fun observeTotals(vehicleId: Long?): Flow<TourTotals> =
        state.map { list -> totalsOf(list.filterByVehicle(vehicleId)) }

    override fun observeYearTotals(vehicleId: Long?): Flow<List<YearTotals>> = state.map { list ->
        list.filterByVehicle(vehicleId)
            .groupBy { it.year }.toSortedMap(reverseOrder()).map { (year, tours) -> YearTotals(year, totalsOf(tours)) }
    }

    private fun totalsOf(tours: List<Tour>) = TourTotals(
        tours = tours.size,
        distanceKm = tours.sumOf { it.distanceKm.toLong() },
        travelDays = tours.sumOf { it.travelDays.toLong() },
        overnightStays = tours.sumOf { it.overnightStays.toLong() },
        costs = tours.flatMap(Tour::costs).sumByCurrency(),
    )
}
