package app.restvolt.camperlog.ui

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
class VehicleScopeSettingsTest {

    @Test
    fun defaultsOffAndChangeSurvivesNewSettingsInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("tours_filter", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertFalse(VehicleScopeSettings(context).allVehicles)

            VehicleScopeSettings(context).allVehicles = true

            assertEquals(true, VehicleScopeSettings(context).allVehicles)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
