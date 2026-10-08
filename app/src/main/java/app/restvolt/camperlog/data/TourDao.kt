package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
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

    @Query("SELECT EXISTS(SELECT 1 FROM tours)")
    suspend fun hasAny(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM tours WHERE vehicle_id = :vehicleId AND end_date IS NULL AND id != :excludedTourId)")
    suspend fun hasRunningTour(vehicleId: Long, excludedTourId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM tours WHERE vehicle_id = :vehicleId AND end_date IS NULL)")
    suspend fun hasRunningTour(vehicleId: Long): Boolean

    @Insert
    suspend fun insert(tour: TourEntity): Long

    @Update
    suspend fun update(tour: TourEntity)

    @Insert
    suspend fun insertCosts(costs: List<TourCostEntity>)

    @Query("DELETE FROM tour_costs WHERE tour_id = :tourId")
    suspend fun deleteCosts(tourId: Long)

    @Insert
    suspend fun insertCountries(countries: List<TourCountryEntity>)

    @Query("DELETE FROM tour_countries WHERE tour_id = :tourId")
    suspend fun deleteCountries(tourId: Long)

    /** Legt [tour] samt [costs] und manuellen Länderanpassungen ([countries]) an; beide erhalten die neue id. */
    @Transaction
    suspend fun insertWithCosts(tour: TourEntity, costs: List<TourCostEntity>, countries: List<TourCountryEntity> = emptyList()): Long {
        val id = insert(tour)
        insertCosts(costs.map { it.copy(tourId = id) })
        insertCountries(countries.map { it.copy(tourId = id) })
        return id
    }

    /** Aktualisiert [tour] und ersetzt ihre Kosten und manuellen Länderanpassungen vollständig durch [costs]/[countries]. */
    @Transaction
    suspend fun updateWithCosts(tour: TourEntity, costs: List<TourCostEntity>, countries: List<TourCountryEntity> = emptyList()) {
        update(tour)
        deleteCosts(tour.id)
        insertCosts(costs)
        deleteCountries(tour.id)
        insertCountries(countries)
    }

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** id und Änderungszeit aller Touren nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM tours")
    suspend fun getVersions(): List<TourVersionRow>

    /** Löscht alle Touren; die Kosten folgen über den Fremdschlüssel. */
    @Query("DELETE FROM tours")
    suspend fun deleteAll()

    @Query(
        "SELECT c.currency FROM tour_costs c JOIN tours t ON t.id = c.tour_id " +
            "ORDER BY t.updated_at DESC, t.id DESC, c.position DESC LIMIT 1",
    )
    suspend fun lastUsedCurrency(): String?

    @Query("SELECT $SUMS FROM tours WHERE :vehicleId IS NULL OR vehicle_id = :vehicleId")
    fun observeTotals(vehicleId: Long?): Flow<TotalsRow>

    @Query(
        "SELECT c.currency, SUM(c.amount_minor) AS amount_minor FROM tour_costs c " +
            "JOIN tours t ON t.id = c.tour_id WHERE :vehicleId IS NULL OR t.vehicle_id = :vehicleId " +
            "GROUP BY c.currency ORDER BY c.currency",
    )
    fun observeCostSums(vehicleId: Long?): Flow<List<CostSumRow>>

    /** Wie [observeCostSums], aber einmalig über alle Fahrzeuge; SQLite wirft bei einem 64-Bit-Überlauf der Summe. */
    @Query("SELECT currency, SUM(amount_minor) AS amount_minor FROM tour_costs GROUP BY currency ORDER BY currency")
    suspend fun getCostSums(): List<CostSumRow>

    @Query("SELECT $YEAR AS year, $SUMS FROM tours WHERE :vehicleId IS NULL OR vehicle_id = :vehicleId GROUP BY year ORDER BY year DESC")
    fun observeYearTotals(vehicleId: Long?): Flow<List<YearTotalsRow>>

    @Query(
        "SELECT $YEAR AS year, c.currency, SUM(c.amount_minor) AS amount_minor " +
            "FROM tour_costs c JOIN tours t ON t.id = c.tour_id " +
            "WHERE :vehicleId IS NULL OR t.vehicle_id = :vehicleId GROUP BY year, c.currency ORDER BY year DESC, c.currency",
    )
    fun observeYearCostSums(vehicleId: Long?): Flow<List<YearCostSumRow>>
}

/** Stand einer gespeicherten Tour für den Import-Abgleich. */
data class TourVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
