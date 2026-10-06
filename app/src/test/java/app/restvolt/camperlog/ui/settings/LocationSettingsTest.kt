package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.data.LocationPermissionRevoker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeLocationPermissionRevoker : LocationPermissionRevoker {
    var revokeCalls = 0
    override fun revokeOnKill() {
        revokeCalls++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToOffAndPersistsAcrossInstances() {
        clear()
        try {
            assertFalse(LocationSettings(context, FakeLocationPermissionRevoker()).enabled)

            LocationSettings(context, FakeLocationPermissionRevoker()).enabled = true

            assertTrue(LocationSettings(context, FakeLocationPermissionRevoker()).enabled)
        } finally {
            clear()
        }
    }

    @Test
    fun settingToOff_revokesThePermission() {
        clear()
        try {
            val revoker = FakeLocationPermissionRevoker()
            val settings = LocationSettings(context, revoker)
            settings.enabled = true

            settings.enabled = false

            assertEquals(1, revoker.revokeCalls)
            assertFalse(settings.values.value)
        } finally {
            clear()
        }
    }

    @Test
    fun settingToOn_doesNotRevoke() {
        clear()
        try {
            val revoker = FakeLocationPermissionRevoker()
            val settings = LocationSettings(context, revoker)

            settings.enabled = true

            assertEquals(0, revoker.revokeCalls)
            assertTrue(settings.values.value)
        } finally {
            clear()
        }
    }

    @Test
    fun approximateHintShown_defaultsToFalseAndPersists() {
        clear()
        try {
            val settings = LocationSettings(context, FakeLocationPermissionRevoker())
            assertFalse(settings.approximateHintShown)

            settings.approximateHintShown = true

            assertTrue(LocationSettings(context, FakeLocationPermissionRevoker()).approximateHintShown)
        } finally {
            clear()
        }
    }
}
