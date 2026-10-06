package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogSyncAction
import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.syncStationLogEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomStationRepositoryTest.
 * [logs] gleicht beim Speichern und Löschen die Ver-/Entsorgungs-Häkchen mit dem Bordbuch ab (4), wie
 * es in echt [app.restvolt.camperlog.data.RoomStationRepository] innerhalb einer Transaktion tut.
 */
class FakeStationRepository(initial: List<Station> = emptyList(), private val logs: FakeLogRepository = FakeLogRepository()) : StationRepository {

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
        val old = if (station.id != 0L) state.value.firstOrNull { it.id == station.id } else null
        val now = Instant.EPOCH
        val saved = if (station.id == 0L) {
            station.copy(id = nextId++, createdAt = now, updatedAt = now)
        } else {
            station.copy(updatedAt = now)
        }
        state.value = if (old == null) state.value + saved else state.value.map { if (it.id == saved.id) saved else it }
        applyLogSync(old, saved)
        return saved.id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
        logs.detachStation(id)
    }

    override suspend fun restore(station: Station) {
        state.value += station
    }

    override suspend fun defaultTourId(vehicleId: Long, date: LocalDate): Long? = null

    override suspend fun linkedLogEntries(stationId: Long): List<LogEntry> =
        SYNCED_SERVICE_LOG_TYPES.values.mapNotNull { type -> logs.linkedEntry(stationId, type) }

    override suspend fun relinkLogEntries(entryIds: List<Long>, stationId: Long) {
        entryIds.forEach { entryId -> logs.link(entryId, stationId) }
    }

    override suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long) {
        state.value.filter { it.tourId == tourId && it.vehicleId != vehicleId }.forEach { save(it.copy(vehicleId = vehicleId)) }
    }

    /** Wendet die Bordbuch-Angleichung aus [syncStationLogEntries] auf [new] an (4), wie [save] in echt tut. */
    private suspend fun applyLogSync(old: Station?, new: Station) {
        val actions = syncStationLogEntries(
            old = old,
            new = new,
            linkedEntry = { type -> old?.let { logs.linkedEntry(it.id, type) } },
            unlinkedEntry = { type -> logs.findUnlinked(new.vehicleId, type, new.date) },
        )
        for (action in actions) {
            when (action) {
                is LogSyncAction.Link -> logs.link(action.entryId, action.stationId)
                is LogSyncAction.Delete -> logs.delete(action.entryId)
                is LogSyncAction.Create -> logs.addLinked(action.vehicleId, action.type, action.date, action.stationId, action.uuid)
            }
        }
    }
}
