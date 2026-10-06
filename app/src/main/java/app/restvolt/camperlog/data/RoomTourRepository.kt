package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.YearTotals
import app.restvolt.camperlog.domain.stationCostTotals
import app.restvolt.camperlog.domain.sumByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * [TourRepository] auf Basis von Room. [clock] liefert die Zeitstempel für Anlage und Änderung,
 * [newUuid] die Kennung neuer Touren ohne eigene UUID. [vehicleDao] löst bei neuen Touren mit
 * [Tour.vehicleId] 0 das aktuelle Fahrzeug auf. [stationDao] liefert die Stationskosten (inklusive
 * abgeleiteter Stromkosten), die in die Gesamt- und Jahreskennzahlen einfließen; Stationen ohne Tour
 * zählen dort mit, siehe [observeTotals] und [observeYearTotals].
 */
class RoomTourRepository(
    private val dao: TourDao,
    private val stationDao: StationDao,
    private val vehicleDao: VehicleDao,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : TourRepository {

    override fun observeTours(): Flow<List<Tour>> =
        dao.observeAll().map { rows -> rows.map(TourWithCosts::toDomain) }

    override fun observeTour(id: Long): Flow<Tour?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun allTours(): List<Tour> = dao.getAllAscending().map(TourWithCosts::toDomain)

    override suspend fun hasTours(): Boolean = dao.hasAny()

    override suspend fun save(tour: Tour): Long {
        val now = clock()
        return if (tour.id == 0L) {
            val uuid = tour.uuid.ifEmpty { newUuid() }
            val vehicleId = if (tour.vehicleId == 0L) vehicleDao.resolveCurrentVehicleId(now, newUuid) else tour.vehicleId
            dao.insertWithCosts(
                tour.copy(uuid = uuid, vehicleId = vehicleId, createdAt = now, updatedAt = now).toEntity(),
                tour.toCostEntities(),
            )
        } else {
            dao.updateWithCosts(tour.copy(updatedAt = now).toEntity(), tour.toCostEntities())
            tour.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun restore(tour: Tour) {
        dao.insertWithCosts(tour.toEntity(), tour.toCostEntities())
    }

    override suspend fun lastUsedCurrency(): Currency? = dao.lastUsedCurrency()?.let(Currency::getInstance)

    override fun observeTotals(vehicleId: Long?): Flow<TourTotals> =
        combine(dao.observeTotals(vehicleId), dao.observeCostSums(vehicleId), stationDao.observeForVehicle(vehicleId)) { totals, sums, stations ->
            val tourCosts = sums.map(CostSumRow::toDomain)
            val costs = (tourCosts + stations.map(StationWithCosts::toDomain).stationCostTotals()).sumByCurrency()
            totals.toDomain(costs)
        }

    override fun observeYearTotals(vehicleId: Long?): Flow<List<YearTotals>> =
        combine(dao.observeYearTotals(vehicleId), dao.observeYearCostSums(vehicleId), stationDao.observeForVehicle(vehicleId)) { years, sums, stationRows ->
            val tourCostsByYear = sums.groupBy(YearCostSumRow::year) { Money(it.amountMinor, Currency.getInstance(it.currency)) }
            val stationCostsByYear = stationRows.map(StationWithCosts::toDomain).groupBy { it.date.year }
                .mapValues { (_, stations) -> stations.stationCostTotals() }
            val rowsByYear = years.associateBy(YearTotalsRow::year)
            // Ein Jahr ganz ohne Tour (nur Stationen) hat keine Zeile aus dao.observeYearTotals; es zählt trotzdem mit.
            (rowsByYear.keys + stationCostsByYear.keys).sortedDescending().map { year ->
                val row = rowsByYear[year] ?: YearTotalsRow(year, tours = 0, distanceKm = 0, travelDays = 0, overnightStays = 0)
                val costs = (tourCostsByYear[year].orEmpty() + stationCostsByYear[year].orEmpty()).sumByCurrency()
                row.toDomain(costs)
            }
        }
}
