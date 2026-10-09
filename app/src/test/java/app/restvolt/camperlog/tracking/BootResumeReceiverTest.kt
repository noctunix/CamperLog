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

/** `BOOT_COMPLETED`-Empfänger: löst [TrackRecordingService.resumeAfterBoot] nur beim passenden Intent aus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootResumeReceiverTest {

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
    fun bootCompleted_resumesAnActiveRecording() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        BootResumeReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

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
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        BootResumeReceiver().onReceive(app, Intent("some.other.action"))

        assertNull(shadowOf(app).nextStartedService)
    }
}
