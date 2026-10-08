package app.restvolt.camperlog.tracking

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Abbruchpfade des Aufzeichnungsdienstes: Vordergrundpflicht nach `startForegroundService`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackRecordingServiceTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() = TrackRecordingSettings.resetShared()

    @After
    fun tearDown() = TrackRecordingSettings.resetShared()

    private fun startIntent(): Intent {
        TrackRecordingService.start(app, 1L)
        return shadowOf(app).nextStartedService
    }

    @Test
    fun abortedForegroundStart_stillEntersForegroundBeforeStopping() {
        // Aufzeichnung ausgeschaltet: der Start wird sofort abgebrochen.
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent()).create().startCommand(0, 1)

        val service = shadowOf(controller.get())
        assertNotEquals(0, service.lastForegroundNotificationId)
        assertTrue(service.isStoppedBySelf)
    }

    @Test
    fun stopIntent_doesNotEnterForeground() {
        TrackRecordingService.stop(app)
        val stop = shadowOf(app).nextStartedService
        val controller = Robolectric.buildService(TrackRecordingService::class.java, stop).create().startCommand(0, 1)

        val service = shadowOf(controller.get())
        assertEquals(0, service.lastForegroundNotificationId)
        assertTrue(service.isStoppedBySelf)
    }
}
