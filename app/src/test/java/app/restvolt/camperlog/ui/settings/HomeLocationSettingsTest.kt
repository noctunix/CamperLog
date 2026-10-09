package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.CoordinateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeLocationSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("home_location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToNoLocationSet() {
        clear()
        try {
            val settings = HomeLocationSettings(context)

            assertEquals("", settings.values.value.name)
            assertNull(settings.values.value.latitude)
            assertNull(settings.values.value.longitude)
            assertNull(settings.values.value.coordinateSource)
            assertFalse(settings.values.value.hasLocation)
        } finally {
            clear()
        }
    }

    @Test
    fun settingLocation_persistsAcrossInstances() {
        clear()
        try {
            val settings = HomeLocationSettings(context)
            settings.name = "Elternhaus"

            settings.setLocation(52.52, 13.405, CoordinateSource.ENTERED)

            val reloaded = HomeLocationSettings(context)
            assertEquals("Elternhaus", reloaded.values.value.name)
            assertEquals(52.52, reloaded.values.value.latitude)
            assertEquals(13.405, reloaded.values.value.longitude)
            assertEquals(CoordinateSource.ENTERED, reloaded.values.value.coordinateSource)
            assertTrue(reloaded.values.value.hasLocation)
        } finally {
            clear()
        }
    }

    @Test
    fun settingLocationToNull_clearsItAgain() {
        clear()
        try {
            val settings = HomeLocationSettings(context)
            settings.setLocation(52.52, 13.405, CoordinateSource.GPS)

            settings.setLocation(null, null, null)

            assertFalse(settings.values.value.hasLocation)
            assertNull(settings.values.value.coordinateSource)
            val reloaded = HomeLocationSettings(context)
            assertNull(reloaded.values.value.latitude)
            assertNull(reloaded.values.value.longitude)
        } finally {
            clear()
        }
    }
}
