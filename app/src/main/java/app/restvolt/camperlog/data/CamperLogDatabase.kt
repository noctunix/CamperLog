package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LegacyPitchAttribute
import app.restvolt.camperlog.domain.LegacyPitchFields
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.migrateLegacyPitch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
        StationEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun vehicleDao(): VehicleDao

    abstract fun logDao(): LogDao

    abstract fun stationDao(): StationDao

    companion object {
        /**
         * Öffnet die Datenbankdatei der App. Nur einmal pro Prozess aufrufen.
         *
         * @param context liefert Dateipfad und die Texte, die Migrationen in Notizen schreiben
         * @param onToursMigrated wird aufgerufen, wenn das Update auf Version 7 bestehende Touren umgebaut hat
         * @return die geöffnete Datenbank
         */
        fun open(context: Context, onToursMigrated: () -> Unit = {}): CamperLogDatabase =
            Room.databaseBuilder(context.applicationContext, CamperLogDatabase::class.java, "camperlog.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, migration6To7(context, onToursMigrated),
                    MIGRATION_7_8,
                )
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

/**
 * Version 7: Stationen kommen hinzu (`stations`, Fremdschlüssel `vehicle_id` RESTRICT, `tour_id`
 * CASCADE). Jede Tour mit `overnight_stays > 0` bekommt dafür genau eine Übernachtungs-Station mit
 * den unverändert übernommenen alten Stellplatz-Werten (3.4); bei Tagestrips (`overnight_stays = 0`)
 * mit davon abweichenden Werten bleibt stattdessen eine Notiz-Zeile, gebaut aus den Strings von
 * [context] in der Gerätesprache. `tours` wird danach ohne die fünf Stellplatz-Spalten neu
 * aufgebaut, wie schon in [MIGRATION_1_2]; `log_entries` bleibt in dieser Phase unverändert.
 */
internal fun migration6To7(context: Context, onToursMigrated: () -> Unit = {}): Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, " +
                "`longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, " +
                "`notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, " +
                "`electricity_flat_rate` TEXT, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, " +
                "`weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, " +
                "`weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)")

        val hadTours = db.query("SELECT EXISTS(SELECT 1 FROM `tours`)").use { it.moveToFirst() && it.getInt(0) == 1 }
        insertOvernightStations(db)
        appendDayTripNotes(db, context)

        db.execSQL(
            "CREATE TABLE `tours_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `tours_new` SELECT `id`, `uuid`, `vehicle_id`, `start_date`, `end_date`, `destination`, " +
                "`tour_type`, `travel_days`, `overnight_stays`, `distance_km`, `notes`, `map_link`, `created_at`, " +
                "`updated_at` FROM `tours`",
        )
        db.execSQL("DROP TABLE `tours`")
        db.execSQL("ALTER TABLE `tours_new` RENAME TO `tours`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 6→7" }
        }
        if (hadTours) onToursMigrated()
    }

    /** Legt für jede Tour mit mindestens einer Übernachtung die Übernachtungs-Station an (3.4, Punkt 2). */
    private fun insertOvernightStations(db: SupportSQLiteDatabase) {
        db.query(
            "SELECT `id`, `vehicle_id`, `start_date`, `destination`, `overnight_stays`, `pitch_assigned`, " +
                "`electricity_flat_rate`, `lte_quality`, `pitch_slope`, `leveling_blocks_used`, `created_at`, " +
                "`updated_at` FROM `tours` WHERE `overnight_stays` > 0",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val tourId = cursor.getLong(0)
                val vehicleId = cursor.getLong(1)
                val startDate = LocalDate.parse(cursor.getString(2))
                val destination = cursor.getString(3)
                val overnightStays = cursor.getInt(4)
                val pitch = cursor.toLegacyPitchFields(startIndex = 5)
                val createdAt = Instant.ofEpochMilli(cursor.getLong(10))
                val updatedAt = Instant.ofEpochMilli(cursor.getLong(11))
                val migration = migrateLegacyPitch(
                    uuid = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    tourId = tourId,
                    startDate = startDate,
                    destination = destination,
                    overnightStays = overnightStays,
                    pitch = pitch,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
                val station = checkNotNull(migration.overnightStation)
                db.execSQL(
                    "INSERT INTO `stations` (`uuid`, `vehicle_id`, `tour_id`, `type`, `date`, `time`, `name`, " +
                        "`place`, `latitude`, `longitude`, `coordinate_source`, `accuracy_m`, `map_link`, `notes`, " +
                        "`nights`, `site_kind`, `pitch_assigned`, `electricity_flat_rate`, `lte_quality`, " +
                        "`pitch_slope`, `leveling_blocks_used`, `services`, `weather_temperature_deci_c`, " +
                        "`weather_code`, `weather_wind_kmh`, `weather_gust_kmh`, `weather_wind_direction_deg`, " +
                        "`weather_observed_at`, `favorite`, `created_at`, `updated_at`) VALUES " +
                        "(?, ?, ?, ?, ?, NULL, ?, '', NULL, NULL, NULL, NULL, NULL, '', ?, NULL, ?, ?, ?, ?, ?, '', " +
                        "NULL, NULL, NULL, NULL, NULL, NULL, 0, ?, ?)",
                    arrayOf<Any?>(
                        station.uuid, station.vehicleId, station.tourId, station.type.name, station.date.toString(),
                        station.name, station.nights, station.pitchAssigned?.toSqlInt(), station.electricityFlatRate?.name,
                        station.lteQuality?.name, station.pitchSlope?.name, station.levelingBlocksUsed?.toSqlInt(),
                        station.createdAt.toEpochMilli(), station.updatedAt.toEpochMilli(),
                    ),
                )
            }
        }
    }

    /** Hängt Tagestrips mit von den alten Vorgaben abweichenden Stellplatz-Werten eine Notiz-Zeile an (3.4, Punkt 3). */
    private fun appendDayTripNotes(db: SupportSQLiteDatabase, context: Context) {
        db.query(
            "SELECT `id`, `pitch_assigned`, `electricity_flat_rate`, `lte_quality`, `pitch_slope`, " +
                "`leveling_blocks_used`, `notes` FROM `tours` WHERE `overnight_stays` = 0",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val tourId = cursor.getLong(0)
                val pitch = cursor.toLegacyPitchFields(startIndex = 1)
                val oldNotes = cursor.getString(6)
                val migration = migrateLegacyPitch(
                    uuid = "",
                    vehicleId = 0,
                    tourId = null,
                    // Ungenutzt: Mit overnightStays = 0 baut migrateLegacyPitch keine Station daraus.
                    startDate = LocalDate.of(1970, 1, 1),
                    destination = "",
                    overnightStays = 0,
                    pitch = pitch,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                )
                if (migration.dayTripAttributes.isEmpty()) continue
                val fragments = migration.dayTripAttributes.joinToString(", ") { context.pitchNoteFragment(it, pitch) }
                val line = context.getString(R.string.migration_pitch_note, fragments)
                val newNotes = if (oldNotes.isBlank()) line else "$oldNotes\n$line"
                db.execSQL("UPDATE `tours` SET `notes` = ? WHERE `id` = ?", arrayOf<Any?>(newNotes, tourId))
            }
        }
    }
}

