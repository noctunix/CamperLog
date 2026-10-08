package app.restvolt.camperlog.data

import androidx.room.withTransaction
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.RunningTourAlreadyExistsException
import app.restvolt.camperlog.domain.TourTotals
import app.restvolt.camperlog.domain.YearTotals
import app.restvolt.camperlog.domain.costsByCategory
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
 * [newUuid] die Kennung neuer Touren ohne eigene UUID. Löst bei neuen Touren mit [Tour.vehicleId] 0
 * das aktuelle Fahrzeug auf. Die Stationskosten (inklusive abgeleiteter Stromkosten) fließen in die
 * Gesamt- und Jahreskennzahlen ein; Stationen ohne Tour zählen dort mit, siehe [observeTotals] und
 * [observeYearTotals].
 *
 * [delete] löscht vor der Tour auch die Anhänge ihrer Stationen: Die Stationen selbst verschwinden
 * beim Löschen der Tour über den Fremdschlüssel (`ON DELETE CASCADE`), ihre Anhänge nicht (siehe KDoc
 * von [RoomVehicleRepository]), daher werden sie hier noch anhand der Stations-ids vorher entfernt.
 */
class RoomTourRepository(
    private val database: CamperLogDatabase,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Instant = Instant::now,
) : TourRepository {

    private val dao get() = database.tourDao()
    private val stationDao get() = database.stationDao()
    private val vehicleDao get() = database.vehicleDao()
    private val attachmentDao get() = database.attachmentDao()

    override fun observeTours(): Flow<List<Tour>> =
        dao.observeAll().map { rows -> rows.map(TourWithCosts::toDomain) }

    override fun observeTour(id: Long): Flow<Tour?> =
        dao.observeById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun allTours(): List<Tour> = dao.getAllAscending().map(TourWithCosts::toDomain)

    override suspend fun hasTours(): Boolean = dao.hasAny()

    override suspend fun save(tour: Tour): Long {
        val now = clock()
        return database.withTransaction {
            val uuid = tour.uuid.ifEmpty { newUuid() }
            val vehicleId = if (tour.vehicleId == 0L) vehicleDao.resolveCurrentVehicleId(now, newUuid) else tour.vehicleId
            if (tour.endDate == null && dao.hasRunningTour(vehicleId, tour.id)) {
                throw RunningTourAlreadyExistsException(vehicleId)
            }
            if (tour.id == 0L) {
                val resolved = tour.copy(uuid = uuid, vehicleId = vehicleId, createdAt = now, updatedAt = now)
                dao.insertWithCosts(resolved.toEntity(), resolved.toCostEntities(), resolved.toCountryEntities())
            } else {
                val resolved = tour.copy(vehicleId = vehicleId, updatedAt = now)
                dao.updateWithCosts(resolved.toEntity(), resolved.toCostEntities(), resolved.toCountryEntities())
                tour.id
            }
        }
    }

    override suspend fun delete(id: Long) = database.withTransaction {
        stationDao.idsForTour(id).takeIf { it.isNotEmpty() }?.let { attachmentDao.deleteForOwners(AttachmentOwnerType.STATION.name, it) }
        dao.deleteById(id)
    }

    override suspend fun restore(tour: Tour) = database.withTransaction {
        if (tour.endDate == null && dao.hasRunningTour(tour.vehicleId)) {
            throw RunningTourAlreadyExistsException(tour.vehicleId)
        }
        dao.insertWithCosts(tour.toEntity(), tour.toCostEntities(), tour.toCountryEntities())
        Unit
    }

    override suspend fun lastUsedCurrency(): Currency? = dao.lastUsedCurrency()?.let(Currency::getInstance)

    override fun observeTotals(vehicleId: Long?): Flow<TourTotals> =
        combine(dao.observeTotals(vehicleId), dao.observeCostSums(vehicleId), stationDao.observeForVehicle(vehicleId)) { totals, sums, stationRows ->
            val stations = stationRows.map(StationWithCosts::toDomain)
            val tourCosts = sums.map(CostSumRow::toDomain)
            val costs = (tourCosts + stations.stationCostTotals()).sumByCurrency()
            totals.toDomain(costs, stations.costsByCategory())
        }

    override fun observeYearTotals(vehicleId: Long?): Flow<List<YearTotals>> =
        combine(dao.observeYearTotals(vehicleId), dao.observeYearCostSums(vehicleId), stationDao.observeForVehicle(vehicleId)) { years, sums, stationRows ->
            val stationsByYear = stationRows.map(StationWithCosts::toDomain).groupBy { it.date.year }
            val tourCostsByYear = sums.groupBy(YearCostSumRow::year) { Money(it.amountMinor, Currency.getInstance(it.currency)) }
            val stationCostsByYear = stationsByYear.mapValues { (_, stations) -> stations.stationCostTotals() }
            val rowsByYear = years.associateBy(YearTotalsRow::year)
            // Ein Jahr ganz ohne Tour (nur Stationen) hat keine Zeile aus dao.observeYearTotals; es zählt trotzdem mit.
            (rowsByYear.keys + stationsByYear.keys).sortedDescending().map { year ->
                val row = rowsByYear[year] ?: YearTotalsRow(year, tours = 0, distanceKm = 0, travelDays = 0, overnightStays = 0)
                val costs = (tourCostsByYear[year].orEmpty() + stationCostsByYear[year].orEmpty()).sumByCurrency()
                row.toDomain(costs, stationsByYear[year].orEmpty().costsByCategory())
            }
        }
}
