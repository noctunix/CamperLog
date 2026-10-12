package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** [HomeLocationRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung. */
class RoomHomeLocationRepository(
    private val database: CamperLogDatabase,
    private val clock: () -> Instant = Instant::now,
) : HomeLocationRepository {

    private val dao get() = database.homeLocationDao()

    override fun observeAll(): Flow<List<HomeLocation>> = dao.observeAll().map { rows -> rows.map(HomeLocationEntity::toDomain) }

    override suspend fun locationAt(date: LocalDate): HomeLocation? = dao.findAt(date.toString())?.toDomain()

    override suspend fun save(location: HomeLocation): Long {
        val now = clock()
        return if (location.id == 0L) {
            dao.insert(location.copy(createdAt = now, updatedAt = now).toEntity())
        } else {
            val withTimestamp = location.copy(updatedAt = now)
            dao.update(withTimestamp.toEntity())
            withTimestamp.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
