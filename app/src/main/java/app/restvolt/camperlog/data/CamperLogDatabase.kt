package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Lokale Room-Datenbank der App. */
@Database(entities = [TourEntity::class, TourCostEntity::class], version = 2, exportSchema = true)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    companion object {
        /** Öffnet die Datenbankdatei der App. Nur einmal pro Prozess aufrufen. */
        fun open(context: Context): CamperLogDatabase =
            Room.databaseBuilder(context.applicationContext, CamperLogDatabase::class.java, "camperlog.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * Version 2: Kosten wandern aus `tours.cost_cents` in die Tabelle `tour_costs` mit einem Betrag je
 * Währung. Bisherige Kosten ungleich 0 werden als Euro übernommen. SQLite kann ab API 26 keine
 * Spalten löschen, daher wird `tours` ohne `cost_cents` neu aufgebaut.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `tours_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                "`distance_km` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("INSERT INTO `tours_new` SELECT $TOUR_COLUMNS FROM `tours`")
        db.execSQL(
            "CREATE TABLE `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO `tour_costs` (`tour_id`, `currency`, `amount_minor`, `position`) " +
                "SELECT `id`, 'EUR', `cost_cents`, 0 FROM `tours` WHERE `cost_cents` <> 0",
        )
        db.execSQL("DROP TABLE `tours`")
        db.execSQL("ALTER TABLE `tours_new` RENAME TO `tours`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)")
    }
}

private const val TOUR_COLUMNS = "`id`, `start_date`, `end_date`, `destination`, `tour_type`, `travel_days`, " +
    "`overnight_stays`, `distance_km`, `pitch_assigned`, `electricity_flat_rate`, `lte_quality`, `pitch_slope`, " +
    "`leveling_blocks_used`, `notes`, `map_link`, `created_at`, `updated_at`"
