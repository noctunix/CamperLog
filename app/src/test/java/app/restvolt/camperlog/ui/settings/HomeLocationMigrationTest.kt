package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.ui.FakeHomeLocationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeLocationMigrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("home_location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aLegacyLocation_becomesExactlyOneHistoryEntry() = runTest {
        clear()
        try {
            val settings = HomeLocationSettings(context)
            settings.name = "Elternhaus"
            settings.setLocation(52.52, 13.405, CoordinateSource.ENTERED)
            val repository = FakeHomeLocationRepository()

            migrateHomeLocationToHistory(settings, repository)

            val saved = repository.locations.single()
            assertEquals("Elternhaus", saved.name)
            assertEquals(52.52, saved.latitude)
            assertEquals(13.405, saved.longitude)
            assertEquals(CoordinateSource.ENTERED, saved.coordinateSource)
            assertTrue(settings.migratedToHistory)
        } finally {
            clear()
        }
    }

    @Test
    fun noLegacyLocation_marksMigratedWithoutCreatingAnEntry() = runTest {
        clear()
        try {
            val settings = HomeLocationSettings(context)
            val repository = FakeHomeLocationRepository()

            migrateHomeLocationToHistory(settings, repository)

            assertTrue(repository.locations.isEmpty())
            assertTrue(settings.migratedToHistory)
        } finally {
            clear()
        }
    }

    @Test
    fun alreadyMigrated_runsOnlyOnce() = runTest {
        clear()
        try {
            val settings = HomeLocationSettings(context)
            settings.setLocation(52.52, 13.405, CoordinateSource.ENTERED)
            val repository = FakeHomeLocationRepository()
            migrateHomeLocationToHistory(settings, repository)
            assertEquals(1, repository.locations.size)

            migrateHomeLocationToHistory(settings, repository)

            assertEquals(1, repository.locations.size)
        } finally {
            clear()
        }
    }
}
