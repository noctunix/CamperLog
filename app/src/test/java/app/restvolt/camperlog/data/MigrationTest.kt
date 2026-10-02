package app.restvolt.camperlog.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

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

    /** Legt `camperlog.db` im Stand von Version 1 an und füllt sie mit [inserts]. */
    private fun createVersion1(vararg inserts: String) {
        val file = context.getDatabasePath("camperlog.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                    "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                    "`distance_km` INTEGER NOT NULL, `cost_cents` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                    "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                    "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '31867d8464f1071ee996d113f1e1e356')",
            )
            inserts.forEach(db::execSQL)
            db.version = 1
        }
    }
}
