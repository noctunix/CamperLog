package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabellen `stations` und `station_costs`. */
@Dao
interface StationDao {

    /** Aufsteigend nach `(date, time NULLS LAST, created_at)`, die Zeitleiste einer Tour. */
    @Transaction
    @Query("SELECT * FROM stations WHERE tour_id = :tourId ORDER BY date ASC, (time IS NULL) ASC, time ASC, created_at ASC")
    fun observeForTour(tourId: Long): Flow<List<StationWithCosts>>

    /** Neueste zuerst; `:vehicleId` `NULL` liefert die Stationen aller Fahrzeuge ("Alle Fahrzeuge"). */
    @Transaction
    @Query(
        "SELECT * FROM stations WHERE :vehicleId IS NULL OR vehicle_id = :vehicleId " +
            "ORDER BY date DESC, (time IS NULL) ASC, time DESC, created_at DESC",
    )
    fun observeForVehicle(vehicleId: Long?): Flow<List<StationWithCosts>>

    @Transaction
    @Query("SELECT * FROM stations WHERE id = :id")
    fun observeById(id: Long): Flow<StationWithCosts?>

    /** Einmaliger Lesezugriff auf eine Station, für den Vorher-Stand beim Speichern. */
    @Transaction
    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: Long): StationWithCosts?

    @Transaction
    @Query("SELECT * FROM stations WHERE tour_id = :tourId")
    suspend fun getForTour(tourId: Long): List<StationWithCosts>

    @Transaction
    @Query("SELECT * FROM stations")
    suspend fun getAll(): List<StationWithCosts>

    @Insert
    suspend fun insert(station: StationEntity): Long

    @Update
    suspend fun update(station: StationEntity)

    @Insert
    suspend fun insertCosts(costs: List<StationCostEntity>)

    @Query("DELETE FROM station_costs WHERE station_id = :stationId")
    suspend fun deleteCosts(stationId: Long)

    /** Legt [station] samt [costs] an; die Kosten erhalten die neue id. */
    @Transaction
    suspend fun insertWithCosts(station: StationEntity, costs: List<StationCostEntity>): Long {
        val id = insert(station)
        insertCosts(costs.map { it.copy(stationId = id) })
        return id
    }

    /** Aktualisiert [station] und ersetzt ihre Kosten vollständig durch [costs]. */
    @Transaction
    suspend fun updateWithCosts(station: StationEntity, costs: List<StationCostEntity>) {
        update(station)
        deleteCosts(station.id)
        insertCosts(costs)
    }

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** ids aller Stationen einer Tour, um vor ihrem kaskadierenden Löschen über die Tour ihre Anhänge zu entfernen. */
    @Query("SELECT id FROM stations WHERE tour_id = :tourId")
    suspend fun idsForTour(tourId: Long): List<Long>

    /** Löscht alle Stationen, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM stations")
    suspend fun deleteAll()

    /** id und Änderungszeit aller Stationen nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM stations")
    suspend fun getVersions(): List<StationVersionRow>

    /**
     * Die Tour von [vehicleId], deren Zeitraum [date] enthält, bei mehreren Treffern die mit dem
     * spätesten Start; `NULL`, wenn keine passt.
     */
    @Query(
        "SELECT id FROM tours WHERE vehicle_id = :vehicleId AND start_date <= :date " +
            "AND (end_date IS NULL OR end_date >= :date) " +
            "ORDER BY start_date DESC, id DESC LIMIT 1",
    )
    suspend fun defaultTourId(vehicleId: Long, date: String): Long?
}
