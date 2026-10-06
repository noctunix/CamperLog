package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDeleteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomVehicleRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomVehicleRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomVehicleRepository(db.vehicleDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeCurrentVehicleAutoCreatesADefaultVehicleOnFirstAccess() = runTest {
        val current = repository.observeCurrentVehicle().first()

        assertEquals("", current.name)
        assertEquals(4, UUID.fromString(current.uuid).version())
        assertEquals(listOf(current), repository.observeVehicles().first())
    }

    @Test
    fun concurrentFirstAccessCreatesOnlyOneVehicle() = runTest {
        val currents = withContext(Dispatchers.Default) {
            List(8) { async { repository.observeCurrentVehicle().first() } }.awaitAll()
        }

        assertEquals(1, repository.observeVehicles().first().size)
        assertEquals(1, currents.map { it.id }.distinct().size)
    }

    @Test
    fun observeCurrentVehicleFallsBackToLowestIdWhenSelectionIsUnset() = runTest {
        val first = repository.save(vehicle(name = "A"))
        val second = repository.save(vehicle(name = "B"))

        assertEquals(first, repository.observeCurrentVehicle().first().id)

        repository.setCurrentVehicle(second)
        assertEquals(second, repository.observeCurrentVehicle().first().id)
    }

    @Test
    fun observeCurrentVehicleFallsBackWhenSelectedVehicleIsGone() = runTest {
        val first = repository.save(vehicle(name = "A"))
        val second = repository.save(vehicle(name = "B"))
        repository.setCurrentVehicle(second)

        repository.delete(second)

        assertEquals(first, repository.observeCurrentVehicle().first().id)
    }

    @Test
    fun observeVehiclesListsNotSoldFirstThenByName() = runTest {
        repository.save(vehicle(name = "Z", saleDate = LocalDate.of(2026, 1, 1)))
        repository.save(vehicle(name = "B"))
        repository.save(vehicle(name = "A"))

        assertEquals(listOf("A", "B", "Z"), repository.observeVehicles().first().map { it.name })
    }

    @Test
    fun saveSetsUuidAndTimestampsAndUpdateKeepsCreatedAt() = runTest {
        val id = repository.save(vehicle(name = "Womo"))
        val stored = checkNotNull(repository.observeVehicle(id).first())
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(4, UUID.fromString(stored.uuid).version())

        val created = now
        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.save(stored.copy(name = "Womo 2"))
        val updated = checkNotNull(repository.observeVehicle(id).first())
        assertEquals("Womo 2", updated.name)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun deleteRefusesTheLastVehicleAndOneWithTours() = runTest {
        val onlyId = repository.save(vehicle(name = "Einzig"))
        assertEquals(VehicleDeleteResult.LAST_VEHICLE, repository.delete(onlyId))

        val secondId = repository.save(vehicle(name = "Zweites"))
        addTourFor(onlyId)
        assertEquals(VehicleDeleteResult.HAS_TOURS_OR_STATIONS, repository.delete(onlyId))

        assertEquals(VehicleDeleteResult.DELETED, repository.delete(secondId))
        assertNull(repository.observeVehicle(secondId).first())
    }

    @Test
    fun deleteRefusesAVehicleWithStationsButWithoutTours() = runTest {
        val onlyId = repository.save(vehicle(name = "Einzig"))
        val secondId = repository.save(vehicle(name = "Zweites"))
        addStationFor(onlyId)

        assertEquals(VehicleDeleteResult.HAS_TOURS_OR_STATIONS, repository.delete(onlyId))
        assertEquals(VehicleDeleteResult.DELETED, repository.delete(secondId))
    }

    @Test
    fun observeRepairsIsNewestFirstAndSaveDeleteRestoreWork() = runTest {
        val vehicleId = repository.save(vehicle(name = "Womo"))
        val first = repository.saveRepair(repair(vehicleId, "2026-01-01", "Reifen"))
        val second = repository.saveRepair(repair(vehicleId, "2026-03-01", "Bremsen"))

        assertEquals(listOf("Bremsen", "Reifen"), repository.observeRepairs(vehicleId).first().map { it.description })

        val stored = repository.observeRepairs(vehicleId).first().first { it.id == second }
        repository.deleteRepair(second)
        assertEquals(listOf("Reifen"), repository.observeRepairs(vehicleId).first().map { it.description })

        repository.restoreRepair(stored)
        assertEquals(listOf("Bremsen", "Reifen"), repository.observeRepairs(vehicleId).first().map { it.description })
        assertEquals(first, repository.observeRepairs(vehicleId).first().last().id)
    }

    @Test
    fun lastUsedRepairCurrencyComesFromTheMostRecentlyChangedRepairWithCost() = runTest {
        assertNull(repository.lastUsedRepairCurrency())
        val vehicleId = repository.save(vehicle(name = "Womo"))

        now = Instant.parse("2026-01-01T10:00:00Z")
        repository.saveRepair(repair(vehicleId, "2026-01-01", "Reifen").copy(cost = Money(10_000, Currency.getInstance("NOK"))))
        assertEquals(Currency.getInstance("NOK"), repository.lastUsedRepairCurrency())

        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.saveRepair(repair(vehicleId, "2026-02-01", "Ohne Kosten"))
        assertEquals(Currency.getInstance("NOK"), repository.lastUsedRepairCurrency())

        now = Instant.parse("2026-03-01T10:00:00Z")
        repository.saveRepair(repair(vehicleId, "2026-03-01", "Bremsen").copy(cost = Money(5_000, Currency.getInstance("ISK"))))
        assertEquals(Currency.getInstance("ISK"), repository.lastUsedRepairCurrency())
    }

    @Test
    fun settingMainCurrencyAndCurrentVehicleDoNotResetEachOther() = runTest {
        val rates = RoomExchangeRateRepository(db.exchangeRateDao())
        val first = repository.save(vehicle(name = "A"))
        val second = repository.save(vehicle(name = "B"))

        rates.setMainCurrency(Currency.getInstance("NOK"))
        repository.setCurrentVehicle(second)
        assertEquals(Currency.getInstance("NOK"), rates.observeMainCurrency().first())
        assertEquals(second, repository.observeCurrentVehicle().first().id)

        rates.setMainCurrency(Currency.getInstance("DKK"))
        assertEquals(Currency.getInstance("DKK"), rates.observeMainCurrency().first())
        assertEquals(second, repository.observeCurrentVehicle().first().id)

        repository.setCurrentVehicle(first)
        assertEquals(first, repository.observeCurrentVehicle().first().id)
        assertEquals(Currency.getInstance("DKK"), rates.observeMainCurrency().first())
    }

    private suspend fun addTourFor(vehicleId: Long) {
        db.tourDao().insertWithCosts(
            TourEntity(
                id = 0,
                uuid = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                startDate = "2026-05-01",
                endDate = "2026-05-01",
                destination = "Ziel",
                tourType = TourType.DAY_TRIP.name,
                travelDays = 1,
                overnightStays = 0,
                distanceKm = 10,
                notes = "",
                mapLink = null,
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ),
            emptyList(),
        )
    }

    private suspend fun addStationFor(vehicleId: Long) {
        db.stationDao().insert(
            StationEntity(
                uuid = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                tourId = null,
                type = "OTHER",
                date = "2026-05-01",
                time = null,
                name = "",
                place = "",
                latitude = null,
                longitude = null,
                coordinateSource = null,
                accuracyM = null,
                mapLink = null,
                notes = "",
                nights = null,
                siteKind = null,
                pitchAssigned = null,
                lteQuality = null,
                pitchSlope = null,
                levelingBlocksUsed = null,
                services = "",
                weatherTemperatureDeciC = null,
                weatherCode = null,
                weatherWindKmh = null,
                weatherGustKmh = null,
                weatherWindDirectionDeg = null,
                weatherObservedAtMillis = null,
                favorite = false,
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ),
        )
    }

    private fun vehicle(name: String, saleDate: LocalDate? = null) = Vehicle(
        name = name,
        saleDate = saleDate,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun repair(vehicleId: Long, date: String, description: String) = Repair(
        vehicleId = vehicleId,
        date = LocalDate.parse(date),
        description = description,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
