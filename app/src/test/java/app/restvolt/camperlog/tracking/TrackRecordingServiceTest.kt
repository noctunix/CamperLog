package app.restvolt.camperlog.tracking

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun stop_clearsTheTrackedTourMarker() {
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        TrackRecordingService.stop(app)

        assertNull(settings.activeRecording)
        assertNull(settings.trackedTourId)
    }

    @Test
    fun pause_clearsOnlyTheActiveRecordingNotTheTrackedTourMarker() {
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        TrackRecordingService.pause(app)

        assertNull(settings.activeRecording)
        assertEquals(1L, settings.trackedTourId)
    }

    @Test
    fun pauseIntent_doesNotEnterForegroundAndKeepsTheMarker() {
        TrackRecordingSettings.get(app).trackedTourId = 1
        TrackRecordingService.pause(app)
        val pause = shadowOf(app).nextStartedService
        val controller = Robolectric.buildService(TrackRecordingService::class.java, pause).create().startCommand(0, 1)

        val service = shadowOf(controller.get())
        assertEquals(0, service.lastForegroundNotificationId)
        assertTrue(service.isStoppedBySelf)
        assertEquals(1L, TrackRecordingSettings.get(app).trackedTourId)
    }

    @Test
    fun startForTour_enablesAndMarksTheTour() {
        TrackRecordingService.startForTour(app, 5L)

        val settings = TrackRecordingSettings.get(app)
        assertTrue(settings.enabled)
        assertEquals(5L, settings.trackedTourId)
        val started = shadowOf(app).nextStartedService
        assertEquals(5L, started.getLongExtra("tour_id", -1))
    }
}
