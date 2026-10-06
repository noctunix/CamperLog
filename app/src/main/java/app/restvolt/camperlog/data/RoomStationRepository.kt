package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [StationRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Stationen ohne eigene UUID.
 */
class RoomStationRepository(
    private val dao: StationDao,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : StationRepository {

    override fun observeForTour(tourId: Long): Flow<List<Station>> =
        dao.observeForTour(tourId).map { rows -> rows.map(StationEntity::toDomain) }

    override fun observeForVehicle(vehicleId: Long?): Flow<List<Station>> =
        dao.observeForVehicle(vehicleId).map { rows -> rows.map(StationEntity::toDomain) }

    override fun observeStation(id: Long): Flow<Station?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun allStations(): List<Station> = dao.getAll().map(StationEntity::toDomain)

    override suspend fun save(station: Station): Long {
        val now = clock()
        return if (station.id == 0L) {
            val uuid = station.uuid.ifEmpty { newUuid() }
            dao.insert(station.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.update(station.copy(updatedAt = now).toEntity())
            station.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(station: Station) {
        dao.insert(station.toEntity())
    }

    override suspend fun defaultTourId(vehicleId: Long, date: LocalDate): Long? = dao.defaultTourId(vehicleId, date.toString())
}
