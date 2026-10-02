package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SUMS = """
    COUNT(*) AS tours,
    COALESCE(SUM(distance_km), 0) AS distance_km,
    COALESCE(SUM(travel_days), 0) AS travel_days,
    COALESCE(SUM(overnight_stays), 0) AS overnight_stays
"""

private const val YEAR = "CAST(substr(start_date, 1, 4) AS INTEGER)"

/** Room-Zugriff auf die Tabellen `tours` und `tour_costs`. */
@Dao
interface TourDao {

    @Transaction
    @Query("SELECT * FROM tours ORDER BY start_date DESC, id DESC")
    fun observeAll(): Flow<List<TourWithCosts>>

    @Transaction
    @Query("SELECT * FROM tours WHERE id = :id")
    fun observeById(id: Long): Flow<TourWithCosts?>

    @Transaction
    @Query("SELECT * FROM tours ORDER BY start_date ASC, id ASC")
    suspend fun getAllAscending(): List<TourWithCosts>

    @Insert
    suspend fun insert(tour: TourEntity): Long

    @Update
    suspend fun update(tour: TourEntity)

    @Insert
    suspend fun insertCosts(costs: List<TourCostEntity>)

    @Query("DELETE FROM tour_costs WHERE tour_id = :tourId")
    suspend fun deleteCosts(tourId: Long)

    /** Legt [tour] samt [costs] an; die Kosten erhalten die neue id. */
    @Transaction
    suspend fun insertWithCosts(tour: TourEntity, costs: List<TourCostEntity>): Long {
        val id = insert(tour)
        insertCosts(costs.map { it.copy(tourId = id) })
        return id
    }

    /** Aktualisiert [tour] und ersetzt ihre Kosten vollständig durch [costs]. */
    @Transaction
    suspend fun updateWithCosts(tour: TourEntity, costs: List<TourCostEntity>) {
        update(tour)
        deleteCosts(tour.id)
        insertCosts(costs)
    }

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT c.currency FROM tour_costs c JOIN tours t ON t.id = c.tour_id " +
            "ORDER BY t.updated_at DESC, t.id DESC, c.position DESC LIMIT 1",
    )
    suspend fun lastUsedCurrency(): String?

    @Query("SELECT $SUMS FROM tours")
    fun observeTotals(): Flow<TotalsRow>

    @Query("SELECT currency, SUM(amount_minor) AS amount_minor FROM tour_costs GROUP BY currency ORDER BY currency")
    fun observeCostSums(): Flow<List<CostSumRow>>

    @Query("SELECT $YEAR AS year, $SUMS FROM tours GROUP BY year ORDER BY year DESC")
    fun observeYearTotals(): Flow<List<YearTotalsRow>>

    @Query(
        "SELECT $YEAR AS year, c.currency, SUM(c.amount_minor) AS amount_minor " +
            "FROM tour_costs c JOIN tours t ON t.id = c.tour_id GROUP BY year, c.currency ORDER BY year DESC, c.currency",
    )
    fun observeYearCostSums(): Flow<List<YearCostSumRow>>
}
