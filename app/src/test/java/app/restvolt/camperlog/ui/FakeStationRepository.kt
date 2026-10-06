package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomStationRepositoryTest. */
class FakeStationRepository(initial: List<Station> = emptyList()) : StationRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val stations: List<Station> get() = state.value

    override fun observeForTour(tourId: Long): Flow<List<Station>> = state.map { list ->
        list.filter { it.tourId == tourId }
            .sortedWith(compareBy({ it.date }, { it.time ?: java.time.LocalTime.MAX }, { it.createdAt }))
    }

    override fun observeForVehicle(vehicleId: Long?): Flow<List<Station>> = state.map { list ->
        list.filter { vehicleId == null || it.vehicleId == vehicleId }
            .sortedWith(compareByDescending<Station> { it.date }.thenByDescending { it.createdAt })
    }

    override fun observeStation(id: Long): Flow<Station?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun allStations(): List<Station> = state.value

    override suspend fun save(station: Station): Long {
        val now = Instant.EPOCH
        return if (station.id == 0L) {
            val id = nextId++
            state.value += station.copy(id = id, createdAt = now, updatedAt = now)
            id
        } else {
            state.value = state.value.map { if (it.id == station.id) station.copy(updatedAt = now) else it }
            station.id
        }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(station: Station) {
        state.value += station
    }

    override suspend fun defaultTourId(vehicleId: Long, date: LocalDate): Long? = null
}
