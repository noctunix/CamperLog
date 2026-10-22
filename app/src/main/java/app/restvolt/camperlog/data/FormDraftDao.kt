package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FormDraftDao {
    @Query("SELECT payload FROM form_drafts WHERE kind = :kind AND record_id = :recordId")
    suspend fun get(kind: String, recordId: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(draft: FormDraftEntity)

    @Query("DELETE FROM form_drafts WHERE kind = :kind AND record_id = :recordId")
    suspend fun delete(kind: String, recordId: Long)

    @Query("DELETE FROM form_drafts WHERE (kind = 'TOUR' AND record_id = :tourId) OR " +
        "(kind = 'STATION' AND record_id IN (SELECT id FROM stations WHERE tour_id = :tourId))")
    suspend fun deleteForTour(tourId: Long)

    @Query("DELETE FROM form_drafts")
    suspend fun deleteAll()
}