/** Liest die fünf alten Stellplatz-Spalten ab Spaltenindex [startIndex] (in Deklarationsreihenfolge). */
private fun android.database.Cursor.toLegacyPitchFields(startIndex: Int): LegacyPitchFields = LegacyPitchFields(
    pitchAssigned = getInt(startIndex) != 0,
    electricityFlatRate = ElectricityFlatRate.valueOf(getString(startIndex + 1)),
    lteQuality = LteQuality.valueOf(getString(startIndex + 2)),
    pitchSlope = PitchSlope.valueOf(getString(startIndex + 3)),
    levelingBlocksUsed = getInt(startIndex + 4) != 0,
)

private fun Boolean.toSqlInt(): Int = if (this) 1 else 0

/** Kurzer, lokalisierter Textbaustein für ein von den alten Vorgaben abweichendes Stellplatz-Attribut. */
private fun Context.pitchNoteFragment(attribute: LegacyPitchAttribute, pitch: LegacyPitchFields): String = when (attribute) {
    LegacyPitchAttribute.PITCH_ASSIGNED -> getString(R.string.migration_pitch_note_assigned)
    LegacyPitchAttribute.ELECTRICITY -> getString(R.string.migration_pitch_note_electricity, getString(electricityLabel(pitch.electricityFlatRate)))
    LegacyPitchAttribute.LTE -> getString(R.string.migration_pitch_note_lte, getString(lteLabel(pitch.lteQuality)))
    LegacyPitchAttribute.PITCH_SLOPE -> getString(pitchSlopeLabel(pitch.pitchSlope))
    LegacyPitchAttribute.LEVELING_BLOCKS -> getString(R.string.migration_pitch_note_blocks)
}

private fun electricityLabel(rate: ElectricityFlatRate): Int = when (rate) {
    ElectricityFlatRate.YES -> R.string.electricity_yes
    ElectricityFlatRate.NO -> R.string.electricity_no
    ElectricityFlatRate.NOT_USED -> R.string.electricity_not_used
}

private fun lteLabel(quality: LteQuality): Int = when (quality) {
    LteQuality.GOOD -> R.string.lte_good
    LteQuality.OK -> R.string.lte_ok
    LteQuality.BAD -> R.string.lte_bad
}

private fun pitchSlopeLabel(slope: PitchSlope): Int = when (slope) {
    PitchSlope.LEVEL -> R.string.pitch_level
    PitchSlope.SLOPED -> R.string.pitch_sloped
}

/**
 * Version 8: `log_entries` bekommt `station_id` (Fremdschlüssel `stations`, `ON DELETE SET NULL`)
 * für die Verknüpfung mit der Station, deren Ver-/Entsorgungs-Häkchen den Eintrag erzeugt haben (4).
 * SQLite kann ab API 26 keine Fremdschlüssel nachträglich hinzufügen, daher wird `log_entries` wie
 * schon in [MIGRATION_1_2] neu aufgebaut; bestehende Einträge bleiben unverknüpft (`NULL`).
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `log_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `station_id` INTEGER, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `log_entries_new` (`id`, `uuid`, `vehicle_id`, `type`, `date`, `created_at`, `station_id`) " +
                "SELECT `id`, `uuid`, `vehicle_id`, `type`, `date`, `created_at`, NULL FROM `log_entries`",
        )
        db.execSQL("DROP TABLE `log_entries`")
        db.execSQL("ALTER TABLE `log_entries_new` RENAME TO `log_entries`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 7→8" }
        }
    }
}
