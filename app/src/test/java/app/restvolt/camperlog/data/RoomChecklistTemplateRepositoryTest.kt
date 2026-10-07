package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.ChecklistTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomChecklistTemplateRepositoryTest {

    private lateinit var db: CamperLogDatabase
    private lateinit var repository: RoomChecklistTemplateRepository
    private var now = Instant.parse("2026-01-01T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomChecklistTemplateRepository(db) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun template(name: String = "Abfahrt", items: List<String> = listOf("Dachluken schließen", "Trittstufe einfahren")) =
        ChecklistTemplate(name = name, items = items, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun saveSetsUuidAndTimestampsAndUpdateKeepsCreatedAt() = runTest {
        val id = repository.save(template())
        val stored = checkNotNull(repository.observeAll().first().firstOrNull { it.id == id })
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(4, UUID.fromString(stored.uuid).version())

        val created = now
        now = Instant.parse("2026-02-01T10:00:00Z")
        repository.save(stored.copy(name = "Abfahrt (neu)"))
        val updated = checkNotNull(repository.observeAll().first().first { it.id == id })
        assertEquals("Abfahrt (neu)", updated.name)
        assertEquals(created, updated.createdAt)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun itemOrderIsPreserved() = runTest {
        val id = repository.save(template(items = listOf("Dritter", "Erster", "Zweiter")))
        val stored = checkNotNull(repository.observeAll().first().firstOrNull { it.id == id })
        assertEquals(listOf("Dritter", "Erster", "Zweiter"), stored.items)
    }

    @Test
    fun updateReplacesItemsCompletely() = runTest {
        val id = repository.save(template(items = listOf("A", "B", "C")))
        val stored = checkNotNull(repository.observeAll().first().first { it.id == id })

        repository.save(stored.copy(items = listOf("Nur das")))

        val updated = checkNotNull(repository.observeAll().first().first { it.id == id })
        assertEquals(listOf("Nur das"), updated.items)
    }

    @Test
    fun observeAllOrdersByName() = runTest {
        repository.save(template(name = "Winterizing"))
        repository.save(template(name = "Abfahrt"))
        repository.save(template(name = "Ankunft"))

        assertEquals(listOf("Abfahrt", "Ankunft", "Winterizing"), repository.observeAll().first().map { it.name })
    }

    @Test
    fun restoreBringsBackADeletedTemplateWithItsOriginalIdAndItems() = runTest {
        val id = repository.save(template())
        val stored = checkNotNull(repository.observeAll().first().first { it.id == id })
        repository.delete(id)

        repository.restore(stored)

        assertEquals(listOf(stored), repository.observeAll().first())
    }

    @Test
    fun deletingATemplateDoesNotAffectAlreadyStartedChecklists() = runTest {
        val id = repository.save(template())
        val template = checkNotNull(repository.observeAll().first().first { it.id == id })
        val vehicleId = db.vehicleDao().insert(VehicleEntity(uuid = "vehicle-1", createdAtMillis = 0, updatedAtMillis = 0))
        val checklists = RoomChecklistRepository(db) { now }
        checklists.save(app.restvolt.camperlog.domain.startChecklist(template, vehicleId))

        repository.delete(id)

        assertEquals(listOf("Dachluken schließen", "Trittstufe einfahren"), checklists.allChecklists().single().items.map { it.text })
    }
}
