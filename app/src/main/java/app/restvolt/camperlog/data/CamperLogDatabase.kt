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
        StationCostEntity::class,
        AttachmentEntity::class,
        VehicleDocumentEntity::class,
        TourCountryEntity::class,
        DiaryEntryEntity::class,
        ChecklistTemplateEntity::class,
        ChecklistTemplateItemEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        TrackPointEntity::class,
    ],
    version = 21,
    exportSchema = true,
)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    abstract fun diaryEntryDao(): DiaryEntryDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun vehicleDao(): VehicleDao

    abstract fun logDao(): LogDao

    abstract fun stationDao(): StationDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun vehicleDocumentDao(): VehicleDocumentDao

    abstract fun checklistTemplateDao(): ChecklistTemplateDao

    abstract fun checklistDao(): ChecklistDao

    abstract fun trackPointDao(): TrackPointDao

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
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21,
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
 * den unverändert übernommenen alten Stellplatz-Werten; bei Tagestrips (`overnight_stays = 0`)
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

    /** Legt für jede Tour mit mindestens einer Übernachtung die Übernachtungs-Station an. */
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
                        station.name, station.nights, station.pitchAssigned?.toSqlInt(), checkNotNull(pitch.electricityFlatRate).name,
                        station.lteQuality?.name, station.pitchSlope?.name, station.levelingBlocksUsed?.toSqlInt(),
                        station.createdAt.toEpochMilli(), station.updatedAt.toEpochMilli(),
                    ),
                )
            }
        }
    }

    /** Hängt Tagestrips mit von den alten Vorgaben abweichenden Stellplatz-Werten eine Notiz-Zeile an. */
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

/**
 * Kurzer, lokalisierter Textbaustein für ein von den alten Vorgaben abweichendes Stellplatz-Attribut;
 * `checkNotNull`, weil [LegacyPitchMigration.dayTripAttributes] ein Attribut nur enthält, wenn sein Feld gesetzt ist.
 */
