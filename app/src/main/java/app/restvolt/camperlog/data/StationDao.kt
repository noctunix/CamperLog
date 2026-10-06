package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabelle `stations`. */
@Dao
interface StationDao {

    /** Aufsteigend nach `(date, time NULLS LAST, created_at)`, die Zeitleiste einer Tour. */
    @Query("SELECT * FROM stations WHERE tour_id = :tourId ORDER BY date ASC, (time IS NULL) ASC, time ASC, created_at ASC")
    fun observeForTour(tourId: Long): Flow<List<StationEntity>>

    /** Neueste zuerst; `:vehicleId` `NULL` liefert die Stationen aller Fahrzeuge ("Alle Fahrzeuge"). */
    @Query(
        "SELECT * FROM stations WHERE :vehicleId IS NULL OR vehicle_id = :vehicleId " +
            "ORDER BY date DESC, (time IS NULL) ASC, time DESC, created_at DESC",
    )
    fun observeForVehicle(vehicleId: Long?): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :id")
    fun observeById(id: Long): Flow<StationEntity?>

    /** Einmaliger Lesezugriff auf eine Station, für den Vorher-Stand beim Speichern. */
    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: Long): StationEntity?

    @Query("SELECT * FROM stations WHERE tour_id = :tourId")
    suspend fun getForTour(tourId: Long): List<StationEntity>

    @Query("SELECT * FROM stations")
    suspend fun getAll(): List<StationEntity>

    @Insert
    suspend fun insert(station: StationEntity): Long

    @Update
    suspend fun update(station: StationEntity)

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun deleteById(id: Long)

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
        "SELECT id FROM tours WHERE vehicle_id = :vehicleId AND start_date <= :date AND end_date >= :date " +
            "ORDER BY start_date DESC, id DESC LIMIT 1",
    )
    suspend fun defaultTourId(vehicleId: Long, date: String): Long?
}
