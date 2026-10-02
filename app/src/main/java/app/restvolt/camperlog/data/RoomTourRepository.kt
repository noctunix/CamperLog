package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.YearTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [TourRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung. */
class RoomTourRepository(
    private val dao: TourDao,
    private val clock: () -> Instant = Instant::now,
) : TourRepository {

    override fun observeTours(): Flow<List<Tour>> =
        dao.observeAll().map { rows -> rows.map(TourEntity::toDomain) }

    override fun observeTour(id: Long): Flow<Tour?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun allTours(): List<Tour> = dao.getAllAscending().map(TourEntity::toDomain)

    override suspend fun save(tour: Tour): Long {
        val now = clock()
        return if (tour.id == 0L) {
            dao.insert(tour.copy(createdAt = now, updatedAt = now).toEntity())
        } else {
            dao.update(tour.copy(updatedAt = now).toEntity())
            tour.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(tour: Tour) {
        dao.insert(tour.toEntity())
    }

    override fun observeTotals(): Flow<TourTotals> = dao.observeTotals().map(TotalsRow::toDomain)

    override fun observeYearTotals(): Flow<List<YearTotals>> =
        dao.observeYearTotals().map { rows -> rows.map(YearTotalsRow::toDomain) }
}
