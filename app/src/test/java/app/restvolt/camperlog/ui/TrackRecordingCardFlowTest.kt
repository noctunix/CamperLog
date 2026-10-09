package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.tracking.ActiveRecording
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Karte "Track" im Tourdetail: Sichtbarkeit, Schalter mit Akkuhinweis, Pausieren/Fortsetzen, Löschen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class TrackRecordingCardFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val settings get() = TrackRecordingSettings.get(context)

    @Before
    fun setUp() {
        TrackRecordingSettings.resetShared()
        // Standort-Schalter an: sonst entzöge ein Ausschalten die Berechtigung, was Robolectric nicht kann.
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
    }

    @After
    fun tearDown() {
        TrackRecordingSettings.resetShared()
    }

    private fun openTour(
        tracks: FakeTrackRepository = FakeTrackRepository(),
        scrollTo: String? = "Für diese Tour aufzeichnen",
        running: Boolean = false,
    ) {
        val firstTour = lofoten(1).let { if (running) it.copy(endDate = null) else it }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(FakeTourRepository(listOf(firstTour, lofoten(2).copy(destination = "Dolomiten"))), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = tracks, onAccentColorChange = { }) { }
            }
        }
        // Mit gesetztem trackedTourId zeigt auch die Aufzeichnungsleiste den Tournamen; auf der Liste zählt nur die Karte.
        compose.onNode(hasText("Lofoten") and hasAnyAncestor(hasScrollAction())).performClick()
        if (scrollTo != null) compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(scrollTo))
    }

    private fun grantLocation() {
        shadowOf(compose.activity.application).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun finishedTourWithoutPoints_hidesTheCard() {
        openTour(scrollTo = null)

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Stationen", substring = true))
        compose.onNodeWithText("Für diese Tour aufzeichnen").assertDoesNotExist()
        compose.onNodeWithText("Track").assertDoesNotExist()
    }

    @Test
    fun finishedTourWithPoints_onlyOffersDeleteWithoutASwitch() {
        val tracks = FakeTrackRepository(listOf(point(1, 1, 0)))
        openTour(tracks, scrollTo = "Track löschen")

        compose.onNodeWithText("Für diese Tour aufzeichnen").assertDoesNotExist()
    }

    @Test
    fun openTourOffersTheSwitchEvenWhileTheGlobalSwitchIsOff() {
        grantLocation()
        openTour(running = true)

        compose.onNodeWithText("Für diese Tour aufzeichnen").performClick()

        assertTrue(settings.enabled)
        assertEquals(1L, settings.trackedTourId)
        val started = shadowOf(compose.activity.application).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started.component?.className)
    }

    @Test
    fun firstSwitchOn_showsBatteryHintOnceAndStartsTheService() {
        grantLocation()
        openTour(running = true)
        compose.onNodeWithText("Noch kein Track aufgezeichnet.").assertExists()

        compose.onNodeWithText("Für diese Tour aufzeichnen").performClick()

        compose.onNodeWithText("Aufzeichnung im Hintergrund").assertExists()
        val started = shadowOf(compose.activity.application).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started.component?.className)
        assertEquals(1L, started.getLongExtra("tour_id", -1))
        assertTrue(settings.batteryHintShown)

        compose.onNodeWithText("Jetzt nicht").performClick()
        // Aus- und wieder einschalten: der Hinweis erscheint beim zweiten Mal nicht mehr.
        compose.onNodeWithText("Für diese Tour aufzeichnen").performClick()
        compose.onNodeWithText("Für diese Tour aufzeichnen").performClick()
        compose.onNodeWithText("Aufzeichnung im Hintergrund").assertDoesNotExist()
    }

    @Test
    fun runningRecording_showsIntervalAndOffersPause() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung pausieren")

        compose.onNodeWithText("Aufzeichnung läuft, Position alle 15 min.").assertExists()
    }

    @Test
    fun pausing_keepsTheTourMarkedAndOffersResume() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung pausieren")

        compose.onNodeWithText("Aufzeichnung pausieren").performClick()

        assertNull(settings.activeRecording)
        assertEquals(1L, settings.trackedTourId)
        compose.onNodeWithText("Aufzeichnung pausiert.").assertExists()
        compose.onNodeWithText("Aufzeichnung fortsetzen").assertExists()
    }

    @Test
    fun resuming_startsTheServiceAgainForTheSameTour() {
        settings.trackedTourId = 1
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung fortsetzen")

        compose.onNodeWithText("Aufzeichnung fortsetzen").performClick()

        val started = shadowOf(compose.activity.application).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started.component?.className)
        assertEquals(1L, started.getLongExtra("tour_id", -1))
    }

    @Test
    fun pausedByReboot_showsTheRebootHintInsteadOfTheNormalPausedText() {
        settings.trackedTourId = 1
        settings.pausedByReboot = true
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung fortsetzen")

        compose.onNodeWithText("Durch einen Geräteneustart unterbrochen.").assertExists()
        compose.onNodeWithText("Aufzeichnung pausiert.").assertDoesNotExist()
    }

    @Test
    fun resumingAfterPausedByReboot_clearsTheRebootMarker() {
        settings.trackedTourId = 1
        settings.pausedByReboot = true
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung fortsetzen")

        compose.onNodeWithText("Aufzeichnung fortsetzen").performClick()

        assertFalse(settings.pausedByReboot)
    }

    @Test
    fun switchingOffEndsTheRecordingAndClearsTheMarker() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        grantLocation()
        openTour(running = true, scrollTo = "Aufzeichnung pausieren")

        compose.onNodeWithText("Für diese Tour aufzeichnen").performClick()

        compose.onNodeWithText("Aufzeichnung läuft", substring = true).assertDoesNotExist()
        assertNull(settings.activeRecording)
        assertNull(settings.trackedTourId)
    }

    @Test
    fun recordingForAnotherTour_isMentioned() {
        settings.trackedTourId = 2
        openTour(running = true)

        compose.onNodeWithText("Für eine andere Tour läuft gerade eine Aufzeichnung. Ein Start hier beendet sie.").assertExists()
    }

    @Test
    fun deletingTheTrack_asksFirstAndClearsIt() {
        val tracks = FakeTrackRepository(listOf(point(1, 1, 0), point(1, 1, 60), point(1, 2, 120), point(2, 1, 0)))
        openTour(tracks, scrollTo = "Track löschen")
        compose.onNodeWithText("0,0 km · 3 Punkte · 2 Aufzeichnungen").assertExists()

        compose.onNodeWithText("Track löschen").performClick()
        compose.onNodeWithText("Alle 3 aufgezeichneten Punkte dieser Tour werden gelöscht. Die Stationen bleiben.").assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(4, tracks.points.size)

        compose.onNodeWithText("Track löschen").performClick()
        compose.onNode(hasText("Track löschen") and hasClickActionInDialog()).performClick()

        // Enddatum gesetzt und keine Punkte mehr: Die Karte verschwindet jetzt ganz.
        compose.onNodeWithText("Track").assertDoesNotExist()
        assertEquals(listOf(2L), tracks.points.map { it.tourId })
    }

    @Test
    fun deleteIsDisabledWhileRecordingThisTour() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 2)
        grantLocation()
        openTour(FakeTrackRepository(listOf(point(1, 1, 0))), scrollTo = "Track löschen")

        compose.onNodeWithText("Track löschen").assertIsNotEnabled()
    }

    private fun hasClickActionInDialog() = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())

    @Test
    fun trackLength_isShownWithoutJoiningSegments() {
        // 0.01° Breite sind etwa 1112 m; der Sprung zwischen den Segmenten zählt nicht.
        val tracks = FakeTrackRepository(
            listOf(point(1, 1, 0), point(1, 1, 60, latitude = 68.21), point(1, 2, 120, latitude = 69.0), point(1, 2, 180, latitude = 69.01)),
        )
        openTour(tracks, scrollTo = "Track löschen")
        compose.onNodeWithText("2,2 km · 4 Punkte · 2 Aufzeichnungen").assertExists()
    }

    private fun point(tourId: Long, segment: Int, seconds: Long, latitude: Double = 68.2) = TrackPoint(
        tourId = tourId,
        segment = segment,
        recordedAt = Instant.ofEpochSecond(1_600_000_000 + seconds),
        latitude = latitude,
        longitude = 13.6,
    )

    private fun lofoten(id: Long) = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(2020, 7, 4),
        endDate = LocalDate.of(2020, 7, 17),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 14,
        overnightStays = 13,
        distanceKm = 3420,
        costs = listOf(Money(48_650, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
