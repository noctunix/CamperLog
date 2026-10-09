package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.ui.settings.HomeLocationSettings
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Neue "Zuhause"-Karte in den Einstellungen: Name, manuelle Koordinate und die GPS-Erfassung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class HomeLocationSettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.getSharedPreferences("home_location", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openSettings() {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(emptyList()), FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(),
                    FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(),
                    FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(), onAccentColorChange = { },
                ) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Zuhause"))
    }

    @Test
    fun enteringNameAndCoordinates_persistsThem() {
        openSettings()

        compose.onNodeWithText("Name").performTextInput("Elternhaus")
        compose.onNodeWithText("Koordinaten oder Kartenlink").performTextInput("52.52, 13.405")

        val saved = HomeLocationSettings(context).values.value
        assertEquals("Elternhaus", saved.name)
        assertEquals(52.52, saved.latitude)
        assertEquals(13.405, saved.longitude)
        assertEquals(CoordinateSource.ENTERED, saved.coordinateSource)
    }

    @Test
    fun locationSwitchOff_hidesTheGpsButton() {
        openSettings()

        compose.onNodeWithText("Aktuellen Standort verwenden").assertDoesNotExist()
    }

    @Test
    fun locationSwitchOn_showsTheGpsButton() {
        LocationSettings(context).enabled = true

        openSettings()

        compose.onNodeWithText("Aktuellen Standort verwenden").assertExists()
    }
}
