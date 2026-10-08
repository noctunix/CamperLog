package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
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
import app.restvolt.camperlog.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Karte "Track" im Tourdetail: Sichtbarkeit, Start mit Akkuhinweis, Stopp, Löschen. */
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
        scrollTo: String? = "Aufzeichnung starten",
        running: Boolean = false,
    ) {
        val firstTour = lofoten(1).let { if (running) it.copy(endDate = null) else it }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(FakeTourRepository(listOf(firstTour, lofoten(2).copy(destination = "Dolomiten"))), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = tracks) { }
            }
        }
        compose.onNodeWithText("Lofoten").performClick()
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
    fun switchOff_hidesTheCard() {
        openTour(scrollTo = null)

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Stationen", substring = true))
        compose.onNodeWithText("Aufzeichnung starten").assertDoesNotExist()
    }

    @Test
    fun runningTourOffersTrackAndExplicitStartEnablesRecording() {
        settings.batteryHintShown = true
        grantLocation()
        openTour(running = true)

        compose.onNodeWithText("Aufzeichnung starten").performClick()

        assertTrue(settings.enabled)
        val started = shadowOf(compose.activity.application).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started.component?.className)
    }

    @Test
    fun firstStart_showsBatteryHintOnceAndStartsTheService() {
        settings.enabled = true
        grantLocation()
        openTour()
        compose.onNodeWithText("Noch kein Track aufgezeichnet.").assertExists()

        compose.onNodeWithText("Aufzeichnung starten").performClick()

        compose.onNodeWithText("Aufzeichnung im Hintergrund").assertExists()
        val started = shadowOf(compose.activity.application).nextStartedService
        assertEquals(TrackRecordingService::class.java.name, started.component?.className)
        assertEquals(1L, started.getLongExtra("tour_id", -1))
        assertTrue(settings.batteryHintShown)

        compose.onNodeWithText("Jetzt nicht").performClick()
        compose.onNodeWithText("Aufzeichnung starten").performClick()
        compose.onNodeWithText("Aufzeichnung im Hintergrund").assertDoesNotExist()
    }

    @Test
    fun runningRecording_showsIntervalAndStops() {
        settings.enabled = true
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        grantLocation()
        openTour(scrollTo = "Aufzeichnung beenden")

        compose.onNodeWithText("Aufzeichnung läuft, Position alle 15 min.").assertExists()
        compose.onNodeWithText("Aufzeichnung beenden").performClick()

        compose.onNodeWithText("Aufzeichnung starten").assertExists()
        assertNull(settings.activeRecording)
    }

    @Test
    fun recordingForAnotherTour_isMentioned() {
        settings.enabled = true
        settings.activeRecording = ActiveRecording(tourId = 2, segment = 1)
        openTour()

        compose.onNodeWithText("Für eine andere Tour läuft gerade eine Aufzeichnung. Ein Start hier beendet sie.").assertExists()
    }

    @Test
    fun deletingTheTrack_asksFirstAndClearsIt() {
        settings.enabled = true
        val tracks = FakeTrackRepository(listOf(point(1, 1, 0), point(1, 1, 60), point(1, 2, 120), point(2, 1, 0)))
        openTour(tracks, scrollTo = "Track löschen")
        compose.onNodeWithText("0,0 km · 3 Punkte · 2 Aufzeichnungen").assertExists()

        compose.onNodeWithText("Track löschen").performClick()
        compose.onNodeWithText("Alle 3 aufgezeichneten Punkte dieser Tour werden gelöscht. Die Stationen bleiben.").assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(4, tracks.points.size)

        compose.onNodeWithText("Track löschen").performClick()
        compose.onNode(hasText("Track löschen") and hasClickActionInDialog()).performClick()

        compose.onNodeWithText("Noch kein Track aufgezeichnet.").assertExists()
        assertEquals(listOf(2L), tracks.points.map { it.tourId })
    }

    @Test
    fun deleteIsDisabledWhileRecordingThisTour() {
        settings.enabled = true
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 2)
        grantLocation()
        openTour(FakeTrackRepository(listOf(point(1, 1, 0))), scrollTo = "Track löschen")

        compose.onNodeWithText("Track löschen").assertIsNotEnabled()
    }

    private fun hasClickActionInDialog() = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())

    @Test
    fun trackLength_isShownWithoutJoiningSegments() {
        settings.enabled = true
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
