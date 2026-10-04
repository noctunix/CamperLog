package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Lokale Room-Datenbank der App. */
@Database(
    entities = [TourEntity::class, TourCostEntity::class, ExchangeRateEntity::class, SettingsEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        /** Öffnet die Datenbankdatei der App. Nur einmal pro Prozess aufrufen. */
        fun open(context: Context): CamperLogDatabase =
            Room.databaseBuilder(context.applicationContext, CamperLogDatabase::class.java, "camperlog.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

/** Version 3: manuell gepflegte Wechselkurse und die Einstellungstabelle für die Hauptwährung. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
        )
        db.execSQL(
            "CREATE TABLE `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * Version 4: jede Tour bekommt eine eindeutige UUID für Import und Abgleich. Bestehende Touren
 * erhalten eine zufällige UUID (Version 4) direkt in SQL, damit die Migration ohne Kotlin-Schleife auskommt.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tours` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE `tours` SET `uuid` = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)")
    }
}

private const val TOUR_COLUMNS = "`id`, `start_date`, `end_date`, `destination`, `tour_type`, `travel_days`, " +
    "`overnight_stays`, `distance_km`, `pitch_assigned`, `electricity_flat_rate`, `lte_quality`, `pitch_slope`, " +
    "`leveling_blocks_used`, `notes`, `map_link`, `created_at`, `updated_at`"
