package app.restvolt.camperlog.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            val tours = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()) { Instant.EPOCH }.allTours()

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
            val tours = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()) { Instant.EPOCH }.allTours()
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
            val tours = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()) { Instant.EPOCH }.allTours()

            assertEquals(listOf(1L, 2L), tours.map { it.id })
            assertEquals(listOf(Money(1_250_000, Currency.getInstance("NOK"))), tours[0].costs)
            tours.forEach { assertEquals(4, UUID.fromString(it.uuid).version()) }
            assertEquals(2, tours.map { it.uuid }.distinct().size)
            assertEquals(1, RoomExchangeRateRepository(db.exchangeRateDao()).observeRates().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration4To5CreatesOneVehicleAndAssignsAllToursToIt() = runTest {
        createVersion4(
            "INSERT INTO tours VALUES (1, 'uuid-1', '2026-06-01', '2026-06-14', 'Lofoten', 'VACATION', 14, 13, " +
                "4200, 1, 'YES', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
            "INSERT INTO tours VALUES (2, 'uuid-2', '2026-07-01', '2026-07-01', 'Ostsee', 'DAY_TRIP', 1, 0, 120, " +
                "0, 'NO', 'OK', 'SLOPED', 1, '', NULL, 3000, 4000)",
            "INSERT INTO tour_costs VALUES (1, 'NOK', 1250000, 0)",
            "INSERT INTO settings VALUES (1, 'NOK')",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val vehicleRows = db.openHelper.readableDatabase.query("SELECT id, uuid, name FROM vehicles")
            val vehicleId: Long
            vehicleRows.use {
                assertEquals(1, it.count)
                it.moveToFirst()
                vehicleId = it.getLong(it.getColumnIndexOrThrow("id"))
                assertEquals(4, UUID.fromString(it.getString(it.getColumnIndexOrThrow("uuid"))).version())
                assertEquals("", it.getString(it.getColumnIndexOrThrow("name")))
            }

            val tours = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()) { Instant.EPOCH }.allTours()
            assertEquals(listOf(vehicleId, vehicleId), tours.map { it.vehicleId })
            assertEquals(listOf(Money(1_250_000, Currency.getInstance("NOK"))), tours[0].costs)
            assertEquals(Currency.getInstance("NOK"), RoomExchangeRateRepository(db.exchangeRateDao()).observeMainCurrency().first())

            // Der Fremdschlüssel verhindert das Löschen des Fahrzeugs, solange Touren darauf verweisen.
            val error = runCatching {
                db.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = $vehicleId")
            }.exceptionOrNull()
            assertTrue(error is SQLiteConstraintException)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration5To6AddsBreakdownAndEmptyWeightColumnsWithDefaults() = runTest {
        createVersion5(
            "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
                "insurance_policy_number, tire_size, created_at, updated_at) VALUES " +
                "(1, 'veh-1', 'Bluebird', '', '', '', '', '', '', '', '', 1000, 2000)",
            "INSERT INTO tours VALUES (1, 'uuid-1', 1, '2026-06-01', '2026-06-14', 'Lofoten', 'VACATION', 14, 13, " +
                "4200, 1, 'YES', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val vehicles = RoomVehicleRepository(db.vehicleDao()).allVehicles()
            val vehicle = vehicles.single()
            assertEquals("Bluebird", vehicle.name)
            assertEquals(null, vehicle.measuredEmptyWeightKg)
            assertEquals("", vehicle.breakdownProvider)
            assertEquals("", vehicle.breakdownPhone)
            assertEquals("", vehicle.travelProtectionProvider)
            assertEquals("", vehicle.insurerClaimsPhone)

            db.openHelper.writableDatabase.execSQL(
                "UPDATE vehicles SET measured_empty_weight_kg = 3020, breakdown_provider = 'ADAC', " +
                    "breakdown_phone = '+49 89 22 22 22' WHERE id = 1",
            )
            val updated = RoomVehicleRepository(db.vehicleDao()).allVehicles().single()
            assertEquals(3020, updated.measuredEmptyWeightKg)
            assertEquals("ADAC", updated.breakdownProvider)
            assertEquals("+49 89 22 22 22", updated.breakdownPhone)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration6To7CreatesOneOvernightStationPerTourWithNightsAndKeepsCosts() = runTest {
        createVersion6(
            "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
                "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, " +
                "breakdown_phone, travel_protection_provider, travel_protection_contract_number, " +
                "travel_protection_phone, insurer_claims_phone, created_at, updated_at) VALUES " +
                "(1, 'veh-1', 'Bluebird', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', 1000, 2000)",
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-17', 'Lofoten', 'VACATION', 14, 13, " +
                "3420, 1, 'YES', 'GOOD', 'LEVEL', 0, 'Reisenotiz', NULL, 1000, 2000)",
            "INSERT INTO tour_costs VALUES (1, 'EUR', 48650, 0)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()).allTours().single()
            assertEquals("Lofoten", tour.destination)
            assertEquals("Reisenotiz", tour.notes)
            assertEquals(listOf(Money(48650, EUR)), tour.costs)

            val station = RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations().single()
            assertEquals(1L, station.vehicleId)
            assertEquals(tour.id, station.tourId)
            assertEquals(StationType.OVERNIGHT, station.type)
            assertEquals(LocalDate.of(2026, 7, 4), station.date)
            assertEquals("Lofoten", station.name)
            assertEquals(13, station.nights)
            assertEquals(true, station.pitchAssigned)
            assertEquals(ElectricityBilling.FLAT_PER_STAY, station.electricityBilling)
            assertEquals(LteQuality.GOOD, station.lteQuality)
            assertEquals(PitchSlope.LEVEL, station.pitchSlope)
            assertEquals(false, station.levelingBlocksUsed)
            assertEquals(Instant.ofEpochMilli(1000), station.createdAt)
            assertEquals(Instant.ofEpochMilli(2000), station.updatedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration6To7ReportsMigratedToursOnlyWhenThereWereAny() = runTest {
        val vehicle = "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
            "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, " +
            "breakdown_phone, travel_protection_provider, travel_protection_contract_number, " +
            "travel_protection_phone, insurer_claims_phone, created_at, updated_at) VALUES " +
            "(1, 'veh-1', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', 1000, 2000)"
        createVersion6(vehicle)
        var reported = false
        CamperLogDatabase.open(context) { reported = true }.apply { openHelper.writableDatabase }.close()
        assertEquals(false, reported)

        context.deleteDatabase("camperlog.db")
        createVersion6(
            vehicle,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-04-18', '2026-04-18', 'Schwarzwald', 'DAY_TRIP', 1, 0, " +
                "240, 0, 'NOT_USED', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
        )
        CamperLogDatabase.open(context) { reported = true }.apply { openHelper.writableDatabase }.close()
        assertEquals(true, reported)
    }

    @Test
    fun migration6To7DayTripWithDefaultValuesGetsNoStationAndKeepsNotes() = runTest {
        createVersion6(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-20', '2026-07-20', 'Ostsee', 'DAY_TRIP', 1, 0, " +
                "120, 0, 'NOT_USED', 'GOOD', 'LEVEL', 0, 'Alte Notiz', NULL, 3000, 4000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()).allTours().single()
            assertEquals("Alte Notiz", tour.notes)
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations())
        } finally {
            db.close()
        }
    }

    @Test
    fun migration6To7DayTripWithNonDefaultValuesAppendsLocalizedNoteLine() = runTest {
        createVersion6(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-21', '2026-07-21', 'Flensburg', 'DAY_TRIP', 1, 0, " +
                "50, 0, 'NOT_USED', 'GOOD', 'SLOPED', 1, '', NULL, 5000, 6000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao()).allTours().single()
            assertEquals("Pitch: sloped, leveling blocks used", tour.notes)
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations())
        } finally {
            db.close()
        }
    }

    @Test
    fun migration6To7ForeignKeysRestrictVehicleAndCascadeTourDeletion() = runTest {
        createVersion6(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, 0, 'NOT_USED', 'GOOD', 'LEVEL', 0, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            assertEquals(1, RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations().size)

            // Ein Fahrzeug mit Stationen lässt sich nicht löschen.
            val error = runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = 1") }.exceptionOrNull()
            assertTrue(error is SQLiteConstraintException)

            // Das Löschen der Tour löscht ihre Station mit (CASCADE).
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations())
        } finally {
            db.close()
        }
    }

    @Test
    fun migration7To8AddsStationLinkAndKeepsExistingEntriesUnlinked() = runTest {
        createVersion7(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
            "INSERT INTO stations (id, uuid, vehicle_id, tour_id, type, date, name, place, notes, services, " +
                "favorite, created_at, updated_at) VALUES (1, 'station-1', 1, 1, 'SUPPLY', '2026-07-04', '', '', " +
                "'', 'CASSETTE', 0, 1000, 2000)",
            "INSERT INTO log_entries (id, uuid, vehicle_id, type, date, created_at) VALUES " +
                "(1, 'log-1', 1, 'CASSETTE_EMPTIED', '2026-07-04', 1000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val entry = RoomLogRepository(db.logDao()).allEntries().single()
            assertEquals(null, entry.stationId)

            // Der neue Fremdschlüssel greift auch nach der Migration: Verknüpfen und Löschen der Station setzt zurück.
            db.logDao().link(entry.id, 1)
            assertEquals(1L, RoomLogRepository(db.logDao()).allEntries().single().stationId)
            db.openHelper.writableDatabase.execSQL("DELETE FROM stations WHERE id = 1")
            assertEquals(null, RoomLogRepository(db.logDao()).allEntries().single().stationId)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration8To9AddsNextLeakTestDateColumn() = runTest {
        createVersion8(defaultVehicleInsert)

        val db = CamperLogDatabase.open(context)
        try {
            val vehicle = RoomVehicleRepository(db.vehicleDao()).allVehicles().single()
            assertEquals("Bluebird", vehicle.name)
            assertEquals(null, vehicle.nextLeakTestDate)

            db.openHelper.writableDatabase.execSQL("UPDATE vehicles SET next_leak_test_date = '2027-03-01' WHERE id = 1")
            val updated = RoomVehicleRepository(db.vehicleDao()).allVehicles().single()
            assertEquals(LocalDate.of(2027, 3, 1), updated.nextLeakTestDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration9To10MapsLegacyElectricityFlagAndKeepsStationsAndLogLinks() = runTest {
        createVersion9(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
            "INSERT INTO stations (id, uuid, vehicle_id, tour_id, type, date, name, place, notes, nights, " +
                "pitch_assigned, electricity_flat_rate, services, favorite, created_at, updated_at) VALUES " +
                "(1, 'station-1', 1, 1, 'OVERNIGHT', '2026-07-04', 'Platz', '', '', 2, 1, 'YES', '', 0, 1000, 2000)",
            "INSERT INTO stations (id, uuid, vehicle_id, tour_id, type, date, name, place, notes, services, " +
                "favorite, created_at, updated_at) VALUES (2, 'station-2', 1, NULL, 'SUPPLY', '2026-07-05', '', " +
                "'', '', '', 0, 3000, 4000)",
            "INSERT INTO log_entries (id, uuid, vehicle_id, type, date, created_at, station_id) VALUES " +
                "(1, 'log-1', 1, 'CASSETTE_EMPTIED', '2026-07-04', 1000, 1)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val stations = RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations().sortedBy { it.id }
            assertEquals(listOf(1L, 2L), stations.map { it.id })
            assertEquals(ElectricityBilling.FLAT_PER_STAY, stations[0].electricityBilling)
            assertEquals(null, stations[0].electricityCurrency)
            assertEquals(emptyList<StationCost>(), stations[0].costs)
            assertEquals(null, stations[1].electricityBilling)

            // Die Verknüpfung des Bordbuch-Eintrags mit der Station bleibt nach der Migration erhalten.
            assertEquals(1L, RoomLogRepository(db.logDao()).allEntries().single().stationId)

            // Die neue Tabelle `station_costs` existiert und hängt per Fremdschlüssel an `stations`.
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO station_costs (station_id, category, currency, amount_minor, note, position) " +
                    "VALUES (1, 'PITCH', 'EUR', 1500, '', 0)",
            )
            db.openHelper.writableDatabase.execSQL("DELETE FROM stations WHERE id = 1")
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM station_costs").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    /** Legt `camperlog.db` im Stand von Version 9 nach `schemas/…/9.json` an und füllt sie mit [inserts]. */
    private fun createVersion9(vararg inserts: String) = createDatabase(
        version = 9,
        identityHash = "b7beae6d0f670762271ff31dda67ba28",
        schema = listOf(
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
                "`measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, " +
                "`breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, " +
                "`travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, " +
                "`travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, " +
                "`power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, " +
                "`tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, " +
                "`fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, " +
                "`cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, " +
                "`next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, " +
                "`last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `station_id` INTEGER, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
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
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 8 nach `schemas/…/8.json` an und füllt sie mit [inserts]. */
    private fun createVersion8(vararg inserts: String) = createDatabase(
        version = 8,
        identityHash = "e91d16ae18c733292cee9363661a5447",
        schema = listOf(
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
                "`measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, " +
                "`breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, " +
                "`travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, " +
                "`travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, " +
                "`power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, " +
                "`tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, " +
                "`fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, " +
                "`cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, " +
                "`next_inspection_date` TEXT, `next_gas_check_date` TEXT, `last_oil_change_date` TEXT, " +
                "`last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `station_id` INTEGER, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
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
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 7 nach `schemas/…/7.json` an und füllt sie mit [inserts]. */
    private fun createVersion7(vararg inserts: String) = createDatabase(
        version = 7,
        identityHash = "0191d3a784cbbe17d960c28b6b036419",
        schema = listOf(
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
                "`measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, " +
                "`breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, " +
                "`travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, " +
                "`travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, " +
                "`power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, " +
                "`tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, " +
                "`fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, " +
                "`cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, " +
                "`next_inspection_date` TEXT, `next_gas_check_date` TEXT, `last_oil_change_date` TEXT, " +
                "`last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
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
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
        ),
        inserts = inserts.toList(),
    )

    private val defaultVehicleInsert =
        "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
            "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, " +
            "breakdown_phone, travel_protection_provider, travel_protection_contract_number, " +
            "travel_protection_phone, insurer_claims_phone, created_at, updated_at) VALUES " +
            "(1, 'veh-1', 'Bluebird', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', 1000, 2000)"

    /** Legt `camperlog.db` im Stand von Version 6 nach `schemas/…/6.json` an und füllt sie mit [inserts]. */
    private fun createVersion6(vararg inserts: String) = createDatabase(
        version = 6,
        identityHash = "db66d1a0bde20ff8ef5fa44b7ca3a68d",
        schema = listOf(
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
                "`measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, " +
                "`breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, " +
                "`travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, " +
                "`travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, " +
                "`power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, " +
                "`tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, " +
                "`fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, " +
                "`cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, " +
                "`next_inspection_date` TEXT, `next_gas_check_date` TEXT, `last_oil_change_date` TEXT, " +
                "`last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`pitch_assigned` INTEGER NOT NULL, `electricity_flat_rate` TEXT NOT NULL, " +
                "`lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, `leveling_blocks_used` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 5 nach `schemas/…/5.json` an und füllt sie mit [inserts]. */
    private fun createVersion5(vararg inserts: String) = createDatabase(
        version = 5,
        identityHash = "89bb465bece1122f7bd6cf85fbb13869",
        schema = listOf(
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
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` " +
                "(`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "`end_date` TEXT NOT NULL, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, " +
                "`travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`pitch_assigned` INTEGER NOT NULL, `electricity_flat_rate` TEXT NOT NULL, " +
                "`lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, `leveling_blocks_used` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, " +
                "`rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 4 nach `schemas/…/4.json` an und füllt sie mit [inserts]. */
    private fun createVersion4(vararg inserts: String) = createDatabase(
        version = 4,
        identityHash = "f6facf88f2a0a37deabeedb2313e2b16",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', `start_date` TEXT NOT NULL, `end_date` TEXT NOT NULL, " +
                "`destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, " +
                "`overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, " +
                "`pitch_assigned` INTEGER NOT NULL, `electricity_flat_rate` TEXT NOT NULL, " +
                "`lte_quality` TEXT NOT NULL, `pitch_slope` TEXT NOT NULL, `leveling_blocks_used` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
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
