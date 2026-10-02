package de.hannes.camperlog.ui

import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourRepository
import de.hannes.camperlog.domain.TourTotals
import de.hannes.camperlog.domain.YearTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomTourRepositoryTest. */
class FakeTourRepository(initial: List<Tour> = emptyList()) : TourRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val tours: List<Tour> get() = state.value

    override fun observeTours(): Flow<List<Tour>> =
        state.map { list -> list.sortedWith(compareByDescending<Tour> { it.startDate }.thenByDescending { it.id }) }

    override fun observeTour(id: Long): Flow<Tour?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun allTours(): List<Tour> = state.value.sortedBy { it.startDate }

    override suspend fun save(tour: Tour): Long {
        val now = Instant.EPOCH
        return if (tour.id == 0L) {
            val id = nextId++
            state.value += tour.copy(id = id, createdAt = now, updatedAt = now)
            id
        } else {
            state.value = state.value.map { if (it.id == tour.id) tour.copy(updatedAt = now) else it }
            tour.id
        }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeTotals(): Flow<TourTotals> = state.map(::totalsOf)

    override fun observeYearTotals(): Flow<List<YearTotals>> = state.map { list ->
        list.groupBy { it.year }.toSortedMap(reverseOrder()).map { (year, tours) -> YearTotals(year, totalsOf(tours)) }
    }

    private fun totalsOf(tours: List<Tour>) = TourTotals(
        tours = tours.size,
        distanceKm = tours.sumOf { it.distanceKm.toLong() },
        travelDays = tours.sumOf { it.travelDays.toLong() },
        overnightStays = tours.sumOf { it.overnightStays.toLong() },
        costCents = tours.sumOf { it.costCents },
    )
}
