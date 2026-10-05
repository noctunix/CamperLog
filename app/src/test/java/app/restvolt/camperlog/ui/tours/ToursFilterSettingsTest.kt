package app.restvolt.camperlog.ui.tours

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToursFilterSettingsTest {

    @Test
    fun defaultsOffAndChangeSurvivesNewSettingsInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("tours_filter", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertFalse(ToursFilterSettings(context).allVehicles)

            ToursFilterSettings(context).allVehicles = true

            assertEquals(true, ToursFilterSettings(context).allVehicles)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
