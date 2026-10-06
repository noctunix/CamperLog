package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogSyncAction
import app.restvolt.camperlog.domain.SYNCED_SERVICE_LOG_TYPES
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.syncStationLogEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [StationRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Stationen ohne eigene UUID. [logs] ist die Bordbuch-Anbindung, mit der
 * [save] die Ver-/Entsorgungs-Häkchen einer Station nach Abschnitt 4 abgleicht; der Abgleich läuft
 * in derselben Datenbank-Transaktion wie das Speichern der Station.
 */
class RoomStationRepository(
    private val database: CamperLogDatabase,
    private val logs: LogRepository,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : StationRepository {

    private val dao get() = database.stationDao()

    override fun observeForTour(tourId: Long): Flow<List<Station>> =
        dao.observeForTour(tourId).map { rows -> rows.map(StationEntity::toDomain) }

    override fun observeForVehicle(vehicleId: Long?): Flow<List<Station>> =
        dao.observeForVehicle(vehicleId).map { rows -> rows.map(StationEntity::toDomain) }

    override fun observeStation(id: Long): Flow<Station?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun allStations(): List<Station> = dao.getAll().map(StationEntity::toDomain)

    override suspend fun save(station: Station): Long = database.withTransaction {
        val old = if (station.id != 0L) dao.getById(station.id)?.toDomain() else null
        val now = clock()
        val saved = if (station.id == 0L) {
            val uuid = station.uuid.ifEmpty { newUuid() }
            val withTimestamps = station.copy(uuid = uuid, createdAt = now, updatedAt = now)
            withTimestamps.copy(id = dao.insert(withTimestamps.toEntity()))
        } else {
            val withTimestamp = station.copy(updatedAt = now)
            dao.update(withTimestamp.toEntity())
            withTimestamp
        }
        applyLogSync(old, saved)
        saved.id
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(station: Station) {
        dao.insert(station.toEntity())
    }

    override suspend fun defaultTourId(vehicleId: Long, date: LocalDate): Long? = dao.defaultTourId(vehicleId, date.toString())

    override suspend fun linkedLogEntries(stationId: Long): List<LogEntry> =
        SYNCED_SERVICE_LOG_TYPES.values.mapNotNull { type -> logs.linkedEntry(stationId, type) }

    override suspend fun relinkLogEntries(entryIds: List<Long>, stationId: Long) {
        entryIds.forEach { entryId -> logs.link(entryId, stationId) }
    }

    override suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long) = database.withTransaction {
        dao.getForTour(tourId).map { it.toDomain() }.filter { it.vehicleId != vehicleId }
            .forEach { save(it.copy(vehicleId = vehicleId)) }
    }

    /** Wendet die Bordbuch-Angleichung aus [syncStationLogEntries] auf [new] an (4). */
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
