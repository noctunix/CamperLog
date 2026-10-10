package app.restvolt.camperlog.tracking

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Alarm-Empfänger für das terminierte Fortsetzen nach einer Pause: löst [TrackRecordingService.resumeFromTimedPause] nur beim passenden Intent aus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PauseResumeReceiverTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() = TrackRecordingSettings.resetShared()

    @After
    fun tearDown() = TrackRecordingSettings.resetShared()

    private fun grantLocation() {
        shadowOf(app).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun autoResumeAction_resumesTheStillPausedTour() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.scheduledResumeAtMillis = 999_000L

        val intent = Intent(PauseResumeReceiver.ACTION_AUTO_RESUME).putExtra(PauseResumeReceiver.EXTRA_TOUR_ID, 1L)
        PauseResumeReceiver().onReceive(app, intent)

        val started = shadowOf(app).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started?.component?.className)
        assertEquals(1L, started?.getLongExtra("tour_id", -1))
    }

    @Test
    fun otherActions_areIgnored() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.scheduledResumeAtMillis = 999_000L

        PauseResumeReceiver().onReceive(app, Intent("some.other.action").putExtra(PauseResumeReceiver.EXTRA_TOUR_ID, 1L))

        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun missingTourIdExtra_isIgnored() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.scheduledResumeAtMillis = 999_000L

        PauseResumeReceiver().onReceive(app, Intent(PauseResumeReceiver.ACTION_AUTO_RESUME))

        assertNull(shadowOf(app).nextStartedService)
    }
}
