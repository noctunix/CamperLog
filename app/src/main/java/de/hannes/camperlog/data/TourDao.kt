package de.hannes.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SUMS = """
    COUNT(*) AS tours,
    COALESCE(SUM(distance_km), 0) AS distance_km,
    COALESCE(SUM(travel_days), 0) AS travel_days,
    COALESCE(SUM(overnight_stays), 0) AS overnight_stays,
    COALESCE(SUM(cost_cents), 0) AS cost_cents
"""

/** Room-Zugriff auf die Tabelle `tours`. */
@Dao
interface TourDao {

    @Query("SELECT * FROM tours ORDER BY start_date DESC, id DESC")
    fun observeAll(): Flow<List<TourEntity>>

    @Query("SELECT * FROM tours WHERE id = :id")
    fun observeById(id: Long): Flow<TourEntity?>

    @Query("SELECT * FROM tours ORDER BY start_date ASC, id ASC")
    suspend fun getAllAscending(): List<TourEntity>

    @Insert
    suspend fun insert(tour: TourEntity): Long

    @Update
    suspend fun update(tour: TourEntity)

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT $SUMS FROM tours")
    fun observeTotals(): Flow<TotalsRow>

    @Query(
        "SELECT CAST(substr(start_date, 1, 4) AS INTEGER) AS year, $SUMS " +
            "FROM tours GROUP BY year ORDER BY year DESC",
    )
    fun observeYearTotals(): Flow<List<YearTotalsRow>>
}
