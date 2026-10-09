package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.TrackInterval
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Schalter "Trackaufzeichnung" in den Einstellungen: Berechtigung, Intervall, Ausschalten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class TrackRecordingSettingsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val settings get() = TrackRecordingSettings.get(ApplicationProvider.getApplicationContext<Context>())

    @Before
    fun setUp() {
        TrackRecordingSettings.resetShared()
    }

    @After
    fun tearDown() {
        TrackRecordingSettings.resetShared()
    }

    private fun openSettings() {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(FakeTourRepository(emptyList()), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Trackaufzeichnung"))
    }

    private fun grantAll() {
        shadowOf(compose.activity.application).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun switchIsOffByDefaultAndHidesTheOptions() {
        openSettings()

        assertFalse(settings.enabled)
        compose.onNodeWithText("Position alle", substring = true).assertDoesNotExist()
    }

    @Test
    fun switchingOnWithPermission_showsIntervalAndChargingOption() {
        grantAll()
        openSettings()

        compose.onNodeWithText("Trackaufzeichnung").performClick()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Beim Laden öfter"))
        compose.onNodeWithText("15 min").assertExists()
        assertTrue(settings.enabled)
    }

    @Test
    fun pickingAnInterval_storesIt() {
        grantAll()
        openSettings()
        compose.onNodeWithText("Trackaufzeichnung").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Position alle"))

        compose.onNodeWithText("Position alle").performClick()
        compose.onNodeWithText("2 min").performClick()

        assertEquals(TrackInterval.MINUTES_2, settings.interval)
    }

    @Test
    fun switchingOff_clearsARunningRecordingAndShowsTheHint() {
        // Standort-Schalter an: sonst würde das Ausschalten die Berechtigung entziehen, was Robolectric nicht kann.
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("location", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        grantAll()
        openSettings()
        compose.onNodeWithText("Trackaufzeichnung").performClick()
        settings.activeRecording = app.restvolt.camperlog.tracking.ActiveRecording(tourId = 1, segment = 1)

        compose.onNodeWithText("Trackaufzeichnung").performClick()

        assertFalse(settings.enabled)
        assertEquals(null, settings.activeRecording)
        compose.onNodeWithText("Trackaufzeichnung aus. Aufgezeichnete Tracks bleiben gespeichert.").assertExists()
    }
}
