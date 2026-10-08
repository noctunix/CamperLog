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
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Schalter "Aktuellen Standort nutzen" in den Einstellungen: Einschalten fragt die Berechtigung an. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class LocationSettingsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val enabled get() = context.getSharedPreferences("location", Context.MODE_PRIVATE).getBoolean("enabled", false)

    @Before
    fun setUp() {
        TrackRecordingSettings.resetShared()
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        TrackRecordingSettings.resetShared()
    }

    private fun openSettings() {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(FakeTourRepository(emptyList()), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository()) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Aktuellen Standort nutzen"))
    }

    @Test
    fun switchingOnWithoutPermission_requestsItAndStaysOff() {
        openSettings()

        compose.onNodeWithText("Aktuellen Standort nutzen").performClick()
        compose.waitForIdle()

        assertFalse(enabled)
        val requested = shadowOf(compose.activity).lastRequestedPermission
        assertTrue(requested?.requestedPermissions?.contains("android.permission.ACCESS_FINE_LOCATION") == true)
    }

    @Test
    fun switchingOnWithPermission_enablesDirectly() {
        shadowOf(compose.activity.application).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
        openSettings()

        compose.onNodeWithText("Aktuellen Standort nutzen").performClick()

        assertTrue(enabled)
    }
}
