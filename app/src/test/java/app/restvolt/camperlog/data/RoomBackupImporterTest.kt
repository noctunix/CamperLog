package app.restvolt.camperlog.data

import android.content.Context
import android.database.SQLException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupReadResult
import app.restvolt.camperlog.backup.BackupVehicle
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.backup.decodeBackup
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomBackupImporterTest {

    private val nok = Currency.getInstance("NOK")
    private val sek = Currency.getInstance("SEK")

    private lateinit var db: CamperLogDatabase
    private lateinit var tours: RoomTourRepository
    private lateinit var rates: RoomExchangeRateRepository
    private lateinit var vehicles: RoomVehicleRepository
    private lateinit var logs: RoomLogRepository
    private lateinit var importer: RoomBackupImporter
    private var vehicleId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tours = RoomTourRepository(db.tourDao(), db.stationDao(), db.vehicleDao())
        rates = RoomExchangeRateRepository(db.exchangeRateDao())
        vehicles = RoomVehicleRepository(db.vehicleDao())
        logs = RoomLogRepository(db.logDao())
        importer = RoomBackupImporter(db, Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC))
        vehicleId = runBlocking {
            db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private fun vehicleUuid(n: Int) = "10000000-0000-4000-8000-%012d".format(n)
    private fun repairUuid(n: Int) = "20000000-0000-4000-8000-%012d".format(n)
    private fun logEntryUuid(n: Int) = "30000000-0000-4000-8000-%012d".format(n)
    private fun stationUuid(n: Int) = "40000000-0000-4000-8000-%012d".format(n)

    private fun tour(n: Int, destination: String = "Ziel $n", updatedAt: String = "2026-07-10T10:00:00Z") = Tour(
        uuid = uuid(n),
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 7, n),
        endDate = LocalDate.of(2026, 7, n + 1),
        destination = destination,
        tourType = TourType.VACATION,
        travelDays = 2,
        overnightStays = 1,
        distanceKm = 100,
        costs = listOf(Money(1000, EUR), Money(5000, nok)),
        notes = "",
        mapLink = null,
        createdAt = Instant.parse("2026-07-01T10:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )

    private fun vehicle(n: Int, name: String = "Fahrzeug $n", updatedAt: String = "2026-07-01T10:00:00Z") = Vehicle(
        uuid = vehicleUuid(n),
        name = name,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )

    private fun repair(n: Int, description: String = "Reparatur $n", updatedAt: String = "2026-07-01T10:00:00Z") = Repair(
        uuid = repairUuid(n),
        vehicleId = 0,
        date = LocalDate.of(2026, 6, n.coerceIn(1, 28)),
        description = description,
        createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )

    private fun logEntry(n: Int) = LogEntry(
        uuid = logEntryUuid(n),
        vehicleId = 0,
        type = LogType.CASSETTE_EMPTIED,
        date = LocalDate.of(2026, 6, n.coerceIn(1, 28)),
        createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )

    private fun rate(currency: Currency, perEuro: String, date: String) =
        ExchangeRate(currency, BigDecimal(perEuro), LocalDate.parse(date), "EZB")

    private fun station(n: Int, name: String = "Platz $n", updatedAt: String = "2026-07-01T10:00:00Z") = Station(
        uuid = stationUuid(n),
        vehicleId = 0,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, n.coerceIn(1, 28)),
        name = name,
        nights = 1,
        createdAt = Instant.parse("2026-07-01T10:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )

    private fun backup(
        tours: List<Tour>,
        rates: List<ExchangeRate> = emptyList(),
        main: Currency = sek,
        vehicles: List<BackupVehicle> = emptyList(),
        tourVehicleUuid: Map<String, String> = emptyMap(),
        currentVehicleUuid: String? = null,
        stations: List<Station> = emptyList(),
        stationVehicleUuid: Map<String, String> = emptyMap(),
        stationTourUuid: Map<String, String> = emptyMap(),
        logEntryStationUuid: Map<String, String> = emptyMap(),
    ) = Backup(
        Instant.parse("2026-10-04T12:00:00Z"), main, rates, tours, tourVehicleUuid, vehicles, currentVehicleUuid,
        stations, stationVehicleUuid, stationTourUuid, logEntryStationUuid,
    )

    /** Gespeicherte Touren ohne Datenbank-id, damit sie mit Sicherungs-Touren vergleichbar sind. */
    private suspend fun storedTours() = tours.allTours().map { it.copy(id = 0) }

    /** Gespeicherte Stationen ohne Datenbank-id, damit sie mit Sicherungs-Stationen vergleichbar sind. */
    private suspend fun storedStations() = RoomStationRepository(db, RoomLogRepository(db.logDao())).allStations().map { it.copy(id = 0) }

    private suspend fun seed(vararg seeded: Tour) = seeded.forEach { tours.restore(it) }

    @Test
    fun merge_addsNewTours_updatesNewer_keepsNewerOrEqualLocal() = runTest {
        seed(
            tour(1, "Lokal alt", updatedAt = "2026-07-10T10:00:00Z"),
            tour(2, "Lokal neu", updatedAt = "2026-07-20T10:00:00Z"),
            tour(3, "Lokal gleich"),
        )

        val result = importer.import(
            backup(
                listOf(
                    tour(1, "Import neu", updatedAt = "2026-07-15T10:00:00Z"),
                    tour(2, "Import alt", updatedAt = "2026-07-15T10:00:00Z"),
                    tour(3, "Import gleich"),
                    tour(4, "Import zusätzlich"),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(ImportResult(addedTours = 1, updatedTours = 1, unchangedTours = 2, importedRates = 0), result)
        assertEquals(
            listOf("Import neu", "Lokal neu", "Lokal gleich", "Import zusätzlich"),
            storedTours().map { it.destination },
        )
        assertEquals(tour(1, "Import neu", updatedAt = "2026-07-15T10:00:00Z"), storedTours().first())
    }

    @Test
    fun merge_assignsCurrentVehicleToToursWithoutVehicleId() = runTest {
        // Sicherungen aus Version 1 kennen noch keine Fahrzeuge; ihre Touren tragen vehicleId 0.
        val result = importer.import(backup(listOf(tour(1).copy(vehicleId = 0))), ImportMode.MERGE)

        assertEquals(ImportResult(addedTours = 1, updatedTours = 0, unchangedTours = 0, importedRates = 0), result)
        assertEquals(vehicleId, tours.allTours().single().vehicleId)
    }

    @Test
    fun merge_updateKeepsLocalIdAndReplacesCosts() = runTest {
        seed(tour(1))
        val id = tours.allTours().single().id
        val newer = tour(1, updatedAt = "2026-08-01T00:00:00Z").copy(costs = listOf(Money(42, sek)))

        importer.import(backup(listOf(newer)), ImportMode.MERGE)

        val stored = tours.allTours().single()
        assertEquals(id, stored.id)
        assertEquals(listOf(Money(42, sek)), stored.costs)
    }

    @Test
    fun merge_takesOnlyNewerRates_andKeepsMainCurrency() = runTest {
        rates.setMainCurrency(nok)
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        rates.saveRate(rate(sek, "11.0", "2026-10-01"))

        val result = importer.import(
            backup(
                emptyList(),
                rates = listOf(rate(nok, "11.7", "2026-09-15"), rate(sek, "10.0", "2026-09-01")),
                main = sek,
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.importedRates)
        assertEquals(listOf("11.7", "11.0"), rates.observeRates().first().map { it.perEuro.toPlainString() })
        assertEquals(nok, rates.observeMainCurrency().first())
    }

    @Test
    fun merge_vehicles_addsNewUpdatesNewerKeepsOlderOrEqualUnchanged() = runTest {
        val idA = db.vehicleDao().insert(vehicle(1, name = "Lokal A", updatedAt = "2026-01-01T00:00:00Z").toEntity())
        val idB = db.vehicleDao().insert(vehicle(2, name = "Lokal B", updatedAt = "2026-09-01T00:00:00Z").toEntity())

        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(
                    BackupVehicle(vehicle(1, name = "Import A", updatedAt = "2026-02-01T00:00:00Z"), emptyList(), emptyList()),
                    BackupVehicle(vehicle(2, name = "Import B", updatedAt = "2026-01-01T00:00:00Z"), emptyList(), emptyList()),
                    BackupVehicle(vehicle(3, name = "Import C"), emptyList(), emptyList()),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedVehicles)
        assertEquals(1, result.updatedVehicles)
        val byUuid = vehicles.allVehicles().associateBy { it.uuid }
        assertEquals(idA, byUuid.getValue(vehicleUuid(1)).id)
        assertEquals("Import A", byUuid.getValue(vehicleUuid(1)).name)
        assertEquals(idB, byUuid.getValue(vehicleUuid(2)).id)
        assertEquals("Lokal B", byUuid.getValue(vehicleUuid(2)).name)
        assertEquals("Import C", byUuid.getValue(vehicleUuid(3)).name)
        assertTrue(byUuid.containsKey("vehicle-1"))
    }

    @Test
    fun merge_repairs_addsNewUpdatesNewerKeepsOlderOrEqualUnchanged() = runTest {
        db.vehicleDao().insertRepair(
            repair(1, description = "Lokal", updatedAt = "2026-01-01T00:00:00Z").copy(vehicleId = vehicleId).toEntity(),
        )

        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(
                    BackupVehicle(
                        vehicle = Vehicle(uuid = "vehicle-1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
                        repairs = listOf(
                            repair(1, description = "Import neu", updatedAt = "2026-02-01T00:00:00Z"),
                            repair(2, description = "Import zusätzlich"),
                        ),
                        logEntries = emptyList(),
                    ),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedRepairs)
        assertEquals(1, result.updatedRepairs)
        val stored = vehicles.allRepairs().associateBy { it.uuid }
        assertEquals("Import neu", stored.getValue(repairUuid(1)).description)
        assertTrue(stored.containsKey(repairUuid(2)))
    }

    @Test
    fun merge_logEntries_insertsUnknownAndSkipsKnown() = runTest {
        db.logDao().insert(
            LogEntry(uuid = logEntryUuid(1), vehicleId = vehicleId, type = LogType.CASSETTE_EMPTIED, date = LocalDate.of(2026, 5, 1), createdAt = Instant.EPOCH)
                .toEntity(),
        )

        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(
                    BackupVehicle(
                        vehicle = Vehicle(uuid = "vehicle-1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
                        repairs = emptyList(),
                        logEntries = listOf(logEntry(1), logEntry(2)),
                    ),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedLogEntries)
        val stored = logs.allEntries().associateBy { it.uuid }
        assertEquals(LocalDate.of(2026, 5, 1), stored.getValue(logEntryUuid(1)).date)
        assertTrue(stored.containsKey(logEntryUuid(2)))
    }

    @Test
    fun merge_mapsTourToItsVehicleByUuid() = runTest {
        val result = importer.import(
            backup(
                tours = listOf(tour(10).copy(vehicleId = 0)),
                vehicles = listOf(BackupVehicle(vehicle(10), emptyList(), emptyList())),
                tourVehicleUuid = mapOf(uuid(10) to vehicleUuid(10)),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedVehicles)
        assertEquals(1, result.addedTours)
        val localVehicleId = vehicles.allVehicles().single { it.uuid == vehicleUuid(10) }.id
        assertEquals(localVehicleId, tours.allTours().single { it.uuid == uuid(10) }.vehicleId)
    }

    @Test
    fun merge_replacesUntouchedPlaceholderVehicleOfAFreshInstall() = runTest {
        val result = importer.import(
            backup(
                tours = listOf(tour(10).copy(vehicleId = 0)),
                vehicles = listOf(BackupVehicle(vehicle(10), emptyList(), emptyList()), BackupVehicle(vehicle(11), emptyList(), emptyList())),
                tourVehicleUuid = mapOf(uuid(10) to vehicleUuid(10)),
                currentVehicleUuid = vehicleUuid(11),
            ),
            ImportMode.MERGE,
        )

        assertEquals(2, result.addedVehicles)
        assertEquals(listOf(vehicleUuid(10), vehicleUuid(11)), vehicles.allVehicles().map { it.uuid }.sorted())
        assertEquals(vehicleUuid(11), vehicles.observeCurrentVehicle().first().uuid)
    }

    @Test
    fun merge_keepsLocalVehicleThatHasTours() = runTest {
        seed(tour(1))

        importer.import(backup(tours = emptyList(), vehicles = listOf(BackupVehicle(vehicle(10), emptyList(), emptyList()))), ImportMode.MERGE)

        assertEquals(2, vehicles.allVehicles().size)
    }

    @Test
    fun merge_keepsLocalVehicleWithDetails() = runTest {
        vehicles.save(vehicles.allVehicles().single().copy(licensePlate = "M-CL 1"))

        importer.import(backup(tours = emptyList(), vehicles = listOf(BackupVehicle(vehicle(10), emptyList(), emptyList()))), ImportMode.MERGE)

        assertEquals(2, vehicles.allVehicles().size)
    }

    @Test
    fun merge_keepsPlaceholderWhenTheBackupContainsIt() = runTest {
        val local = vehicles.allVehicles().single()

        importer.import(backup(tours = emptyList(), vehicles = listOf(BackupVehicle(local, emptyList(), emptyList()))), ImportMode.MERGE)

        assertEquals(listOf(local.id), vehicles.allVehicles().map { it.id })
    }

    @Test
    fun merge_doesNotChangeCurrentVehicle() = runTest {
        vehicles.save(vehicles.allVehicles().single().copy(name = "Eigenes"))
        db.vehicleDao().setCurrentVehicleId(vehicleId)

        importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(9), emptyList(), emptyList())),
                currentVehicleUuid = vehicleUuid(9),
            ),
            ImportMode.MERGE,
        )

        assertEquals(vehicleId, db.vehicleDao().getCurrentVehicleId())
    }

    @Test
    fun replace_removesEverythingNotInBackup() = runTest {
        seed(tour(1, "Lokal"), tour(2))
        rates.setMainCurrency(nok)
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        val imported = listOf(tour(2, "Import", updatedAt = "2026-01-01T00:00:00Z"), tour(5))

        val result = importer.import(backup(imported, rates = listOf(rate(sek, "11.2", "2026-01-01"))), ImportMode.REPLACE)

        assertEquals(ImportResult(addedTours = 2, updatedTours = 0, unchangedTours = 0, importedRates = 1), result)
        val newVehicleId = vehicles.allVehicles().single().id
        assertEquals(imported.map { it.copy(vehicleId = newVehicleId) }, storedTours())
        assertEquals(listOf(sek), rates.observeRates().first().map { it.currency })
        assertEquals(sek, rates.observeMainCurrency().first())
    }

    @Test
    fun replace_v1Backup_keepsExactlyOneVehicleAndAssignsAllToursToIt() = runTest {
        val result = importer.import(backup(listOf(tour(1).copy(vehicleId = 0), tour(2).copy(vehicleId = 0))), ImportMode.REPLACE)

        assertEquals(2, result.addedTours)
        val allVehicles = vehicles.allVehicles()
        assertEquals(1, allVehicles.size)
        val onlyVehicleId = allVehicles.single().id
        assertTrue(tours.allTours().all { it.vehicleId == onlyVehicleId })
        assertEquals(onlyVehicleId, db.vehicleDao().getCurrentVehicleId())
    }

    @Test
    fun replace_v2Backup_replacesVehiclesRepairsLogEntriesAndSetsCurrentVehicle() = runTest {
        db.vehicleDao().insertRepair(
            Repair(uuid = "old-repair", vehicleId = vehicleId, date = LocalDate.of(2020, 1, 1), description = "Alt", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
                .toEntity(),
        )
        db.logDao().insert(
            LogEntry(uuid = "old-log", vehicleId = vehicleId, type = LogType.GREY_WATER_EMPTIED, date = LocalDate.of(2020, 1, 1), createdAt = Instant.EPOCH)
                .toEntity(),
        )

        val result = importer.import(
            backup(
                tours = listOf(tour(1).copy(vehicleId = 0)),
                vehicles = listOf(
                    BackupVehicle(vehicle(1, name = "Erstes"), listOf(repair(1)), listOf(logEntry(1))),
                    BackupVehicle(vehicle(2, name = "Zweites"), emptyList(), emptyList()),
                ),
                tourVehicleUuid = mapOf(uuid(1) to vehicleUuid(2)),
                currentVehicleUuid = vehicleUuid(2),
            ),
            ImportMode.REPLACE,
        )

        assertEquals(2, result.addedVehicles)
        assertEquals(1, result.addedRepairs)
        assertEquals(1, result.addedLogEntries)
        val allVehicles = vehicles.allVehicles()
        assertEquals(setOf(vehicleUuid(1), vehicleUuid(2)), allVehicles.map { it.uuid }.toSet())
        val vehicle2Id = allVehicles.single { it.uuid == vehicleUuid(2) }.id
        assertEquals(vehicle2Id, db.vehicleDao().getCurrentVehicleId())
        assertEquals(vehicle2Id, tours.allTours().single().vehicleId)
        assertEquals(1, vehicles.allRepairs().size)
        assertEquals(1, logs.allEntries().size)
    }

    @Test
    fun failure_rollsBackEverything() = runTest {
        seed(tour(1, "Lokal"))
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        // Gleiche UUID zweimal verletzt den eindeutigen Index; decodeBackup würde das vorher ablehnen.
        val broken = backup(listOf(tour(7), tour(7)), rates = listOf(rate(sek, "11.2", "2026-01-01")))

        val failed = runCatching { importer.import(broken, ImportMode.REPLACE) }

        assertTrue(failed.isFailure)
        assertEquals(listOf("Lokal"), storedTours().map { it.destination })
        assertEquals(listOf(nok), rates.observeRates().first().map { it.currency })
    }

    @Test
    fun failure_rollsBackEverything_leavesVehiclesIntact() = runTest {
        seed(tour(1, "Lokal"))
        db.vehicleDao().insert(vehicle(1, name = "Behalten").toEntity())
        // Zwei Fahrzeuge mit derselben UUID verletzen den eindeutigen Index; decodeBackup würde das vorher ablehnen.
        val broken = backup(
            listOf(tour(7)),
            vehicles = listOf(
                BackupVehicle(vehicle(9), emptyList(), emptyList()),
                BackupVehicle(vehicle(9), emptyList(), emptyList()),
            ),
        )

        val failed = runCatching { importer.import(broken, ImportMode.MERGE) }

        assertTrue(failed.isFailure)
        assertEquals(listOf("Lokal"), storedTours().map { it.destination })
        assertEquals(setOf("vehicle-1", vehicleUuid(1)), vehicles.allVehicles().map { it.uuid }.toSet())
    }

    @Test
    fun futureTimestampsAndRateDates_areCappedAtImportTime() = runTest {
        val future = tour(1, "Zukunft", updatedAt = "2150-01-01T00:00:00Z").copy(createdAt = Instant.parse("2150-01-01T00:00:00Z"))

        importer.import(backup(listOf(future), rates = listOf(rate(nok, "11.5", "2150-01-01"))), ImportMode.MERGE)

        val stored = storedTours().single()
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), stored.updatedAt)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), stored.createdAt)
        assertEquals(LocalDate.of(2026, 10, 4), rates.observeRates().first().single().date)

        // Eine spätere lokale Änderung gewinnt beim erneuten Zusammenführen.
        val local = tours.allTours().single()
        tours.delete(local.id)
        tours.restore(local.copy(destination = "Lokal", updatedAt = Instant.parse("2026-10-05T00:00:00Z")))
        importer.import(backup(listOf(future)), ImportMode.MERGE)
        assertEquals(listOf("Lokal"), storedTours().map { it.destination })
    }

    @Test
    fun futureVehicleRepairAndLogEntryDates_areCappedAtImportTime() = runTest {
        val futureVehicle = vehicle(1, updatedAt = "2150-01-01T00:00:00Z").copy(createdAt = Instant.parse("2150-01-01T00:00:00Z"))
        val futureRepair = repair(1, updatedAt = "2150-01-01T00:00:00Z").copy(
            date = LocalDate.of(2150, 1, 1),
            createdAt = Instant.parse("2150-01-01T00:00:00Z"),
        )
        val futureLogEntry = logEntry(1).copy(date = LocalDate.of(2150, 1, 1), createdAt = Instant.parse("2150-01-01T00:00:00Z"))

        importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(futureVehicle, listOf(futureRepair), listOf(futureLogEntry))),
            ),
            ImportMode.MERGE,
        )

        val storedVehicle = vehicles.allVehicles().single { it.uuid == vehicleUuid(1) }
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), storedVehicle.createdAt)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), storedVehicle.updatedAt)
        val storedRepair = vehicles.allRepairs().single()
        assertEquals(LocalDate.of(2026, 10, 4), storedRepair.date)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), storedRepair.createdAt)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), storedRepair.updatedAt)
        val storedLogEntry = logs.allEntries().single()
        assertEquals(LocalDate.of(2026, 10, 4), storedLogEntry.date)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), storedLogEntry.createdAt)
    }

    @Test
    fun overflowingCostSum_isRejectedAndRolledBack() = runTest {
        seed(tour(1, "Lokal").copy(costs = listOf(Money(Long.MAX_VALUE - 10, sek))))

        val failed = runCatching {
            importer.import(backup(listOf(tour(2).copy(costs = listOf(Money(100, sek))))), ImportMode.MERGE)
        }

        assertTrue(failed.exceptionOrNull() is SQLException)
        assertEquals(listOf("Lokal"), storedTours().map { it.destination })
    }

    @Test
    fun merge_stations_addsNewUpdatesNewerKeepsOlderOrEqualUnchanged() = runTest {
        val local = RoomStationRepository(db, RoomLogRepository(db.logDao()))
        local.restore(station(1, name = "Lokal alt", updatedAt = "2026-07-10T10:00:00Z").copy(vehicleId = vehicleId))
        local.restore(station(2, name = "Lokal neu", updatedAt = "2026-07-20T10:00:00Z").copy(vehicleId = vehicleId))

        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), emptyList())),
                stations = listOf(
                    station(1, name = "Import neu", updatedAt = "2026-07-15T10:00:00Z"),
                    station(2, name = "Import alt", updatedAt = "2026-07-15T10:00:00Z"),
                    station(3, name = "Import zusätzlich"),
                ),
                stationVehicleUuid = mapOf(
                    stationUuid(1) to vehicleUuid(1),
                    stationUuid(2) to vehicleUuid(1),
                    stationUuid(3) to vehicleUuid(1),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedStations)
        assertEquals(1, result.updatedStations)
        assertEquals(
            setOf("Import neu", "Lokal neu", "Import zusätzlich"),
            storedStations().map { it.name }.toSet(),
        )
    }

    @Test
    fun merge_resolvesStationVehicleAndTourFromTheirUuids() = runTest {
        val result = importer.import(
            backup(
                tours = listOf(tour(1).copy(vehicleId = 0)),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), emptyList())),
                tourVehicleUuid = mapOf(uuid(1) to vehicleUuid(1)),
                stations = listOf(station(1)),
                stationVehicleUuid = mapOf(stationUuid(1) to vehicleUuid(1)),
                stationTourUuid = mapOf(stationUuid(1) to uuid(1)),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedStations)
        val storedStation = storedStations().single()
        val importedVehicleId = vehicles.allVehicles().single { it.uuid == vehicleUuid(1) }.id
        assertEquals(importedVehicleId, storedStation.vehicleId)
        assertEquals(tours.allTours().single().id, storedStation.tourId)
    }

    @Test
    fun merge_resolvesLegacyStationVehicleFromItsTourWhenVehicleUuidIsAbsent() = runTest {
        // Aus alten Stellplatz-Feldern abgeleitete Stationen tragen keine eigene Fahrzeug-UUID.
        val result = importer.import(
            backup(
                tours = listOf(tour(1)),
                stations = listOf(station(1)),
                stationTourUuid = mapOf(stationUuid(1) to uuid(1)),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedStations)
        val storedStation = storedStations().single()
        assertEquals(vehicleId, storedStation.vehicleId)
        assertEquals(tours.allTours().single().id, storedStation.tourId)
    }

    @Test
    fun merge_resolvesLogEntryStationLinkByUuid() = runTest {
        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), listOf(logEntry(1)))),
                stations = listOf(station(1)),
                stationVehicleUuid = mapOf(stationUuid(1) to vehicleUuid(1)),
                logEntryStationUuid = mapOf(logEntryUuid(1) to stationUuid(1)),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.addedStations)
        assertEquals(1, result.addedLogEntries)
        val storedStation = RoomStationRepository(db, logs).allStations().single()
        val importedEntry = logs.allEntries().single { it.uuid == logEntryUuid(1) }
        assertEquals(storedStation.id, importedEntry.stationId)
    }

    @Test
    fun merge_logEntryWithoutAStationLink_staysUnlinked() = runTest {
        importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), listOf(logEntry(1)))),
            ),
            ImportMode.MERGE,
        )

        assertEquals(null, logs.allEntries().single { it.uuid == logEntryUuid(1) }.stationId)
    }

    @Test
    fun replace_resolvesLogEntryStationLinkByUuid() = runTest {
        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), listOf(logEntry(1)))),
                stations = listOf(station(1)),
                stationVehicleUuid = mapOf(stationUuid(1) to vehicleUuid(1)),
                logEntryStationUuid = mapOf(logEntryUuid(1) to stationUuid(1)),
            ),
            ImportMode.REPLACE,
        )

        assertEquals(1, result.addedStations)
        val storedStation = RoomStationRepository(db, logs).allStations().single()
        val importedEntry = logs.allEntries().single { it.uuid == logEntryUuid(1) }
        assertEquals(storedStation.id, importedEntry.stationId)
    }

    @Test
    fun replace_stations_removesEverythingNotInBackup() = runTest {
        RoomStationRepository(db, RoomLogRepository(db.logDao())).restore(station(1, name = "Lokal").copy(vehicleId = vehicleId))

        val result = importer.import(
            backup(
                tours = emptyList(),
                vehicles = listOf(BackupVehicle(vehicle(1), emptyList(), emptyList())),
                stations = listOf(station(2, name = "Import")),
                stationVehicleUuid = mapOf(stationUuid(2) to vehicleUuid(1)),
            ),
            ImportMode.REPLACE,
        )

        assertEquals(1, result.addedStations)
        assertEquals(listOf("Import"), storedStations().map { it.name })
    }

    @Test
    fun legacyPitchFromAnOldTour_isImportedAsOneOvernightStation() = runTest {
        val text = """
            {
              "format": "camperlog-backup",
              "schemaVersion": 1,
              "exportedAt": "2026-10-04T12:00:00Z",
              "mainCurrency": "SEK",
              "exchangeRates": [],
              "tours": [
                {
                  "uuid": "${uuid(9)}",
                  "startDate": "2026-07-09",
                  "endDate": "2026-07-22",
                  "destination": "Lofoten",
                  "tourType": "VACATION",
                  "travelDays": 14,
                  "overnightStays": 13,
                  "distanceKm": 3420,
                  "costs": [],
                  "pitchAssigned": true,
                  "electricityFlatRate": "YES",
                  "lteQuality": "GOOD",
                  "pitchSlope": "LEVEL",
                  "levelingBlocksUsed": false,
                  "notes": "",
                  "mapLink": null,
                  "createdAt": "2026-07-23T08:00:00Z",
                  "updatedAt": "2026-07-23T08:00:00Z"
                }
              ]
            }
        """.trimIndent()
        val decoded = (decodeBackup(text) as BackupReadResult.Success).backup

        val result = importer.import(decoded, ImportMode.MERGE)

        assertEquals(1, result.addedTours)
        assertEquals(1, result.addedStations)
        val storedTour = tours.allTours().single()
        val storedStation = storedStations().single()
        assertEquals(storedTour.id, storedStation.tourId)
        assertEquals(storedTour.vehicleId, storedStation.vehicleId)
        assertEquals(13, storedStation.nights)
        assertEquals("Lofoten", storedStation.name)
    }
}
