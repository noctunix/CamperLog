package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.onboarding.FirstRunSetupScreen
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.WeatherSettings
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Der Erststart führt ohne Pflichtassistent in die App; die Einrichtung bleibt separat bedienbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class FirstRunSetupFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clearPreferences() {
        context.getSharedPreferences("introduction", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("guide_progress", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start(
        logs: FakeLogRepository = FakeLogRepository(),
        tours: FakeTourRepository = FakeTourRepository(emptyList()),
        onThemeModeChange: (ThemeMode) -> Unit = {},
    ) {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tours,
                    FakeVehicleRepository(),
                    logs,
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    AccentColor.AZURE,
                    countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(),
                    onAccentColorChange = { },
                    onThemeModeChange = onThemeModeChange,
                    formDraftStore = FakeFormDraftStore(),
                )
            }
        }
    }

    @Test
    fun firstStartShowsRealActionsWithoutSetupOrAutomaticGuide() {
        clearPreferences()
        try {
            start()
            compose.onNodeWithText("Tour starten").assertExists()
            compose.onNodeWithText("Station ohne Tour erfassen").assertExists()
            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()
            assertFalse(IntroductionSettings(context).seen)
            assertFalse(LocationSettings(context).enabled)
            assertFalse(WeatherSettings(context).enabled)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun startingTheFirstTourShowsSetupBeforeTheFormAndOnlyOnce() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Tour starten").performClick()
            compose.onNodeWithText("Einstellungen").assertExists()
            compose.onNodeWithContentDescription("Tour anlegen").assertDoesNotExist()
            assertFalse(IntroductionSettings(context).seen)

            compose.onNodeWithText("Überspringen").performClick()
            compose.onNodeWithContentDescription("Tour anlegen").assertExists()
            assertTrue(IntroductionSettings(context).seen)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun addingTheFirstStationWithoutATourShowsSetupBeforeTheTypePicker() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Station ohne Tour erfassen").performClick()
            compose.onNodeWithText("Einstellungen").assertExists()
            compose.onNodeWithText("Was für eine Station?").assertDoesNotExist()

            compose.onNodeWithText("Überspringen").performClick()
            compose.onNodeWithText("Was für eine Station?").assertExists()
            assertTrue(IntroductionSettings(context).seen)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun choosingToImportABackupDoesNotShowSetupBeforeTheImportFinished() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Sicherung einspielen").performClick()
            compose.onNodeWithText("Einstellungen").assertDoesNotExist()
            assertFalse(IntroductionSettings(context).seen)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun setupDoesNotAppearAgainAfterItWasAlreadySeen() {
        clearPreferences()
        try {
            IntroductionSettings(context).seen = true

            start()
            compose.onNodeWithText("Tour starten").performClick()

            compose.onNodeWithContentDescription("Tour anlegen").assertExists()
            compose.onNodeWithText("Einstellungen").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun doesNotStartTheIntroductionTourAgainOnALaterLaunch() {
        clearPreferences()
        try {
            IntroductionSettings(context).seen = true

            start()

            compose.onNodeWithText("Touren").assertExists()
            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }

    private fun startSetup(onThemeModeChange: (ThemeMode) -> Unit = {}, onFinished: () -> Unit = {}) {
        compose.setContent {
            CamperLogTheme {
                FirstRunSetupScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = onThemeModeChange,
                    reminderSettings = ReminderSettings(context),
                    locationSettings = LocationSettings(context),
                    weatherSettings = WeatherSettings(context),
                    onFinished = onFinished,
                )
            }
        }
    }

    @Test
    fun skippingOptionalSetupOnlyFinishesIt() {
        clearPreferences()
        try {
            var finished = false
            startSetup(onFinished = { finished = true })
            compose.onNodeWithText("Überspringen").performClick()
            assertTrue(finished)
            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()
            assertFalse(LocationSettings(context).enabled)
            assertFalse(WeatherSettings(context).enabled)
        } finally { clearPreferences() }
    }

    @Test
    fun backButtonReturnsToThePreviousPage() {
        clearPreferences()
        try {
            startSetup()

            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Optional: Standort, Wetter, Karte").assertExists()

            compose.onNodeWithText("Zurück").performClick()
            compose.onNodeWithText("Einstellungen").assertExists()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun settingsPageAppliesThemeModeImmediatelyAndSavesReminderLeadDays() {
        clearPreferences()
        try {
            var appliedThemeMode: ThemeMode? = null
            startSetup(onThemeModeChange = { appliedThemeMode = it })

            compose.onNodeWithText("Hell").performClick()
            assertEquals(ThemeMode.LIGHT, appliedThemeMode)

            compose.onNodeWithText("Vor Fälligkeit erinnern").performClick()
            compose.onNodeWithText("60 Tage vorher").performClick()

            assertEquals(60, ReminderSettings(context).reminderLeadDays)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun optInPage_locationSwitchDefaultsOffAndTogglingPersists() {
        clearPreferences()
        try {
            startSetup()

            compose.onNodeWithText("Weiter").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Optional: Standort, Wetter, Karte").assertExists()
            compose.onNodeWithText("Aktuellen Standort nutzen").performClick()

            assertTrue(LocationSettings(context).values.value)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun optInPage_weatherSwitchDefaultsOffAndTogglingPersists() {
        clearPreferences()
        try {
            startSetup()

            compose.onNodeWithText("Weiter").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Wetter & Karte (Internet)").performClick()

            assertTrue(WeatherSettings(context).values.value)
            // Der Standort-Schalter bleibt von der Wetter-Umschaltung unberührt: zwei unabhängige Schalter.
            assertFalse(LocationSettings(context).values.value)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun keepAndroidOpenDialogDoesNotInterruptTheFirstStart() {
        clearPreferences()
        try {
            val entry = LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = LocalDate.now(), createdAt = Instant.EPOCH)

            start(logs = FakeLogRepository(listOf(entry)))

            compose.onNodeWithText("Tour starten").assertExists()
            compose.onNodeWithText("Android droht die Abschottung").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }
}
