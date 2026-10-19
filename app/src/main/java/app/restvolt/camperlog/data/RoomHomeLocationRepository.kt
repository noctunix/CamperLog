package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [HomeLocationRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und
 * Änderung, [newUuid] die Kennung neuer Adressen ohne eigene UUID.
 */
class RoomHomeLocationRepository(
    private val database: CamperLogDatabase,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) : HomeLocationRepository {

    private val dao get() = database.homeLocationDao()

    override fun observeAll(): Flow<List<HomeLocation>> = dao.observeAll().map { rows -> rows.map(HomeLocationEntity::toDomain) }

    override suspend fun locationAt(date: LocalDate): HomeLocation? = dao.findAt(date.toString())?.toDomain()

    override suspend fun save(location: HomeLocation): Long {
        val now = clock()
        return if (location.id == 0L) {
            val uuid = location.uuid.ifEmpty { newUuid() }
            dao.insert(location.copy(uuid = uuid, createdAt = now, updatedAt = now).toEntity())
        } else {
            val withTimestamp = location.copy(updatedAt = now)
            dao.update(withTimestamp.toEntity())
            withTimestamp.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
