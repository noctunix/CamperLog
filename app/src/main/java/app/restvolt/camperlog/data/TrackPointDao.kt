package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Anzahl Punkte und Segmente eines Tracks. */
data class TrackSummaryRow(val points: Int, val segments: Int)

/** Room-Zugriff auf die Tabelle `track_points`. */
@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE tour_id = :tourId ORDER BY segment ASC, recorded_at ASC")
    fun observeForTour(tourId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) AS points, COUNT(DISTINCT segment) AS segments FROM track_points WHERE tour_id = :tourId")
    fun observeSummary(tourId: Long): Flow<TrackSummaryRow>

    @Query("SELECT * FROM track_points")
    suspend fun getAll(): List<TrackPointEntity>

    @Query("SELECT COALESCE(MAX(segment), 0) FROM track_points WHERE tour_id = :tourId")
    suspend fun maxSegment(tourId: Long): Int

    /** Doppelte `(tour_id, recorded_at)` werden still übersprungen. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE tour_id = :tourId")
    suspend fun deleteForTour(tourId: Long)
}
