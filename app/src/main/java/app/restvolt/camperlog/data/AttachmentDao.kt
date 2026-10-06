package app.restvolt.camperlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room-Zugriff auf die Tabelle `attachments`. */
@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE owner_type = :ownerType AND owner_id = :ownerId ORDER BY id ASC")
    fun observeForOwner(ownerType: String, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE owner_type = :ownerType AND owner_id = :ownerId ORDER BY id ASC")
    suspend fun forOwner(ownerType: String, ownerId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments ORDER BY owner_type ASC, owner_id ASC, id ASC")
    suspend fun getAll(): List<AttachmentEntity>

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE owner_type = :ownerType AND owner_id = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Long)

    @Query("DELETE FROM attachments WHERE owner_type = :ownerType AND owner_id IN (:ownerIds)")
    suspend fun deleteForOwners(ownerType: String, ownerIds: List<Long>)

    /** Löscht alle Anhänge, für den vollständigen Ersatz beim Sicherungs-Import. */
    @Query("DELETE FROM attachments")
    suspend fun deleteAll()

    /** uuid und Dateiname aller gespeicherten Anhänge, für den Abgleich beim Import und den Aufräumlauf. */
    @Query("SELECT uuid, id FROM attachments")
    suspend fun getVersions(): List<AttachmentVersionRow>

    @Query("SELECT file_name FROM attachments")
    suspend fun getAllFileNames(): List<String>
}

/** Stand eines gespeicherten Anhangs für den Import-Abgleich; Anhänge sind unveränderlich, daher reicht die id. */
data class AttachmentVersionRow(val uuid: String, val id: Long)
