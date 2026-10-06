package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLogRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomLogRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var vehicleId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLogRepository(db) { now }
        vehicleId = runBlocking {
            db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun hasEntriesReflectsWhetherAnyEntryIsStored() = runTest {
        assertFalse(repository.hasEntries())

        repository.add(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 1))

        assertTrue(repository.hasEntries())
    }

    @Test
    fun observeLatestHasOneDatePerTypeAndIgnoresOtherVehicles() = runTest {
        val otherVehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-2", createdAtMillis = 0, updatedAtMillis = 0))
        repository.add(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 1))
        repository.add(vehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 10))
        repository.add(vehicleId, LogType.GREY_WATER_EMPTIED, LocalDate.of(2026, 1, 5))
        repository.add(otherVehicleId, LogType.CASSETTE_EMPTIED, LocalDate.of(2026, 1, 20))

        val latest = repository.observeLatest(vehicleId).first()

        assertEquals(LocalDate.of(2026, 1, 10), latest[LogType.CASSETTE_EMPTIED])
        assertEquals(LocalDate.of(2026, 1, 5), latest[LogType.GREY_WATER_EMPTIED])
        assertEquals(null, latest[LogType.DIESEL_HEATER_RUN])
    }

    @Test
    fun observeEntriesIsNewestFirstAndDeleteRestoreWork() = runTest {
        val first = repository.add(vehicleId, LogType.GAS_HEATER_RUN, LocalDate.of(2026, 1, 1))
        val second = repository.add(vehicleId, LogType.GAS_HEATER_RUN, LocalDate.of(2026, 2, 1))

        assertEquals(listOf(second, first).map { it.id }, repository.observeEntries(vehicleId, LogType.GAS_HEATER_RUN).first().map { it.id })
        assertEquals(4, UUID.fromString(first.uuid).version())

        repository.delete(second.id)
        assertEquals(listOf(first.id), repository.observeEntries(vehicleId, LogType.GAS_HEATER_RUN).first().map { it.id })

        repository.restore(second)
        assertEquals(
            listOf(second.id, first.id),
            repository.observeEntries(vehicleId, LogType.GAS_HEATER_RUN).first().map { it.id },
        )
    }
}
