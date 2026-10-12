package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Datenbankzeile einer Heimatadresse. [validFrom]/[validTo] sind ISO-Texte (`yyyy-MM-dd`) oder
 * `NULL` für "schon immer" bzw. "bis auf Weiteres".
 */
@Entity(tableName = "home_locations")
data class HomeLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "coordinate_source") val coordinateSource: String,
    @ColumnInfo(name = "valid_from") val validFrom: String?,
    @ColumnInfo(name = "valid_to") val validTo: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Room-Zugriff auf `home_locations`. */
@Dao
interface HomeLocationDao {
    @Query("SELECT * FROM home_locations ORDER BY valid_from IS NULL DESC, valid_from DESC")
    fun observeAll(): Flow<List<HomeLocationEntity>>

    /** Die am [date] (ISO-Text) gültige Adresse, oder `null`, wenn keine passt. */
    @Query(
        "SELECT * FROM home_locations WHERE (valid_from IS NULL OR valid_from <= :date) " +
            "AND (valid_to IS NULL OR valid_to >= :date) ORDER BY valid_from IS NULL ASC, valid_from DESC LIMIT 1",
    )
    suspend fun findAt(date: String): HomeLocationEntity?

    @Insert
    suspend fun insert(entity: HomeLocationEntity): Long

    @Update
    suspend fun update(entity: HomeLocationEntity)

    @Query("DELETE FROM home_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
