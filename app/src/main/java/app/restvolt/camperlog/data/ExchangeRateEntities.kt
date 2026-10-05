package app.restvolt.camperlog.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.restvolt.camperlog.domain.ExchangeRate
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * Kurs je Währung mit Euro-Basis: 1 EUR = [perEuro]. Der Kurs liegt als exakte Dezimalzahl in
 * Textform vor (`11.4850`), damit keine Gleitkomma-Rundung entsteht und die Genauigkeit erhalten bleibt.
 */
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val currency: String,
    @ColumnInfo(name = "per_euro") val perEuro: String,
    @ColumnInfo(name = "rate_date") val rateDate: String,
    val source: String,
)

/** Einstellungen der App als einzelne Zeile mit [id] [SETTINGS_ID]. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SETTINGS_ID,
    @ColumnInfo(name = "main_currency") val mainCurrency: String,
    @ColumnInfo(name = "current_vehicle_id") val currentVehicleId: Long? = null,
)

internal const val SETTINGS_ID = 1

@Dao
interface ExchangeRateDao {

    @Query("SELECT * FROM exchange_rates ORDER BY currency")
    fun observeRates(): Flow<List<ExchangeRateEntity>>

    @Upsert
    suspend fun upsertRate(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates WHERE currency = :currency")
    suspend fun deleteRate(currency: String)

    @Query("SELECT * FROM exchange_rates")
    suspend fun getRates(): List<ExchangeRateEntity>

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAllRates()

    @Query("SELECT main_currency FROM settings WHERE id = $SETTINGS_ID")
    fun observeMainCurrency(): Flow<String?>

    @Query("INSERT OR IGNORE INTO settings (id, main_currency) VALUES ($SETTINGS_ID, 'EUR')")
    suspend fun ensureSettingsRow()

    @Query("UPDATE settings SET main_currency = :currency WHERE id = $SETTINGS_ID")
    suspend fun updateMainCurrency(currency: String)

    /**
     * Setzt die Hauptwährung spaltenweise, ohne das aktuelle Fahrzeug anzutasten. Ein `UPSERT` in
     * SQL-Syntax steht auf API 26 noch nicht zur Verfügung.
     */
    @Transaction
    suspend fun setMainCurrency(currency: String) {
        ensureSettingsRow()
        updateMainCurrency(currency)
    }
}

internal fun ExchangeRateEntity.toDomain() = ExchangeRate(
    currency = Currency.getInstance(currency),
    perEuro = BigDecimal(perEuro),
    date = LocalDate.parse(rateDate),
    source = source,
)

internal fun ExchangeRate.toEntity() = ExchangeRateEntity(
    currency = currency.currencyCode,
    perEuro = perEuro.toPlainString(),
    rateDate = date.toString(),
    source = source,
)
