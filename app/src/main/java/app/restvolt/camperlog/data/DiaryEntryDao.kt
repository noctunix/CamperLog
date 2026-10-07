package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabelle `diary_entries`. */
@Dao
interface DiaryEntryDao {

    @Query("SELECT * FROM diary_entries WHERE tour_id = :tourId ORDER BY date ASC, id ASC")
    fun observeForTour(tourId: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries ORDER BY tour_id ASC, date ASC")
    suspend fun getAll(): List<DiaryEntryEntity>

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht alle Einträge, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()

    /** id und Änderungszeit aller Einträge nach UUID, für den Abgleich beim Import. */
    @Query("SELECT uuid, id, updated_at FROM diary_entries")
    suspend fun getVersions(): List<DiaryEntryVersionRow>
}

/** Stand eines gespeicherten Tagebucheintrags für den Import-Abgleich. */
data class DiaryEntryVersionRow(
    val uuid: String,
    val id: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)
