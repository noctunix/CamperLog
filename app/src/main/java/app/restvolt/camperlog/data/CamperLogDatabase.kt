package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Lokale Room-Datenbank der App. */
@Database(
    entities = [
        TourEntity::class,
        TourCostEntity::class,
        ExchangeRateEntity::class,
        SettingsEntity::class,
        VehicleEntity::class,
        RepairEntity::class,
        LogEntryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun vehicleDao(): VehicleDao

    abstract fun logDao(): LogDao

    companion object {
        /** Öffnet die Datenbankdatei der App. Nur einmal pro Prozess aufrufen. */
        fun open(context: Context): CamperLogDatabase =
            Room.databaseBuilder(context.applicationContext, CamperLogDatabase::class.java, "camperlog.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

/**
 * Version 5: Fahrzeuge, Reparaturen und das Bordbuch kommen hinzu. Jede Tour bekommt ein Fahrzeug
 * (Fremdschlüssel `ON DELETE RESTRICT`, damit ein Fahrzeug mit Touren nicht gelöscht werden kann);
 * bestehende Touren bekommen dafür ein einzelnes, neu angelegtes Fahrzeug mit leerem Namen.
 * SQLite kann ab API 26 keine Fremdschlüssel nachträglich hinzufügen, daher wird `tours` wie schon
 * in [MIGRATION_1_2] neu aufgebaut.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, " +
                "`manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, " +
                "`first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, " +
                "`purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, " +
                "`sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, " +
                "`insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, " +
                "`insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, " +
                "`vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, " +
                "`length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, " +
                "`power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, " +
                "`tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, " +
                "`fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, " +
                "`cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, " +
                "`next_inspection_date` TEXT, `next_gas_check_date` TEXT, `last_oil_change_date` TEXT, " +
                "`last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)")

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `vehicles` (`uuid`, `name`, `license_plate`, `manufacturer`, `model`, `vin`, `notes`, " +
                "`insurer`, `insurance_policy_number`, `tire_size`, `created_at`, `updated_at`) VALUES (" +
                "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))), " +
                "'', '', '', '', '', '', '', '', '', $now, $now)",
        )

        db.execSQL(
            "CREATE TABLE `tours_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, " +
                "`tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, " +
                "`distance_km` INTEGER NOT NULL, `pitch_assigned` INTEGER NOT NULL, " +
                "`electricity_flat_rate` TEXT NOT NULL, `lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, " +
                "`leveling_blocks_used` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `tours_new` SELECT `id`, `uuid`, (SELECT `id` FROM `vehicles` LIMIT 1), `start_date`, " +
                "`end_date`, `destination`, `tour_type`, `travel_days`, `overnight_stays`, `distance_km`, " +
                "`pitch_assigned`, `electricity_flat_rate`, `lte_quality`, `pitch_slope`, `leveling_blocks_used`, " +
                "`notes`, `map_link`, `created_at`, `updated_at` FROM `tours`",
        )
        db.execSQL("DROP TABLE `tours`")
        db.execSQL("ALTER TABLE `tours_new` RENAME TO `tours`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)")

        db.execSQL("ALTER TABLE `settings` ADD COLUMN `current_vehicle_id` INTEGER")
    }
}

/**
 * Version 6: Pannenhilfe, Schutzbrief und Schadenhotline sowie das gewogene Leergewicht kommen zu
 * den Fahrzeugen hinzu. Reine Textspalten und ein zusätzliches Gewicht brauchen keinen Fremdschlüssel,
 * daher reicht `ALTER TABLE ADD COLUMN` ohne Neuaufbau der Tabelle.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `measured_empty_weight_kg` INTEGER")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `breakdown_provider` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `breakdown_membership_number` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `breakdown_phone` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `travel_protection_provider` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `travel_protection_contract_number` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `travel_protection_phone` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `insurer_claims_phone` TEXT NOT NULL DEFAULT ''")
    }
}