private fun Context.pitchNoteFragment(attribute: LegacyPitchAttribute, pitch: LegacyPitchFields): String = when (attribute) {
    LegacyPitchAttribute.PITCH_ASSIGNED -> getString(R.string.migration_pitch_note_assigned)
    LegacyPitchAttribute.ELECTRICITY -> getString(R.string.migration_pitch_note_electricity, getString(electricityLabel(checkNotNull(pitch.electricityFlatRate))))
    LegacyPitchAttribute.LTE -> getString(R.string.migration_pitch_note_lte, getString(lteLabel(checkNotNull(pitch.lteQuality))))
    LegacyPitchAttribute.PITCH_SLOPE -> getString(pitchSlopeLabel(checkNotNull(pitch.pitchSlope)))
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
 * für die Verknüpfung mit der Station, deren Ver-/Entsorgungs-Häkchen den Eintrag erzeugt haben.
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

/**
 * Version 9: Fahrzeuge bekommen die nächste Dichtheitsprüfung (`next_leak_test_date`), analog zur
 * nächsten Gasprüfung. Eine nullable Textspalte braucht keinen Fremdschlüssel, daher reicht
 * `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_5_6].
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `next_leak_test_date` TEXT")
    }
}

/**
 * Version 10: Stationskosten ([CostCategory]) kommen in der neuen Tabelle `station_costs` hinzu, dazu
 * die Stromabrechnung ([ElectricityBilling]), Maut- und Fähre-Felder auf `stations`. Die alte Spalte
 * `electricity_flat_rate` entfällt dafür: `YES` wird zu `FLAT_PER_STAY`, `NO` zu `METERED`, `NOT_USED`
 * zu `NONE`, `NULL` bleibt `NULL` (siehe [app.restvolt.camperlog.domain.migrateLegacyElectricityFlatRate]).
 * SQLite kann ab API 26 keine Spalten löschen, daher wird `stations` wie schon in [MIGRATION_1_2] neu
 * aufgebaut; alle übrigen neuen Spalten bleiben für bestehende Stationen `NULL` bzw. leer.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `stations_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, " +
                "`coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, " +
                "`nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, " +
                "`pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, " +
                "`electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, " +
                "`electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, " +
                "`electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, " +
                "`electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL DEFAULT '', " +
                "`toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, " +
                "`ferry_booking_reference` TEXT NOT NULL DEFAULT '', `services` TEXT NOT NULL, " +
                "`weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, " +
                "`weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, " +
                "`favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `stations_new` (`id`, `uuid`, `vehicle_id`, `tour_id`, `type`, `date`, `time`, `name`, " +
                "`place`, `latitude`, `longitude`, `coordinate_source`, `accuracy_m`, `map_link`, `notes`, " +
                "`nights`, `site_kind`, `pitch_assigned`, `lte_quality`, `pitch_slope`, `leveling_blocks_used`, " +
                "`electricity_billing`, `services`, `weather_temperature_deci_c`, `weather_code`, " +
                "`weather_wind_kmh`, `weather_gust_kmh`, `weather_wind_direction_deg`, `weather_observed_at`, " +
                "`favorite`, `created_at`, `updated_at`) " +
                "SELECT `id`, `uuid`, `vehicle_id`, `tour_id`, `type`, `date`, `time`, `name`, `place`, " +
                "`latitude`, `longitude`, `coordinate_source`, `accuracy_m`, `map_link`, `notes`, `nights`, " +
                "`site_kind`, `pitch_assigned`, `lte_quality`, `pitch_slope`, `leveling_blocks_used`, " +
                "CASE `electricity_flat_rate` WHEN 'YES' THEN 'FLAT_PER_STAY' WHEN 'NO' THEN 'METERED' " +
                "WHEN 'NOT_USED' THEN 'NONE' ELSE NULL END, " +
                "`services`, `weather_temperature_deci_c`, `weather_code`, `weather_wind_kmh`, " +
                "`weather_gust_kmh`, `weather_wind_direction_deg`, `weather_observed_at`, `favorite`, " +
                "`created_at`, `updated_at` FROM `stations`",
        )
        db.execSQL("DROP TABLE `stations`")
        db.execSQL("ALTER TABLE `stations_new` RENAME TO `stations`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)")

        db.execSQL(
            "CREATE TABLE `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 9→10" }
        }
    }
}

/**
 * Version 11: Anhänge (Fotos, Dokumente) kommen in der neuen Tabelle `attachments` hinzu, dazu die
 * Fahrzeugdokumente ([app.restvolt.camperlog.domain.VehicleDocument]) in `vehicle_documents`
 * (Fremdschlüssel `vehicle_id` CASCADE). `attachments` hat keinen Fremdschlüssel: `owner_id` zeigt je
 * `owner_type` in eine andere Tabelle (`stations`, `repairs`, `log_entries` oder `vehicle_documents`),
 * siehe [app.restvolt.camperlog.domain.AttachmentRepository]. `latitude`/`longitude`/`taken_at` kommen
 * aus dem EXIF eines importierten Fotos; Dokumente lassen sie `NULL`. Beide Tabellen sind neu, daher
 * reicht `CREATE TABLE` ohne Datenübernahme.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, " +
                "`file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, " +
                "`caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 10→11" }
        }
    }
}

/**
 * Version 12: manuell nachgetragene oder ausgeblendete Länder einer Tour (siehe
 * [app.restvolt.camperlog.domain.tourCountries]) kommen in der neuen Tabelle `tour_countries` hinzu
 * (Fremdschlüssel `tour_id` CASCADE). Die Tabelle ist neu, daher reicht `CREATE TABLE` ohne
 * Datenübernahme; die automatisch erkannten Länder selbst werden nicht gespeichert.
 */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, " +
                "`added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 11→12" }
        }
    }
}

/**
 * Version 13: das Tagebuch ([app.restvolt.camperlog.domain.DiaryEntry]) kommt in der neuen Tabelle
 * `diary_entries` hinzu (Fremdschlüssel `tour_id` CASCADE, höchstens ein Eintrag je Tour und Tag).
 * Die Tabelle ist neu, daher reicht `CREATE TABLE` ohne Datenübernahme.
 */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 12→13" }
        }
    }
}

/**
 * Version 14: Checklisten ([app.restvolt.camperlog.domain.ChecklistTemplate], [app.restvolt.camperlog.domain.Checklist])
 * kommen in den neuen Tabellen `checklist_templates`, `checklist_template_items`, `checklists` und
 * `checklist_items` hinzu. Keine Vorlage wird automatisch angelegt. Alle vier Tabellen sind neu,
 * daher reicht `CREATE TABLE` ohne Datenübernahme.
 */
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), " +
                "FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), " +
                "FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 13→14" }
        }
    }
}

/**
 * Version 15: aufgezeichnete Trackpunkte ([app.restvolt.camperlog.domain.TrackPoint]) kommen in der neuen
 * Tabelle `track_points` hinzu (Fremdschlüssel `tour_id` CASCADE, ein Punkt je Tour und Zeitpunkt).
 * Die Tabelle ist neu, daher reicht `CREATE TABLE` ohne Datenübernahme.
 */
internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        )
    }
}

