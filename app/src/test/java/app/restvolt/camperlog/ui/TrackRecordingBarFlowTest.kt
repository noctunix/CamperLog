package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.tracking.ActiveRecording
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Leiste über der Navigation: sichtbar, solange eine Tour markiert ist; Pausieren/Fortsetzen und Beenden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class TrackRecordingBarFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val settings get() = TrackRecordingSettings.get(context)

    @Before
    fun setUp() = TrackRecordingSettings.resetShared()

    @After
    fun tearDown() = TrackRecordingSettings.resetShared()

    private fun start(tour: Tour) {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(FakeTourRepository(listOf(tour)), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }) { }
            }
        }
    }

    @Test
    fun noTrackedTour_hidesTheBar() {
        start(lofoten())

        compose.onNodeWithText("GPS-Track läuft").assertDoesNotExist()
        compose.onNodeWithText("GPS-Track pausiert").assertDoesNotExist()
    }

    /** Das Tourenlog zeigt "GPS-Track läuft" ebenfalls auf der laufenden Tourkarte; die Leiste ist der zweite Treffer. */
    private fun barNode(text: String) = hasText(text) and !hasAnyAncestor(hasScrollAction())

    @Test
    fun runningTour_showsTheBarWithNameAndPauseAndStopActions() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        start(lofoten())

        compose.onNode(barNode("GPS-Track läuft")).assertExists()
        compose.onNodeWithContentDescription("Aufzeichnung pausieren").assertExists()
        compose.onNodeWithContentDescription("Aufzeichnung beenden").assertExists()
    }

    @Test
    fun pausedTour_showsResumeInstead() {
        settings.trackedTourId = 1
        start(lofoten())

        compose.onNodeWithText("GPS-Track pausiert").assertExists()
        compose.onNodeWithContentDescription("Aufzeichnung fortsetzen").assertExists()
    }

    @Test
    fun pausedByReboot_showsTheRebootHintInsteadOfTheNormalPausedText() {
        settings.trackedTourId = 1
        settings.pausedByReboot = true
        start(lofoten())

        compose.onNodeWithText("GPS-Track unterbrochen").assertExists()
        compose.onNodeWithText("GPS-Track pausiert").assertDoesNotExist()
    }

    @Test
    fun resumedAfterBoot_showsTheResumedHintWhileRunning() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        settings.resumedAfterBoot = true
        start(lofoten())

        compose.onNode(barNode("GPS-Track nach Neustart fortgesetzt")).assertExists()
    }

    @Test
    fun pausingFromTheBar_opensDurationDialogAndWithoutAChoiceClearsTheActiveRecordingButKeepsTheMarker() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        start(lofoten())

        compose.onNodeWithContentDescription("Aufzeichnung pausieren").performClick()
        compose.onNodeWithText("Automatisch fortsetzen nach").assertExists()
        compose.onNodeWithText("Ohne automatisches Fortsetzen").performClick()

        assertNull(settings.activeRecording)
        assertNull(settings.scheduledResumeAtMillis)
        assertEquals(1L, settings.trackedTourId)
        compose.onNodeWithText("GPS-Track pausiert").assertExists()
    }

    @Test
    fun choosingADurationFromTheBar_pausesAndSchedulesAnAutoResumeHint() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        start(lofoten())

        compose.onNodeWithContentDescription("Aufzeichnung pausieren").performClick()
        compose.onNodeWithText("1 h").performClick()

        assertNull(settings.activeRecording)
        assertNotNull(settings.scheduledResumeAtMillis)
        compose.onNodeWithText("Setzt automatisch um", substring = true).assertExists()
    }

    @Test
    fun resumedAfterTimedPause_showsTheResumedHintWhileRunning() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        settings.resumedAfterTimedPause = true
        start(lofoten())

        compose.onNode(barNode("GPS-Track nach Pause fortgesetzt")).assertExists()
    }

    @Test
    fun stoppingFromTheBar_clearsTheMarkerAndHidesTheBar() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        start(lofoten())

        compose.onNodeWithContentDescription("Aufzeichnung beenden").performClick()

        assertNull(settings.trackedTourId)
        compose.onNodeWithText("GPS-Track läuft").assertDoesNotExist()
    }

    @Test
    fun tappingTheBar_opensTheTrackedTour() {
        settings.trackedTourId = 1
        settings.activeRecording = ActiveRecording(tourId = 1, segment = 1)
        start(lofoten())

        compose.onNode(barNode("GPS-Track läuft")).performClick()

        compose.onNodeWithContentDescription("Bearbeiten").assertExists()
    }

    private fun lofoten() = Tour(
        id = 1,
        vehicleId = 1,
        startDate = LocalDate.of(2020, 7, 4),
        endDate = null,
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
