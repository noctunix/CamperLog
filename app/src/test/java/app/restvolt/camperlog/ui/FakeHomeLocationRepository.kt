package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomHomeLocationRepositoryTest. */
class FakeHomeLocationRepository(initial: List<HomeLocation> = emptyList()) : HomeLocationRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val locations: List<HomeLocation> get() = state.value

    override fun observeAll(): Flow<List<HomeLocation>> = state

    override suspend fun locationAt(date: LocalDate): HomeLocation? = state.value.firstOrNull { location ->
        location.hasLocation &&
            (location.validFrom == null || !location.validFrom.isAfter(date)) &&
            (location.validTo == null || !location.validTo.isBefore(date))
    }

    override suspend fun save(location: HomeLocation): Long {
        val now = Instant.EPOCH
        return if (location.id == 0L) {
            val saved = location.copy(id = nextId++, createdAt = now, updatedAt = now)
            state.value += saved
            saved.id
        } else {
            state.value = state.value.map { if (it.id == location.id) location.copy(updatedAt = now) else it }
            location.id
        }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