/**
 * Version 16: `tours.end_date` wird nullable; `NULL` kennzeichnet eine laufende Tour. Weil SQLite
 * die `NOT NULL`-Bedingung nicht direkt entfernen kann, wird nur die Tourtabelle neu aufgebaut.
 * `legacy_alter_table` verhindert dabei, dass SQLite die Fremdschlüssel der abhängigen Tabellen
 * beim vorübergehenden Umbenennen auf `tours_old` umschreibt.
 */
internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA legacy_alter_table = ON")
        db.execSQL("ALTER TABLE `tours` RENAME TO `tours_old`")
        db.execSQL(
            "CREATE TABLE `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `tours` (`id`, `uuid`, `vehicle_id`, `start_date`, `end_date`, `destination`, `tour_type`, " +
                "`travel_days`, `overnight_stays`, `distance_km`, `notes`, `map_link`, `created_at`, `updated_at`) " +
                "SELECT `id`, `uuid`, `vehicle_id`, `start_date`, `end_date`, `destination`, `tour_type`, " +
                "`travel_days`, `overnight_stays`, `distance_km`, `notes`, `map_link`, `created_at`, `updated_at` " +
                "FROM `tours_old`",
        )
        db.execSQL("DROP TABLE `tours_old`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)")
        db.execSQL("PRAGMA legacy_alter_table = OFF")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) { "Fremdschlüsselverletzung nach Migration 15→16" }
        }
    }
}

/**
 * Version 17: Touren bekommen einen freien [app.restvolt.camperlog.domain.Tour.name] und einen
 * URL-/dateinamensicheren [app.restvolt.camperlog.domain.Tour.slug] für Berichte und Exporte; beide
 * sind optional. Zwei nullable-freie Textspalten brauchen keinen Fremdschlüssel, daher reicht
 * `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_5_6].
 */
internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tours` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `tours` ADD COLUMN `slug` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Version 18: Stationen bekommen eine 1–5-Bewertung ([app.restvolt.camperlog.domain.Station.rating],
 * für jeden Stationstyp), einen Kilometerstand ([app.restvolt.camperlog.domain.Station.odometerKm]),
 * eine manuell erfasste Temperatur in Zehntelgrad ([app.restvolt.camperlog.domain.Station.manualTemperatureDeciC])
 * und einen Link ([app.restvolt.camperlog.domain.Station.link], bislang nur bei Übernachtungen genutzt).
 * Vier nullable Spalten ohne Fremdschlüssel, daher reicht `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_5_6].
 */
internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stations` ADD COLUMN `rating` INTEGER")
        db.execSQL("ALTER TABLE `stations` ADD COLUMN `odometer_km` INTEGER")
        db.execSQL("ALTER TABLE `stations` ADD COLUMN `manual_temperature_deci_c` INTEGER")
        db.execSQL("ALTER TABLE `stations` ADD COLUMN `link` TEXT")
    }
}

/**
 * Version 19: Fahrzeuge bekommen die benötigten Energiearten
 * ([app.restvolt.camperlog.domain.Vehicle.requiredEnergyTypes]), um im Formular nur die passenden
 * Tank-/Kapazitätsfelder anzuzeigen; eine leere Menge bedeutet "nicht konfiguriert" und zeigt wie
 * bisher alle Felder. Eine nullable-freie Textspalte ohne Fremdschlüssel, daher reicht
 * `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_16_17].
 */
internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `required_energy_types` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Version 20: Fahrzeuge bekommen den Hubraum ([app.restvolt.camperlog.domain.Vehicle.displacementCc]),
 * die Schaltungsart ([app.restvolt.camperlog.domain.Vehicle.transmission]) und den Kilometerstand beim
 * Verkauf ([app.restvolt.camperlog.domain.Vehicle.saleOdometerKm]). Drei nullable Spalten ohne
 * Fremdschlüssel, daher reicht `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_16_17].
 */
internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `displacement_cc` INTEGER")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `transmission` TEXT")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `sale_odometer_km` INTEGER")
    }
}

/**
 * Version 21: Touren und Fahrzeuge bekommen `is_demo`
 * ([app.restvolt.camperlog.domain.Tour.isDemo]/[app.restvolt.camperlog.domain.Vehicle.isDemo]), um
 * die simulierte Demo-Tour des Tutorials ([app.restvolt.camperlog.domain.guide.DemoTourSession])
 * zuverlässig wiederzufinden und aufzuräumen. Zwei Boolean-Spalten ohne Fremdschlüssel, daher reicht
 * `ALTER TABLE ADD COLUMN` wie schon bei [MIGRATION_16_17].
 */
internal val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tours` ADD COLUMN `is_demo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `is_demo` INTEGER NOT NULL DEFAULT 0")
    }
}
