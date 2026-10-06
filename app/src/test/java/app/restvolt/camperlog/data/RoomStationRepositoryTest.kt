package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Prüft den Bordbuch-Abgleich einer Station (Abschnitt 4) gegen eine echte, transaktionale Room-Datenbank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomStationRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var stations: RoomStationRepository
    private lateinit var logs: RoomLogRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var vehicleId = 0L
    private var nextUuid = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logs = RoomLogRepository(db.logDao(), { "log-${nextUuid++}" }) { now }
        stations = RoomStationRepository(db, logs, { "station-${nextUuid++}" }) { now }
        vehicleId = runBlocking {
            db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun station(
        id: Long = 0,
        date: LocalDate = LocalDate.of(2026, 7, 4),
        services: Set<StationService> = emptySet(),
        vehicleId: Long = this.vehicleId,
    ) = Station(
        id = id,
        vehicleId = vehicleId,
        type = StationType.SUPPLY,
        date = date,
        services = services,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun savingWithCassette_createsALinkedLogEntry() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE)))

        val entry = logs.allEntries().single()
        assertEquals(LogType.CASSETTE_EMPTIED, entry.type)
        assertEquals(id, entry.stationId)
        assertEquals(LocalDate.of(2026, 7, 4), entry.date)
    }

    @Test
    fun savingWithCassetteAndGas_createsBothLinkedEntries() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE, StationService.GAS)))

        val types = logs.allEntries().map { it.type }.toSet()
        assertEquals(setOf(LogType.CASSETTE_EMPTIED, LogType.GAS_BOTTLE_SWAPPED), types)
        assertEquals(setOf(id, id), logs.allEntries().map { it.stationId }.toSet())
    }

    @Test
    fun savingWithGreyWater_feedsTheGreyWaterEmptiedType() = runTest {
        stations.save(station(services = setOf(StationService.GREY_WATER)))

        assertEquals(LogType.GREY_WATER_EMPTIED, logs.allEntries().single().type)
    }

    @Test
    fun savingWithAnUnsyncedService_createsNoLogEntry() = runTest {
        stations.save(station(services = setOf(StationService.FRESH_WATER)))

        assertEquals(emptyList<Any>(), logs.allEntries())
    }

    @Test
    fun savingWithCassette_whenAnUnlinkedEntryAlreadyExists_linksItInsteadOfDuplicating() = runTest {
        val existing = logs.add(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 7, 4))

        val id = stations.save(station(services = setOf(StationService.CASSETTE)))

        val entry = logs.allEntries().single()
        assertEquals(existing.id, entry.id)
        assertEquals(existing.uuid, entry.uuid)
        assertEquals(id, entry.stationId)
    }

    @Test
    fun untickingCassette_deletesTheLinkedEntry() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE)))
        assertEquals(1, logs.allEntries().size)

        stations.save(station(id = id, services = emptySet()))

        assertEquals(emptyList<Any>(), logs.allEntries())
    }

    @Test
    fun savingUnchanged_keepsTheSameLinkedEntry() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE)))
        val before = logs.allEntries().single()

        stations.save(station(id = id, services = setOf(StationService.CASSETTE)))

        val after = logs.allEntries().single()
        assertEquals(before.id, after.id)
        assertEquals(before.uuid, after.uuid)
    }

    @Test
    fun movingTheDate_deletesAndRecreatesTheEntryKeepingItsUuid() = runTest {
        val id = stations.save(station(date = LocalDate.of(2026, 7, 4), services = setOf(StationService.CASSETTE)))
        val before = logs.allEntries().single()

        stations.save(station(id = id, date = LocalDate.of(2026, 7, 6), services = setOf(StationService.CASSETTE)))

        val after = logs.allEntries().single()
        assertNotEquals(before.id, after.id)
        assertEquals(before.uuid, after.uuid)
        assertEquals(LocalDate.of(2026, 7, 6), after.date)
        assertEquals(id, after.stationId)
    }

    @Test
    fun movingTheVehicle_movesTheLinkedEntryToo() = runTest {
        val otherVehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-2", createdAtMillis = 0, updatedAtMillis = 0))
        val id = stations.save(station(services = setOf(StationService.CASSETTE)))

        stations.save(station(id = id, services = setOf(StationService.CASSETTE), vehicleId = otherVehicleId))

        val entry = logs.allEntries().single()
        assertEquals(otherVehicleId, entry.vehicleId)
        assertEquals(id, entry.stationId)
    }

    @Test
    fun movingATourToAnotherVehicle_movesItsStationsAndLinkedEntries() = runTest {
        val otherVehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-2", createdAtMillis = 0, updatedAtMillis = 0))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO tours (id, uuid, vehicle_id, start_date, end_date, destination, tour_type, travel_days, " +
                "overnight_stays, distance_km, notes, map_link, created_at, updated_at) VALUES " +
                "(1, 'tour-1', $vehicleId, '2026-07-01', '2026-07-10', 'Lofoten', 'VACATION', 10, 9, 0, '', NULL, 0, 0)",
        )
        stations.save(station(services = setOf(StationService.CASSETTE)).copy(tourId = 1))
        stations.save(station(date = LocalDate.of(2026, 7, 5)).copy(tourId = 1))
        stations.save(station(date = LocalDate.of(2026, 7, 6)))

        stations.moveTourToVehicle(tourId = 1, vehicleId = otherVehicleId)

        val all = stations.allStations()
        assertEquals(listOf(otherVehicleId, otherVehicleId), all.filter { it.tourId == 1L }.map { it.vehicleId })
        assertEquals(vehicleId, all.single { it.tourId == null }.vehicleId)
        assertEquals(otherVehicleId, logs.allEntries().single().vehicleId)
    }

    @Test
    fun movingToADateWithAnExistingUnlinkedEntry_dedupesAndDropsTheOldOne() = runTest {
        val id = stations.save(station(date = LocalDate.of(2026, 7, 4), services = setOf(StationService.CASSETTE)))
        val old = logs.allEntries().single()
        val target = logs.add(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 7, 6))

        stations.save(station(id = id, date = LocalDate.of(2026, 7, 6), services = setOf(StationService.CASSETTE)))

        val entry = logs.allEntries().single()
        assertEquals(target.id, entry.id)
        assertEquals(id, entry.stationId)
        assertEquals(null, logs.findUnlinked(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 7, 4)))
        assertNotEquals(old.id, entry.id)
    }

    @Test
    fun deletingTheStation_keepsItsLinkedEntryButUnlinksIt() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE)))

        stations.delete(id)

        val entry = logs.allEntries().single()
        assertNull(entry.stationId)
    }

    @Test
    fun linkedLogEntriesAndRelink_roundTripForUndo() = runTest {
        val id = stations.save(station(services = setOf(StationService.CASSETTE, StationService.GAS)))
        val saved = checkNotNull(db.stationDao().getById(id)).toDomain()
        val linked = stations.linkedLogEntries(id)
        assertEquals(2, linked.size)

        stations.delete(id)
        assertEquals(listOf(null, null), logs.allEntries().map { it.stationId })

        stations.restore(saved)
        stations.relinkLogEntries(linked.map { it.id }, id)
        assertEquals(setOf(id), logs.allEntries().map { it.stationId }.toSet())
    }
}
