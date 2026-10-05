package app.restvolt.camperlog.ui.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeepAndroidOpenSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clearPreferences() {
        context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun fixedClock(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun freshInstanceHasNoFirstLaunchUntilRecorded() {
        clearPreferences()
        try {
            val settings = KeepAndroidOpenSettings(context, fixedClock(Instant.parse("2026-01-01T00:00:00Z")))

            assertNull(settings.state.firstLaunchAt)

            settings.recordFirstLaunchIfNeeded()

            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), settings.state.firstLaunchAt)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun recordFirstLaunchIfNeededDoesNotOverwriteAnExistingValue() {
        clearPreferences()
        try {
            KeepAndroidOpenSettings(context, fixedClock(Instant.parse("2026-01-01T00:00:00Z"))).recordFirstLaunchIfNeeded()

            val later = KeepAndroidOpenSettings(context, fixedClock(Instant.parse("2026-02-01T00:00:00Z")))
            later.recordFirstLaunchIfNeeded()

            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), later.state.firstLaunchAt)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun markShownAndMarkSupportedPersistAcrossInstances() {
        clearPreferences()
        try {
            val settings = KeepAndroidOpenSettings(context, fixedClock(Instant.parse("2026-03-10T08:00:00Z")))

            settings.markShown()

            assertEquals(Instant.parse("2026-03-10T08:00:00Z"), KeepAndroidOpenSettings(context).state.lastShownAt)
            assertFalse(KeepAndroidOpenSettings(context).state.supported)

            settings.markSupported()

            assertTrue(KeepAndroidOpenSettings(context).state.supported)
        } finally {
            clearPreferences()
        }
    }
}
