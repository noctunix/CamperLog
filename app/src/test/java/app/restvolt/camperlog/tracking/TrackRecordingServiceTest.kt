package app.restvolt.camperlog.tracking

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.CamperLogApp
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun startIntent(tourId: Long = 1L): Intent {
        TrackRecordingService.start(app, tourId)
        return shadowOf(app).nextStartedService
    }

    private fun grantLocation() {
        shadowOf(app).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    /**
     * Pumpt den Hauptlooper, bis [condition] zutrifft oder die Zeit abläuft. Die Coroutine des
     * Dienstes greift über echte Hintergrundthreads auf die Datenbank zu; ein einzelner `idle()`
     * reicht dafür nicht zuverlässig, da der Hintergrundthread seine Antwort noch nicht gepostet
     * haben muss.
     */
    private fun idleUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Zeitüberschreitung beim Warten auf die Dienst-Coroutine", condition())
    }

    /** Legt eine echte Tour an, damit der Dienst sie beim Start als existierend vorfindet. */
    private fun createTour(): Long = runBlocking {
        (app as CamperLogApp).repository.save(
            Tour(
                startDate = LocalDate.parse("2026-01-01"),
                endDate = null,
                destination = "Ziel",
                tourType = TourType.VACATION,
                travelDays = 1,
                overnightStays = 0,
                distanceKm = 0,
                costs = emptyList(),
                notes = "",
                mapLink = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
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
    fun notification_titleIncludesTheAppName() {
        grantLocation()
        TrackRecordingSettings.get(app).enabled = true
        val tourId = createTour()
        Robolectric.buildService(TrackRecordingService::class.java, startIntent(tourId)).create().startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        val notificationManager = app.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notificationManager).allNotifications.single()
        val title = NotificationCompat.getContentTitle(notification).toString()

        assertTrue(title.contains("CamperLog"))
    }

    @Test
    fun stopIfTracking_onlyStopsAMatchingTour() {
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 5
        settings.activeRecording = ActiveRecording(tourId = 5, segment = 1)

        TrackRecordingService.stopIfTracking(app, 9)
        assertEquals(5L, settings.trackedTourId)
        assertEquals(ActiveRecording(5, 1), settings.activeRecording)
        assertNull(shadowOf(app).nextStartedService)

        TrackRecordingService.stopIfTracking(app, 5)
        assertNull(settings.trackedTourId)
        assertNull(settings.activeRecording)
    }

    @Test
    fun startingForANonexistentTour_shutsDownAndForgetsIt() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 42
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(42)).create().startCommand(0, 1)
        val service = shadowOf(controller.get())
        idleUntil { service.isStoppedBySelf }

        assertTrue(service.isStoppedBySelf)
        assertNull(settings.activeRecording)
        assertNull(settings.trackedTourId)
    }

    @Test
    fun startingForANonexistentTour_whileAnotherRecordingIsRunning_leavesItUntouched() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        val realTourId = createTour()
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(realTourId)).create()
        controller.startCommand(0, 1)
        idleUntil { settings.activeRecording != null }
        assertEquals(ActiveRecording(realTourId, 1), settings.activeRecording)

        // Fälschlich auf eine inzwischen verschwundene Tour gesetzt, z. B. durch eine verspätete Reaktion.
        val missingTourId = realTourId + 1_000
        settings.trackedTourId = missingTourId
        val secondStart = Intent(app, TrackRecordingService::class.java)
            .setAction("app.restvolt.camperlog.tracking.START")
            .putExtra("tour_id", missingTourId)
        controller.get().onStartCommand(secondStart, 0, 2)
        idleUntil { settings.trackedTourId != missingTourId }

        assertEquals(ActiveRecording(realTourId, 1), settings.activeRecording)
        assertEquals(realTourId, settings.trackedTourId)
    }

    @Test
    fun replaceImportDeletingTheTrackedTour_rejectsAGhostResume() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        val tourId = createTour()
        settings.trackedTourId = tourId
        settings.activeRecording = ActiveRecording(tourId = tourId, segment = 1)
        settings.lastActiveElapsedRealtime = 1L

        // Sicherung im Ersetzen-Modus einspielen: löscht alle Touren, ohne den Aufzeichnungszustand zu kennen.
        runBlocking {
            (app as CamperLogApp).backupImporter.import(
                Backup(exportedAt = Instant.EPOCH, mainCurrency = Currency.getInstance("EUR"), rates = emptyList(), tours = emptyList()),
                ImportMode.REPLACE,
            )
        }

        TrackRecordingService.resumeIfNeeded(app)
        val resumed = shadowOf(app).nextStartedService
        val controller = Robolectric.buildService(TrackRecordingService::class.java, resumed).create().startCommand(0, 1)
        val service = shadowOf(controller.get())
        idleUntil { service.isStoppedBySelf }

        assertTrue(service.isStoppedBySelf)
        assertNull(settings.activeRecording)
        assertNull(settings.trackedTourId)
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
    fun resumeIfNeeded_restartsSilentlyWithoutADetectedReboot() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        settings.lastActiveElapsedRealtime = 1L // derselbe Boot-Zyklus: der gespeicherte Wert ist winzig.

        TrackRecordingService.resumeIfNeeded(app)

        val started = shadowOf(app).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started?.component?.className)
        assertEquals(1L, started?.getLongExtra("tour_id", -1))
        assertEquals(ActiveRecording(1, 1), settings.activeRecording)
        assertFalse(settings.pausedByReboot)
    }

    @Test
    fun resumeIfNeeded_restartsAutomaticallyAfterADetectedReboot() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        // Weit in der Zukunft gespeichert: nach einem echten Neustart ist die aktuelle elapsedRealtime winzig.
        settings.lastActiveElapsedRealtime = Long.MAX_VALUE / 2

        TrackRecordingService.resumeIfNeeded(app)

        val started = shadowOf(app).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started?.component?.className)
        assertEquals(1L, started?.getLongExtra("tour_id", -1))
        assertEquals(ActiveRecording(1, 1), settings.activeRecording)
        assertTrue(settings.resumedAfterBoot)
        assertFalse(settings.pausedByReboot)
    }

    @Test
    fun resumeIfNeeded_fallsBackToPausedByRebootWhenPermissionWasRevoked() {
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        // Weit in der Zukunft gespeichert: nach einem echten Neustart ist die aktuelle elapsedRealtime winzig.
        settings.lastActiveElapsedRealtime = Long.MAX_VALUE / 2

        TrackRecordingService.resumeIfNeeded(app)

        assertNull(shadowOf(app).nextStartedService)
        assertNull(settings.activeRecording)
        assertEquals(1L, settings.trackedTourId)
        assertTrue(settings.pausedByReboot)
        assertFalse(settings.resumedAfterBoot)
    }

    @Test
    fun resumeAfterBoot_startsDirectlyWhenActiveEnabledAndPermissionGranted() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        TrackRecordingService.resumeAfterBoot(app)

        val started = shadowOf(app).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started?.component?.className)
        assertEquals(1L, started?.getLongExtra("tour_id", -1))
        assertTrue(settings.resumedAfterBoot)
    }

    @Test
    fun resumeAfterBoot_doesNothingWithoutAnActiveRecording() {
        grantLocation()
        TrackRecordingSettings.get(app).enabled = true

        TrackRecordingService.resumeAfterBoot(app)

        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun resumeAfterBoot_doesNothingWhileTheGlobalSwitchIsOff() {
        grantLocation()
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        TrackRecordingService.resumeAfterBoot(app)

        assertNull(shadowOf(app).nextStartedService)
        assertFalse(settings.resumedAfterBoot)
    }

    @Test
    fun resumeAfterBoot_doesNothingWithoutLocationPermission() {
        val settings = TrackRecordingSettings.get(app)
        settings.enabled = true
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)

        TrackRecordingService.resumeAfterBoot(app)

        assertNull(shadowOf(app).nextStartedService)
        assertFalse(settings.resumedAfterBoot)
    }

    @Test
    fun resumingAfterAPausedByRebootClearsTheMarker() {
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 1
        settings.pausedByReboot = true

        TrackRecordingService.start(app, 1)

        assertFalse(settings.pausedByReboot)
    }

    @Test
    fun pauseAndStopAndStartForTour_clearTheResumedAfterBootMarker() {
        val settings = TrackRecordingSettings.get(app)
        settings.trackedTourId = 1
        settings.resumedAfterBoot = true
        TrackRecordingService.pause(app)
        assertFalse(settings.resumedAfterBoot)

        settings.resumedAfterBoot = true
        TrackRecordingService.stop(app)
        assertFalse(settings.resumedAfterBoot)

        settings.resumedAfterBoot = true
        TrackRecordingService.startForTour(app, 1)
        assertFalse(settings.resumedAfterBoot)
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
