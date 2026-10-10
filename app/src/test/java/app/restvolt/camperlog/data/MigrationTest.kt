package app.restvolt.camperlog.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.EnergyType
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackSummary
import app.restvolt.camperlog.domain.TransmissionType
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
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
 * Prüft jede Migration von Version 1 bis 20 einzeln: Die Ausgangsdatenbank wird exakt nach dem
 * jeweiligen `schemas/…/<n>.json` angelegt (`createVersion<n>`), migriert und auf erhaltene bzw.
 * umgewandelte Daten geprüft. Room validiert beim Öffnen zusätzlich, dass das Ergebnis dem Schema der
 * Zielversion entspricht.
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
            val tours = RoomTourRepository(db) { Instant.EPOCH }.allTours()

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
            val tours = RoomTourRepository(db) { Instant.EPOCH }.allTours()
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
            val tours = RoomTourRepository(db) { Instant.EPOCH }.allTours()

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

            val tours = RoomTourRepository(db) { Instant.EPOCH }.allTours()
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
            val vehicles = RoomVehicleRepository(db).allVehicles()
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
            val updated = RoomVehicleRepository(db).allVehicles().single()
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
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Lofoten", tour.destination)
            assertEquals("Reisenotiz", tour.notes)
            assertEquals(listOf(Money(48650, EUR)), tour.costs)

            val station = RoomStationRepository(db, RoomLogRepository(db)).allStations().single()
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
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Alte Notiz", tour.notes)
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db)).allStations())
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
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Pitch: sloped, leveling blocks used", tour.notes)
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db)).allStations())
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
            assertEquals(1, RoomStationRepository(db, RoomLogRepository(db)).allStations().size)

            // Ein Fahrzeug mit Stationen lässt sich nicht löschen.
            val error = runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = 1") }.exceptionOrNull()
            assertTrue(error is SQLiteConstraintException)

            // Das Löschen der Tour löscht ihre Station mit (CASCADE).
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            assertEquals(emptyList<Station>(), RoomStationRepository(db, RoomLogRepository(db)).allStations())
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
            val entry = RoomLogRepository(db).allEntries().single()
            assertEquals(null, entry.stationId)

            // Der neue Fremdschlüssel greift auch nach der Migration: Verknüpfen und Löschen der Station setzt zurück.
            db.logDao().link(entry.id, 1)
            assertEquals(1L, RoomLogRepository(db).allEntries().single().stationId)
            db.openHelper.writableDatabase.execSQL("DELETE FROM stations WHERE id = 1")
            assertEquals(null, RoomLogRepository(db).allEntries().single().stationId)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration8To9AddsNextLeakTestDateColumn() = runTest {
        createVersion8(defaultVehicleInsert)

        val db = CamperLogDatabase.open(context)
        try {
            val vehicle = RoomVehicleRepository(db).allVehicles().single()
            assertEquals("Bluebird", vehicle.name)
            assertEquals(null, vehicle.nextLeakTestDate)

            db.openHelper.writableDatabase.execSQL("UPDATE vehicles SET next_leak_test_date = '2027-03-01' WHERE id = 1")
            val updated = RoomVehicleRepository(db).allVehicles().single()
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
            val stations = RoomStationRepository(db, RoomLogRepository(db)).allStations().sortedBy { it.id }
            assertEquals(listOf(1L, 2L), stations.map { it.id })
            assertEquals(ElectricityBilling.FLAT_PER_STAY, stations[0].electricityBilling)
            assertEquals(null, stations[0].electricityCurrency)
            assertEquals(emptyList<StationCost>(), stations[0].costs)
            assertEquals(null, stations[1].electricityBilling)

            // Die Verknüpfung des Bordbuch-Eintrags mit der Station bleibt nach der Migration erhalten.
            assertEquals(1L, RoomLogRepository(db).allEntries().single().stationId)

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

    @Test
    fun migration10To11CreatesAttachmentsAndVehicleDocumentsTables() = runTest {
        createVersion10(defaultVehicleInsert)

        val db = CamperLogDatabase.open(context)
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO vehicle_documents (uuid, vehicle_id, kind, title, expiry_date, created_at, updated_at) " +
                    "VALUES ('doc-1', 1, 'REGISTRATION', 'Fahrzeugschein', '2030-01-01', 1000, 1000)",
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO attachments (uuid, owner_type, owner_id, file_name, mime_type, size_bytes, width, height, caption, created_at) " +
                    "VALUES ('att-1', 'VEHICLE_DOCUMENT', 1, 'att-1.jpg', 'image/jpeg', 1024, 800, 600, '', 1000)",
            )

            val documents = RoomVehicleDocumentRepository(db).allDocuments()
            assertEquals(listOf("Fahrzeugschein"), documents.map { it.title })
            assertEquals(LocalDate.of(2030, 1, 1), documents.single().expiryDate)

            val attachmentDao = db.attachmentDao()
            val attachments = RoomAttachmentRepository(attachmentDao, FakeAttachmentFileStore()).allAttachments()
            assertEquals(1, attachments.size)
            assertEquals(AttachmentOwnerType.VEHICLE_DOCUMENT, attachments.single().ownerType)
            assertEquals(DocumentKind.REGISTRATION, documents.single().kind)
            assertEquals(null, attachments.single().latitude)
            assertEquals(null, attachments.single().takenAt)

            // `vehicle_documents` kaskadiert über den Fremdschlüssel auf `vehicles`; `attachments` kennt
            // keinen Fremdschlüssel auf das Dokument (siehe KDoc von RoomVehicleRepository) und bleibt
            // deshalb stehen - genau deshalb räumt die App das selbst beim Löschen eines Dokuments auf.
            db.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = 1")
            assertEquals(emptyList<String>(), RoomVehicleDocumentRepository(db).allDocuments().map { it.title })
            assertEquals(1, RoomAttachmentRepository(attachmentDao, FakeAttachmentFileStore()).allAttachments().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration10To11AttachmentsHaveLocationAndTakenAtColumns() = runTest {
        createVersion10(defaultVehicleInsert)

        val db = CamperLogDatabase.open(context)
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO attachments (uuid, owner_type, owner_id, file_name, mime_type, size_bytes, width, height, " +
                    "latitude, longitude, taken_at, caption, created_at) VALUES " +
                    "('att-2', 'STATION', 1, 'att-2.jpg', 'image/jpeg', 1024, 800, 600, 47.5, 11.0, " +
                    "'2026-05-03T14:22:01', '', 1000)",
            )

            val attachment = RoomAttachmentRepository(db.attachmentDao(), FakeAttachmentFileStore()).allAttachments().single()
            assertEquals(47.5, attachment.latitude)
            assertEquals(11.0, attachment.longitude)
            assertEquals(java.time.LocalDateTime.of(2026, 5, 3, 14, 22, 1), attachment.takenAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration12To13CreatesEmptyDiaryEntriesTableAndKeepsTours() = runTest {
        createVersion12(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Lofoten", tour.destination)
            assertEquals(emptyList<String>(), RoomDiaryEntryRepository(db).observeForTour(tour.id).first())

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO diary_entries (uuid, tour_id, date, text, created_at, updated_at) VALUES " +
                    "('entry-1', 1, '2026-07-04', 'Langer Tag am Fjord.', 1000, 2000)",
            )
            val entries = RoomDiaryEntryRepository(db).observeForTour(tour.id).first()
            assertEquals(listOf("Langer Tag am Fjord."), entries.map { it.text })

            // Zweiter Eintrag für denselben Tag verletzt den eindeutigen Index.
            assertTrue(
                runCatching {
                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO diary_entries (uuid, tour_id, date, text, created_at, updated_at) VALUES " +
                            "('entry-2', 1, '2026-07-04', 'Zweiter Eintrag.', 1000, 2000)",
                    )
                }.exceptionOrNull() is SQLiteConstraintException,
            )

            // Das Löschen der Tour löscht ihre Tagebucheinträge mit (CASCADE).
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM diary_entries").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migration13To14CreatesEmptyChecklistTablesAndKeepsTours() = runTest {
        createVersion13(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Lofoten", tour.destination)
            assertEquals(emptyList<String>(), RoomChecklistTemplateRepository(db).observeAll().first())
            assertEquals(emptyList<String>(), RoomChecklistRepository(db).observeForTour(tour.id).first())

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO checklist_templates (uuid, name, created_at, updated_at) VALUES ('template-1', 'Abfahrt', 1000, 1000)",
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO checklist_template_items (template_id, position, text) VALUES (1, 0, 'Dachluken schließen')",
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO checklists (uuid, vehicle_id, tour_id, title, created_at, updated_at) VALUES " +
                    "('checklist-1', 1, 1, 'Abfahrt', 1000, 1000)",
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO checklist_items (checklist_id, position, text, checked) VALUES (1, 0, 'Dachluken schließen', 0)",
            )

            val templates = RoomChecklistTemplateRepository(db).observeAll().first()
            assertEquals(listOf("Dachluken schließen"), templates.single().items)
            val checklists = RoomChecklistRepository(db).observeForTour(tour.id).first()
            assertEquals("Abfahrt", checklists.single().title)
            assertEquals(false, checklists.single().items.single().checked)

            // Das Löschen der Tour löscht ihre Checklisten mit (CASCADE), die Vorlage bleibt unberührt.
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            assertEquals(emptyList<String>(), RoomChecklistRepository(db).observeForTour(tour.id).first())
            assertEquals(1, RoomChecklistTemplateRepository(db).observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration14To15CreatesEmptyTrackTableAndCascadesWithTour() = runTest {
        createVersion14(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Lofoten", tour.destination)
            val tracks = RoomTrackRepository(db)
            assertEquals(TrackSummary.EMPTY, tracks.observeSummary(tour.id).first())
            assertEquals(1, tracks.nextSegment(tour.id))

            val point = TrackPoint(tourId = tour.id, segment = 1, recordedAt = Instant.ofEpochMilli(5000), latitude = 68.2, longitude = 14.5)
            tracks.addAll(listOf(point, point.copy(latitude = 68.3)))
            tracks.addAll(listOf(point.copy(recordedAt = Instant.ofEpochMilli(6000), segment = 2, accuracyM = 8, altitudeM = 12)))
            // Gleicher Zeitpunkt wird übersprungen, nicht doppelt gespeichert.
            assertEquals(TrackSummary(points = 2, segments = 2), tracks.observeSummary(tour.id).first())
            assertEquals(3, tracks.nextSegment(tour.id))
            assertEquals(68.2, tracks.observeForTour(tour.id).first().first().latitude, 0.0)

            // Das Löschen der Tour löscht ihren Track mit (CASCADE).
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            assertEquals(emptyList<TrackPoint>(), tracks.allPoints())
        } finally {
            db.close()
        }
    }

    @Test
    fun migration15To16MakesEndDateNullableAndKeepsTourChildren() = runTest {
        createVersion15(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
            "INSERT INTO tour_costs VALUES (1, 'EUR', 1234, 0)",
            "INSERT INTO track_points VALUES (1, 1, 1, 5000, 68.2, 14.5, NULL, NULL)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val repository = RoomTourRepository(db) { Instant.EPOCH }
            val migrated = repository.allTours().single()
            assertEquals(LocalDate.of(2026, 7, 5), migrated.endDate)
            assertEquals(listOf(Money(1234, EUR)), migrated.costs)
            assertEquals(1, RoomTrackRepository(db).allPoints().size)

            repository.save(migrated.copy(id = 0, uuid = "tour-2", endDate = null, costs = emptyList()))
            assertEquals(null, repository.allTours().last().endDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration16To17AddsEmptyNameAndSlugColumnsAndKeepsThemEditable() = runTest {
        createVersion16(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val repository = RoomTourRepository(db) { Instant.EPOCH }
            val tour = repository.allTours().single()
            assertEquals("", tour.name)
            assertEquals("", tour.slug)

            repository.save(tour.copy(name = "Sommerurlaub", slug = "sommerurlaub-2026"))
            val reloaded = repository.allTours().single()
            assertEquals("Sommerurlaub", reloaded.name)
            assertEquals("sommerurlaub-2026", reloaded.slug)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration17To18AddsRatingOdometerTemperatureAndLinkColumnsAndKeepsThemEditable() = runTest {
        createVersion17(
            defaultVehicleInsert,
            "INSERT INTO tours (id, uuid, vehicle_id, start_date, end_date, destination, tour_type, travel_days, " +
                "overnight_stays, distance_km, notes, map_link, created_at, updated_at) VALUES (1, 'tour-1', 1, " +
                "'2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, 100, '', NULL, 1000, 2000)",
            "INSERT INTO stations (id, uuid, vehicle_id, tour_id, type, date, name, place, notes, toll_payment_method, " +
                "ferry_booking_reference, services, favorite, created_at, updated_at) VALUES (1, 'station-1', 1, 1, " +
                "'FOOD', '2026-07-04', '', '', '', '', '', '', 0, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val repository = RoomStationRepository(db, RoomLogRepository(db))
            val station = repository.allStations().single()
            assertEquals(null, station.rating)
            assertEquals(null, station.odometerKm)
            assertEquals(null, station.manualTemperatureDeciC)
            assertEquals(null, station.link)

            repository.save(station.copy(rating = 4, odometerKm = 54_000, manualTemperatureDeciC = 183, link = "https://example.org/platz"))
            val reloaded = repository.allStations().single()
            assertEquals(4, reloaded.rating)
            assertEquals(54_000, reloaded.odometerKm)
            assertEquals(183, reloaded.manualTemperatureDeciC)
            assertEquals("https://example.org/platz", reloaded.link)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration18To19AddsRequiredEnergyTypesColumnAndKeepsItEditable() = runTest {
        createVersion18(defaultVehicleInsert)

        val db = CamperLogDatabase.open(context)
        try {
            val repository = RoomVehicleRepository(db)
            val vehicle = repository.allVehicles().single()
            assertEquals(emptySet<EnergyType>(), vehicle.requiredEnergyTypes)

            repository.save(vehicle.copy(requiredEnergyTypes = setOf(EnergyType.DIESEL, EnergyType.ELECTRICITY)))
            val reloaded = repository.allVehicles().single()
            assertEquals(setOf(EnergyType.DIESEL, EnergyType.ELECTRICITY), reloaded.requiredEnergyTypes)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration19To20AddsDisplacementTransmissionAndSaleOdometerColumnsAndKeepsThemEditable() = runTest {
        createVersion19(
            "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
                "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, " +
                "breakdown_phone, travel_protection_provider, travel_protection_contract_number, " +
                "travel_protection_phone, insurer_claims_phone, required_energy_types, created_at, updated_at) VALUES " +
                "(1, 'veh-1', 'Bluebird', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val repository = RoomVehicleRepository(db)
            val vehicle = repository.allVehicles().single()
            assertEquals(null, vehicle.displacementCc)
            assertEquals(null, vehicle.transmission)
            assertEquals(null, vehicle.saleOdometerKm)

            repository.save(vehicle.copy(displacementCc = 2287, transmission = TransmissionType.AUTOMATIC, saleOdometerKm = 80_000))
            val reloaded = repository.allVehicles().single()
            assertEquals(2287, reloaded.displacementCc)
            assertEquals(TransmissionType.AUTOMATIC, reloaded.transmission)
            assertEquals(80_000, reloaded.saleOdometerKm)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration20To21AddsIsDemoColumnsToToursAndVehiclesDefaultingToFalseAndKeepsThemEditable() = runTest {
        createVersion20(
            "INSERT INTO vehicles (id, uuid, name, license_plate, manufacturer, model, vin, notes, insurer, " +
                "insurance_policy_number, tire_size, breakdown_provider, breakdown_membership_number, " +
                "breakdown_phone, travel_protection_provider, travel_protection_contract_number, " +
                "travel_protection_phone, insurer_claims_phone, required_energy_types, created_at, updated_at) VALUES " +
                "(1, 'veh-1', 'Bluebird', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', 1000, 2000)",
            "INSERT INTO tours (id, uuid, vehicle_id, start_date, end_date, destination, name, slug, tour_type, " +
                "travel_days, overnight_stays, distance_km, notes, map_link, created_at, updated_at) VALUES " +
                "(1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', '', '', 'WEEKEND', 2, 1, 100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val vehicles = RoomVehicleRepository(db)
            val tours = RoomTourRepository(db)
            val vehicle = vehicles.allVehicles().single()
            val tour = tours.allTours().single()
            assertEquals(false, vehicle.isDemo)
            assertEquals(false, tour.isDemo)
            assertEquals(emptyList<Long>(), tours.demoTourIds())

            vehicles.save(vehicle.copy(isDemo = true))
            tours.save(tour.copy(isDemo = true))

            assertEquals(true, vehicles.allVehicles().single().isDemo)
            assertEquals(true, tours.allTours().single().isDemo)
            assertEquals(listOf(tour.id), tours.demoTourIds())
        } finally {
            db.close()
        }
    }

    /** Legt `camperlog.db` im Stand von Version 20 nach `schemas/…/20.json` an und füllt sie mit [inserts]. */
    private fun createVersion20(vararg inserts: String) = createDatabase(
        version = 20,
        identityHash = "20e482871a1321ed45a06b72b3eb41d6",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT, `destination` TEXT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `slug` TEXT NOT NULL DEFAULT '', `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, `rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, `current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, `first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, `purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, `sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, `sale_odometer_km` INTEGER, `insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, `insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, `vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, `length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, `measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, `breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, `travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, `travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, `power_kw` INTEGER, `displacement_cc` INTEGER, `transmission` TEXT, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, `tire_pressure_rear_mbar` INTEGER, `required_energy_types` TEXT NOT NULL, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, `fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, `cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, `next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, `last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `station_id` INTEGER, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, `electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, `electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, `electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, `electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, `electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, `ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `rating` INTEGER, `odometer_km` INTEGER, `manual_temperature_deci_c` INTEGER, `link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, `caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)",
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 19 nach `schemas/…/19.json` an und füllt sie mit [inserts]. */
    private fun createVersion19(vararg inserts: String) = createDatabase(
        version = 19,
        identityHash = "44432b0508d5e81baacf41f658c62992",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT, `destination` TEXT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `slug` TEXT NOT NULL DEFAULT '', `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, `rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, `current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, `first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, `purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, `sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, `insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, `insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, `vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, `length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, `measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, `breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, `travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, `travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, `power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, `tire_pressure_rear_mbar` INTEGER, `required_energy_types` TEXT NOT NULL, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, `fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, `cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, `next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, `last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `station_id` INTEGER, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, `electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, `electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, `electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, `electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, `electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, `ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `rating` INTEGER, `odometer_km` INTEGER, `manual_temperature_deci_c` INTEGER, `link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, `caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)",
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 18 nach `schemas/…/18.json` an und füllt sie mit [inserts]. */
    private fun createVersion18(vararg inserts: String) = createDatabase(
        version = 18,
        identityHash = "29d3a06aec5792ca6d964be220b65a80",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT, `destination` TEXT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `slug` TEXT NOT NULL DEFAULT '', `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, `rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, `current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, `first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, `purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, `sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, `insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, `insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, `vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, `length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, `measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, `breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, `travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, `travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, `power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, `tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, `fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, `cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, `next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, `last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `station_id` INTEGER, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, `electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, `electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, `electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, `electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, `electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, `ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `rating` INTEGER, `odometer_km` INTEGER, `manual_temperature_deci_c` INTEGER, `link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, `caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)",
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 17 nach `schemas/…/17.json` an und füllt sie mit [inserts]. */
    private fun createVersion17(vararg inserts: String) = createDatabase(
        version = 17,
        identityHash = "68d1530b34a92cff2da0c731054f6f00",
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT, `destination` TEXT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `slug` TEXT NOT NULL DEFAULT '', `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, `rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, `current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, `first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, `purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, `sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, `insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, `insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, `vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, `length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, `measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, `breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, `travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, `travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, `power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, `tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, `fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, `cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, `next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, `last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `station_id` INTEGER, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, `electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, `electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, `electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, `electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, `electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, `ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, `caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)",
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 16 nach `schemas/…/16.json` an und füllt sie mit [inserts]. */
    private fun createVersion16(vararg inserts: String) = createVersion14Schema(
        version = 16,
        identityHash = "50fb4ee6a871a5518847c6b9f8777501",
        includeTrackPoints = true,
        inserts = inserts,
        toursEndDateNullable = true,
    )

    private fun createVersion15(vararg inserts: String) = createVersion14Schema(
        version = 15,
        identityHash = "f32acf41debcc664b1bdf640271c1311",
        includeTrackPoints = true,
        inserts = inserts,
    )

    /** Legt `camperlog.db` im Stand von Version 14 nach `schemas/…/14.json` an und füllt sie mit [inserts]. */
    private fun createVersion14(vararg inserts: String) = createVersion14Schema(
        version = 14,
        identityHash = "e66896a3e7ff829c6c299ff1974b48c2",
        includeTrackPoints = false,
        inserts = inserts,
    )

    private fun createVersion14Schema(
        version: Int,
        identityHash: String,
        includeTrackPoints: Boolean,
        inserts: Array<out String>,
        toursEndDateNullable: Boolean = false,
    ) = createDatabase(
        version = version,
        identityHash = identityHash,
        schema = listOf(
            "CREATE TABLE IF NOT EXISTS `tours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `vehicle_id` INTEGER NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT${if (toursEndDateNullable) "" else " NOT NULL"}, `destination` TEXT NOT NULL, `tour_type` TEXT NOT NULL, `travel_days` INTEGER NOT NULL, `overnight_stays` INTEGER NOT NULL, `distance_km` INTEGER NOT NULL, `notes` TEXT NOT NULL, `map_link` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_tours_start_date` ON `tours` (`start_date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tours_uuid` ON `tours` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_tours_vehicle_id` ON `tours` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_costs` (`tour_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `currency`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currency` TEXT NOT NULL, `per_euro` TEXT NOT NULL, `rate_date` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`currency`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `main_currency` TEXT NOT NULL, `current_vehicle_id` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `license_plate` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `vin` TEXT NOT NULL, `first_registration` TEXT, `notes` TEXT NOT NULL, `purchase_date` TEXT, `purchase_price_currency` TEXT, `purchase_price_minor` INTEGER, `purchase_odometer_km` INTEGER, `sale_date` TEXT, `sale_price_currency` TEXT, `sale_price_minor` INTEGER, `insurer` TEXT NOT NULL, `insurance_policy_number` TEXT NOT NULL, `insurance_premium_per_year_currency` TEXT, `insurance_premium_per_year_minor` INTEGER, `vehicle_tax_per_year_currency` TEXT, `vehicle_tax_per_year_minor` INTEGER, `length_cm` INTEGER, `width_cm` INTEGER, `height_cm` INTEGER, `gross_weight_kg` INTEGER, `measured_empty_weight_kg` INTEGER, `breakdown_provider` TEXT NOT NULL, `breakdown_membership_number` TEXT NOT NULL, `breakdown_phone` TEXT NOT NULL, `travel_protection_provider` TEXT NOT NULL, `travel_protection_contract_number` TEXT NOT NULL, `travel_protection_phone` TEXT NOT NULL, `insurer_claims_phone` TEXT NOT NULL, `power_kw` INTEGER, `tire_size` TEXT NOT NULL, `tire_pressure_front_mbar` INTEGER, `tire_pressure_rear_mbar` INTEGER, `fuel_tank_dl` INTEGER, `ad_blue_tank_dl` INTEGER, `fresh_water_tank_dl` INTEGER, `grey_water_tank_dl` INTEGER, `boiler_dl` INTEGER, `cassette_dl` INTEGER, `battery_capacity_ah` INTEGER, `solar_power_wp` INTEGER, `next_inspection_date` TEXT, `next_gas_check_date` TEXT, `next_leak_test_date` TEXT, `last_oil_change_date` TEXT, `last_oil_change_odometer_km` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_uuid` ON `vehicles` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `odometer_km` INTEGER, `cost_currency` TEXT, `cost_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_repairs_vehicle_id` ON `repairs` (`vehicle_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_repairs_uuid` ON `repairs` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `station_id` INTEGER, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_vehicle_id_type_date` ON `log_entries` (`vehicle_id`, `type`, `date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_log_entries_uuid` ON `log_entries` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_station_id` ON `log_entries` (`station_id`)",
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, `nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, `pitch_slope` TEXT, `leveling_blocks_used` INTEGER, `electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, `electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, `electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, `electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, `electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, `ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, `weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, `weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, `favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, `caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
            "CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_templates_uuid` ON `checklist_templates` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `checklist_template_items` (`template_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`template_id`, `position`), FOREIGN KEY(`template_id`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_uuid` ON `checklists` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_vehicle_id` ON `checklists` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_checklists_tour_id` ON `checklists` (`tour_id`)",
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`checklist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`checklist_id`, `position`), FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        ) + if (includeTrackPoints) listOf(
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tour_id` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `recorded_at` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy_m` INTEGER, `altitude_m` INTEGER, FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_track_points_tour_id_recorded_at` ON `track_points` (`tour_id`, `recorded_at`)",
        ) else emptyList(),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 13 nach `schemas/…/13.json` an und füllt sie mit [inserts]. */
    private fun createVersion13(vararg inserts: String) = createDatabase(
        version = 13,
        identityHash = "7a0ee2df505c5ddd3de23b5aee5eeec9",
        schema = listOf(
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
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, " +
                "`coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, " +
                "`nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, " +
                "`pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, " +
                "`electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, " +
                "`electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, " +
                "`electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, " +
                "`electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, `toll_country` TEXT, " +
                "`toll_valid_from` TEXT, `toll_valid_until` TEXT, " +
                "`ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, " +
                "`weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, " +
                "`weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, " +
                "`favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, " +
                "`file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, " +
                "`caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, " +
                "`added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `tour_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_tour_id` ON `diary_entries` (`tour_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_tour_id_date` ON `diary_entries` (`tour_id`, `date`)",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 12 nach `schemas/…/12.json` an und füllt sie mit [inserts]. */
    private fun createVersion12(vararg inserts: String) = createDatabase(
        version = 12,
        identityHash = "0ba7fd7c99d84fd94a484f308afe2f3c",
        schema = listOf(
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
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, " +
                "`coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, " +
                "`nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, " +
                "`pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, " +
                "`electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, " +
                "`electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, " +
                "`electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, " +
                "`electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, " +
                "`toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, " +
                "`ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, " +
                "`weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, " +
                "`weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, " +
                "`favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, " +
                "`file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, " +
                "`caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
            "CREATE TABLE IF NOT EXISTS `tour_countries` (`tour_id` INTEGER NOT NULL, `code` TEXT NOT NULL, " +
                "`added` INTEGER NOT NULL, PRIMARY KEY(`tour_id`, `code`), " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        ),
        inserts = inserts.toList(),
    )

    /** Legt `camperlog.db` im Stand von Version 11 nach `schemas/…/11.json` an und füllt sie mit [inserts]. */
    private fun createVersion11(vararg inserts: String) = createDatabase(
        version = 11,
        identityHash = "fa8128ee2b998a7f833e0c37fcdd418d",
        schema = listOf(
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
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, " +
                "`coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, " +
                "`nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, " +
                "`pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, " +
                "`electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, " +
                "`electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, " +
                "`electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, " +
                "`electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, " +
                "`toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, " +
                "`ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, " +
                "`weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, " +
                "`weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, " +
                "`favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, " +
                "`file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`width` INTEGER, `height` INTEGER, `latitude` REAL, `longitude` REAL, `taken_at` TEXT, " +
                "`caption` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, `owner_id`)",
            "CREATE TABLE IF NOT EXISTS `vehicle_documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `vehicle_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`expiry_date` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_documents_uuid` ON `vehicle_documents` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_vehicle_documents_vehicle_id` ON `vehicle_documents` (`vehicle_id`)",
        ),
        inserts = inserts.toList(),
    )

    @Test
    fun migration11To12CreatesEmptyTourCountriesTableAndKeepsTours() = runTest {
        createVersion11(
            defaultVehicleInsert,
            "INSERT INTO tours VALUES (1, 'tour-1', 1, '2026-07-04', '2026-07-05', 'Lofoten', 'WEEKEND', 2, 1, " +
                "100, '', NULL, 1000, 2000)",
        )

        val db = CamperLogDatabase.open(context)
        try {
            val tour = RoomTourRepository(db).allTours().single()
            assertEquals("Lofoten", tour.destination)
            assertEquals(emptySet<String>(), tour.manualCountriesAdded)
            assertEquals(emptySet<String>(), tour.manualCountriesRemoved)

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO tour_countries (tour_id, code, added) VALUES (1, 'NO', 1)",
            )
            val withCountry = RoomTourRepository(db).allTours().single()
            assertEquals(setOf("NO"), withCountry.manualCountriesAdded)

            // Das Löschen der Tour löscht ihre Länderanpassungen mit (CASCADE).
            db.openHelper.writableDatabase.execSQL("DELETE FROM tours WHERE id = 1")
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tour_countries").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    /** Legt `camperlog.db` im Stand von Version 10 nach `schemas/…/10.json` an und füllt sie mit [inserts]. */
    private fun createVersion10(vararg inserts: String) = createDatabase(
        version = 10,
        identityHash = "1e91bde7e336ecd620afcc8e550a1d68",
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
            "CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `tour_id` INTEGER, `type` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, " +
                "`coordinate_source` TEXT, `accuracy_m` INTEGER, `map_link` TEXT, `notes` TEXT NOT NULL, " +
                "`nights` INTEGER, `site_kind` TEXT, `pitch_assigned` INTEGER, `lte_quality` TEXT, " +
                "`pitch_slope` TEXT, `leveling_blocks_used` INTEGER, " +
                "`electricity_billing` TEXT, `electricity_currency` TEXT, `electricity_flat_amount_minor` INTEGER, " +
                "`electricity_base_fee_minor` INTEGER, `electricity_price_per_kwh` TEXT, " +
                "`electricity_coin_price_minor` INTEGER, `electricity_coins_used` INTEGER, " +
                "`electricity_kwh_per_coin` TEXT, `electricity_meter_start` TEXT, `electricity_meter_end` TEXT, " +
                "`electricity_kwh_used` TEXT, `toll_kind` TEXT, `toll_payment_method` TEXT NOT NULL, " +
                "`toll_country` TEXT, `toll_valid_from` TEXT, `toll_valid_until` TEXT, " +
                "`ferry_booking_reference` TEXT NOT NULL, `services` TEXT NOT NULL, " +
                "`weather_temperature_deci_c` INTEGER, `weather_code` INTEGER, `weather_wind_kmh` INTEGER, " +
                "`weather_gust_kmh` INTEGER, `weather_wind_direction_deg` INTEGER, `weather_observed_at` INTEGER, " +
                "`favorite` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tour_id`) REFERENCES `tours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stations_uuid` ON `stations` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_vehicle_id` ON `stations` (`vehicle_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_tour_id` ON `stations` (`tour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stations_date` ON `stations` (`date`)",
            "CREATE TABLE IF NOT EXISTS `station_costs` (`station_id` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`station_id`, `category`, `currency`), " +
                "FOREIGN KEY(`station_id`) REFERENCES `stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        ),
        inserts = inserts.toList(),
    )

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
