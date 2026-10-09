package app.restvolt.camperlog.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.data.LocationPermissionRevoker
import app.restvolt.camperlog.domain.TrackInterval
import app.restvolt.camperlog.ui.settings.LocationSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class CountingRevoker : LocationPermissionRevoker {
    var calls = 0
    override fun revokeOnKill() {
        calls++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackRecordingSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clear() {
        context.getSharedPreferences(TrackRecordingSettings.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsAreOffFifteenMinutesAndNoChargingBoost() {
        val values = TrackRecordingSettings(context, CountingRevoker()).values.value
        assertEquals(TrackRecordingPreferences(false, TrackInterval.MINUTES_15, false), values)
        assertNull(TrackRecordingSettings(context, CountingRevoker()).activeRecording)
        assertNull(TrackRecordingSettings(context, CountingRevoker()).trackedTourId)
    }

    @Test
    fun valuesPersistAcrossInstances() {
        TrackRecordingSettings(context, CountingRevoker()).apply {
            enabled = true
            interval = TrackInterval.SECONDS_30
            fasterWhileCharging = true
            batteryHintShown = true
            activeRecording = ActiveRecording(tourId = 7, segment = 3)
            trackedTourId = 7
        }

        val again = TrackRecordingSettings(context, CountingRevoker())
        assertEquals(TrackRecordingPreferences(true, TrackInterval.SECONDS_30, true), again.values.value)
        assertTrue(again.batteryHintShown)
        assertEquals(ActiveRecording(7, 3), again.activeRecording)
        assertEquals(7L, again.trackedTourId)
        assertTrue(TrackRecordingSettings.isSwitchOn(context))
    }

    @Test
    fun trackedTourIdStaysSetWhileTheRecordingItselfIsPausedAndClearsExplicitly() {
        val settings = TrackRecordingSettings(context, CountingRevoker())
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        // Pausieren: der Dienst stoppt und löscht nur die laufende Aufzeichnung, nicht die Markierung.
        settings.activeRecording = null
        assertEquals(1L, settings.trackedTourId)

        settings.trackedTourId = null
        assertNull(settings.trackedTourId)
    }

    @Test
    fun startFailedIsAOneShotSignal() {
        val settings = TrackRecordingSettings(context, CountingRevoker())
        assertFalse(settings.startFailed.value)

        settings.reportStartFailed()
        assertTrue(settings.startFailed.value)

        settings.clearStartFailed()
        assertFalse(settings.startFailed.value)
    }

    @Test
    fun switchingOffEndsRecordingAndRevokesOnlyWhenLocationIsOff() {
        val revoker = CountingRevoker()
        var location = true
        val settings = TrackRecordingSettings(context, revoker) { location }
        settings.enabled = true
        settings.activeRecording = ActiveRecording(1, 1)

        settings.enabled = false
        assertNull(settings.active.value)
        assertEquals(0, revoker.calls)

        location = false
        settings.enabled = true
        settings.enabled = false
        assertEquals(1, revoker.calls)
    }

    @Test
    fun rebootDetectedSinceLastActiveComparesStoredAgainstCurrentElapsedRealtime() {
        var now = 5_000L
        val settings = TrackRecordingSettings(context, CountingRevoker(), elapsedRealtime = { now })
        assertFalse(settings.rebootDetectedSinceLastActive())

        // Gespeicherter Wert aus einem früheren Boot-Zyklus, größer als der aktuelle: ein Reboot liegt dazwischen.
        settings.lastActiveElapsedRealtime = 10_000L
        assertTrue(settings.rebootDetectedSinceLastActive())

        // Derselbe Boot-Zyklus läuft weiter hoch: kein Reboot.
        now = 20_000L
        assertFalse(settings.rebootDetectedSinceLastActive())
    }

    @Test
    fun pausedByRebootPersistsAcrossInstances() {
        TrackRecordingSettings(context, CountingRevoker()).pausedByReboot = true

        assertTrue(TrackRecordingSettings(context, CountingRevoker()).pausedByReboot)
        assertTrue(TrackRecordingSettings(context, CountingRevoker()).pausedByRebootFlow.value)
    }

    @Test
    fun resumedAfterBootPersistsAcrossInstances() {
        TrackRecordingSettings(context, CountingRevoker()).resumedAfterBoot = true

        assertTrue(TrackRecordingSettings(context, CountingRevoker()).resumedAfterBoot)
        assertTrue(TrackRecordingSettings(context, CountingRevoker()).resumedAfterBootFlow.value)
    }

    @Test
    fun locationSwitchKeepsPermissionWhileTrackRecordingIsOn() {
        TrackRecordingSettings(context, CountingRevoker()).enabled = true
        val revoker = CountingRevoker()
        val location = LocationSettings(context, revoker)
        location.enabled = true

        location.enabled = false
        assertEquals(0, revoker.calls)
        assertFalse(location.enabled)
    }
}
