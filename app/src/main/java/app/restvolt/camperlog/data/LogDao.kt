package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabelle `log_entries`. */
@Dao
interface LogDao {

    @Query("SELECT type, MAX(date) AS date FROM log_entries WHERE vehicle_id = :vehicleId GROUP BY type")
    fun observeLatest(vehicleId: Long): Flow<List<LatestLogRow>>

    @Query("SELECT * FROM log_entries WHERE vehicle_id = :vehicleId AND type = :type ORDER BY date DESC, id DESC")
    fun observeEntries(vehicleId: Long, type: String): Flow<List<LogEntryEntity>>

    /** Alle Bordbuch-Einträge aller Fahrzeuge für den Sicherungs-Export. */
    @Query("SELECT EXISTS(SELECT 1 FROM log_entries)")
    suspend fun hasAny(): Boolean

    @Query("SELECT * FROM log_entries ORDER BY vehicle_id ASC, date DESC, id DESC")
    suspend fun getAll(): List<LogEntryEntity>

    /** uuids aller gespeicherten Bordbuch-Einträge, für den Abgleich beim Import. */
    @Query("SELECT uuid FROM log_entries")
    suspend fun getUuids(): List<String>

    @Query("SELECT COUNT(*) FROM log_entries WHERE vehicle_id = :vehicleId")
    suspend fun countForVehicle(vehicleId: Long): Int

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Bordbuch-Einträge; die Fahrzeuge bleiben erhalten. */
    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}

/** Jüngstes Datum einer Art für [LogDao.observeLatest]. */
data class LatestLogRow(val type: String, @ColumnInfo(name = "date") val date: String)
