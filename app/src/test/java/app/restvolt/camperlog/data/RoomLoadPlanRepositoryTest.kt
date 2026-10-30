package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadProfile
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLoadPlanRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomLoadPlanRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private var vehicleId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLoadPlanRepository(db) { now }
        vehicleId = kotlinx.coroutines.runBlocking {
            db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun item(name: String = "Fahrradträger") = LoadItem(
        vehicleId = vehicleId,
        name = name,
        category = LoadCategory.FIXTURE,
        massGrams = 15_000,
        quantity = 1,
        positionCm = 640,
        inWeighing = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun saveItemAssignsUuidAndTimestamps() = runTest {
        val id = repository.saveItem(item())

        val stored = repository.observePlan(vehicleId).first().items.single { it.id == id }
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertTrue(stored.uuid.isNotBlank())
    }

    @Test
    fun deletingAnItemRemovesItsProfileStates() = runTest {
        val itemId = repository.saveItem(item())
        val profileId = repository.saveProfile(LoadProfile(vehicleId = vehicleId, name = "Reise"))
        repository.setItemActive(profileId, itemId, active = false)

        repository.deleteItem(itemId)

        val plan = repository.observePlan(vehicleId).first()
        assertEquals(emptyList<LoadItem>(), plan.items)
        assertTrue(plan.itemStates.isEmpty())
    }

    @Test
    fun restoreBringsBackADeletedItemWithItsOriginalIdAndUuid() = runTest {
        val id = repository.saveItem(item())
        val stored = repository.observePlan(vehicleId).first().items.single { it.id == id }
        repository.deleteItem(id)

        repository.restoreItem(stored)

        assertEquals(listOf(stored), repository.observePlan(vehicleId).first().items)
    }

    @Test
    fun duplicateProfileCopiesItsStatesAndTankLevels() = runTest {
        val itemId = repository.saveItem(item())
        val profileId = repository.saveProfile(LoadProfile(vehicleId = vehicleId, name = "Original"))
        repository.setItemActive(profileId, itemId, active = false)
        repository.setTankLevel(profileId, LoadTankKind.FRESH_WATER, levelDl = 500)

        val copyId = repository.duplicateProfile(profileId, "Kopie")

        val plan = repository.observePlan(vehicleId).first()
        assertEquals("Kopie", plan.profiles.single { it.id == copyId }.name)
        assertEquals(false, plan.itemStates.single { it.profileId == copyId }.active)
        assertEquals(500, plan.tankLevels.single { it.profileId == copyId }.levelDl)
    }

    @Test
    fun deletingAProfileRemovesItsStatesAndTankLevelsButKeepsItems() = runTest {
        val itemId = repository.saveItem(item())
        val profileId = repository.saveProfile(LoadProfile(vehicleId = vehicleId, name = "Reise"))
        repository.setItemActive(profileId, itemId, active = false)
        repository.setTankLevel(profileId, LoadTankKind.FRESH_WATER, levelDl = 500)

        repository.deleteProfile(profileId)

        val plan = repository.observePlan(vehicleId).first()
        assertEquals(1, plan.items.size)
        assertTrue(plan.itemStates.isEmpty())
        assertTrue(plan.tankLevels.isEmpty())
    }

    @Test
    fun ensureProfileCreatesOneOnlyWhenNoneExists() = runTest {
        val firstId = repository.ensureProfile(vehicleId, "Standard")
        val secondId = repository.ensureProfile(vehicleId, "Standard")

        assertEquals(firstId, secondId)
        assertEquals(1, repository.observePlan(vehicleId).first().profiles.size)
    }

    @Test
    fun saveTankSettingsReplacesExistingSettingsForTheSameKind() = runTest {
        repository.saveTankSettings(LoadTankSettings(vehicleId, LoadTankKind.FRESH_WATER, positionCm = 100, densityGramsPerLitre = 1000))
        repository.saveTankSettings(LoadTankSettings(vehicleId, LoadTankKind.FRESH_WATER, positionCm = 200, densityGramsPerLitre = 1000))

        val settings = repository.observePlan(vehicleId).first().tankSettings
        assertEquals(1, settings.size)
        assertEquals(200, settings.single().positionCm)
    }

    @Test
    fun deletingTheVehicleCascadesToItsLoadPlan() = runTest {
        repository.saveItem(item())
        repository.saveProfile(LoadProfile(vehicleId = vehicleId, name = "Reise"))
        repository.saveTankSettings(LoadTankSettings(vehicleId, LoadTankKind.FRESH_WATER, densityGramsPerLitre = 1000))

        db.vehicleDao().deleteById(vehicleId)

        val plan = repository.observePlan(vehicleId).first()
        assertEquals(emptyList<LoadItem>(), plan.items)
        assertEquals(emptyList<LoadProfile>(), plan.profiles)
        assertTrue(plan.tankSettings.isEmpty())
    }

    @Test
    fun freshPlanHasNoItemsOrProfiles() = runTest {
        val plan = repository.observePlan(vehicleId).first()
        assertTrue(plan.items.isEmpty())
        assertTrue(plan.profiles.isEmpty())
        assertNull(plan.tankSettings.firstOrNull())
    }
}
