package app.restvolt.camperlog.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

/**
 * Prüft die Migration auf Version 2 mit einer Datenbank, die exakt nach `schemas/…/1.json` angelegt
 * wird. Room validiert beim Öffnen zusätzlich, dass das Ergebnis dem Schema von Version 2 entspricht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migration1To2MovesCostsToEuroRowsAndKeepsTours() = runTest {
        createVersion1(
            "INSERT INTO tours VALUES (1, '2025-07-01', '2025-07-03', 'Gardasee', 'WEEKEND', 3, 2, 840, 8950, 1, " +
                "'YES', 'GOOD', 'LEVEL', 0, 'Notiz', 'https://example.org', 1000, 2000)",
            "INSERT INTO tours VALUES (2, '2026-04-10', '2026-04-10', 'Ostsee', 'DAY_TRIP', 1, 0, 120, 0, 0, " +
                "'NO', 'OK', 'SLOPED', 1, '', NULL, 3000, 4000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tours = RoomTourRepository(db.tourDao()) { Instant.EPOCH }.allTours()

            assertEquals(listOf(1L, 2L), tours.map { it.id })
            assertEquals(listOf(Money(8_950, EUR)), tours[0].costs)
            assertEquals(emptyList<Money>(), tours[1].costs)
            assertEquals("Gardasee", tours[0].destination)
            assertEquals(840, tours[0].distanceKm)
            assertEquals("https://example.org", tours[0].mapLink)
            assertEquals(Instant.ofEpochMilli(2000), tours[0].updatedAt)

            // Der Fremdschlüssel greift auch nach dem Neuaufbau der Tabelle `tours`.
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tour_costs").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migration2To3AddsEmptyRatesAndKeepsCosts() = runTest {
        createVersion2(
            "INSERT INTO tours VALUES (1, '2026-06-01', '2026-06-14', 'Lofoten', 'VACATION', 14, 13, 4200, 1, " +
                "'YES', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
            "INSERT INTO tour_costs VALUES (1, 'NOK', 1250000, 0)",
            "INSERT INTO tour_costs VALUES (1, 'EUR', 4500, 1)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tours = RoomTourRepository(db.tourDao()) { Instant.EPOCH }.allTours()
            val rates = RoomExchangeRateRepository(db.exchangeRateDao())

            assertEquals(listOf(Money(1_250_000, Currency.getInstance("NOK")), Money(4_500, EUR)), tours.single().costs)
            assertEquals(emptyList<ExchangeRate>(), rates.observeRates().first())
            assertEquals(EUR, rates.observeMainCurrency().first())

            val nok = ExchangeRate(Currency.getInstance("NOK"), BigDecimal("11.4850"), LocalDate.of(2026, 10, 1), "EZB")
            rates.saveRate(nok)
            rates.setMainCurrency(Currency.getInstance("NOK"))
            assertEquals(listOf(nok), rates.observeRates().first())
            assertEquals(Currency.getInstance("NOK"), rates.observeMainCurrency().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun migration3To4GivesEveryTourADistinctUuidAndKeepsData() = runTest {
        createVersion3(
            "INSERT INTO tours VALUES (1, '2026-06-01', '2026-06-14', 'Lofoten', 'VACATION', 14, 13, 4200, 1, " +
                "'YES', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
            "INSERT INTO tours VALUES (2, '2026-07-01', '2026-07-01', 'Ostsee', 'DAY_TRIP', 1, 0, 120, 0, " +
                "'NO', 'OK', 'SLOPED', 1, '', NULL, 3000, 4000)",
            "INSERT INTO tour_costs VALUES (1, 'NOK', 1250000, 0)",
            "INSERT INTO exchange_rates VALUES ('NOK', '11.4850', '2026-10-01', 'EZB')",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tours = RoomTourRepository(db.tourDao()) { Instant.EPOCH }.allTours()

            assertEquals(listOf(1L, 2L), tours.map { it.id })
            assertEquals(listOf(Money(1_250_000, Currency.getInstance("NOK"))), tours[0].costs)
            tours.forEach { assertEquals(4, UUID.fromString(it.uuid).version()) }
            assertEquals(2, tours.map { it.uuid }.distinct().size)
            assertEquals(1, RoomExchangeRateRepository(db.exchangeRateDao()).observeRates().first().size)
        } finally {
            db.close()
        }
    }

    /** Legt `camperlog.db` im Stand von Version 3 nach `schemas/…/3.json` an und füllt sie mit [inserts]. */
    private fun createVersion3(vararg inserts: String) = createDatabase(
        version = 3,
        identityHash = "555e3d29145874d0c0f6c05a51c649ba",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                "`distance_km` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 2 nach `schemas/…/2.json` an und füllt sie mit [inserts]. */
    private fun createVersion2(vararg inserts: String) = createDatabase(
        version = 2,
        identityHash = "0fbe0e06f94a429cb1f6be32b5b70d9a",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                "`distance_km` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 1 an und füllt sie mit [inserts]. */
    private fun createVersion1(vararg inserts: String) = createDatabase(
        version = 1,
        identityHash = "31867d8464f1071ee996d113f1e1e356",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                "`distance_km` INTEGER NOT NULL, `cost_cents` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` mit [schema] und dem Room-[identityHash] von [version] an. */
    private fun createDatabase(version: Int, identityHash: String, schema: List<String>, inserts: List<String>) {
        val file = context.getDatabasePath("camperlog.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.forEach(db::execSQL)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identityHash')")
            inserts.forEach(db::execSQL)
            db.version = version
        }
    }
}
