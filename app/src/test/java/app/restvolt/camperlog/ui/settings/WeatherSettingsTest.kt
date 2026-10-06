package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToOffAndPersistsAcrossInstances() {
        clear()
        try {
            assertFalse(WeatherSettings(context).enabled)

            WeatherSettings(context).enabled = true

            assertTrue(WeatherSettings(context).enabled)
        } finally {
            clear()
        }
    }

    @Test
    fun enabled_updatesTheValuesFlowImmediately() {
        clear()
        try {
            val settings = WeatherSettings(context)

            settings.enabled = true

            assertTrue(settings.values.value)
        } finally {
            clear()
        }
    }
}
