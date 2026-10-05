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

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Jüngstes Datum einer Art für [LogDao.observeLatest]. */
data class LatestLogRow(val type: String, @ColumnInfo(name = "date") val date: String)
