package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.WeatherSettings
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

/** Einführungstour: Anzeige beim ersten Start, Bedienung, Einstellungen-Seite und erneutes Anzeigen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class IntroductionTourFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Scrollt die Einstellungen-Liste so lange nach unten, bis [text] komponiert ist: eine
     * `LazyColumn` komponiert nur sichtbare Einträge, daher reicht ein einzelnes `performScrollTo()`
     * nicht, wenn der gesuchte Knoten noch gar nicht existiert.
     */
    private fun scrollUntilVisible(text: String, maxAttempts: Int = 10) {
        repeat(maxAttempts) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun clearPreferences() {
        context.getSharedPreferences("introduction", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().commit()
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
                    FakeVehicleDocumentRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    countryLookup = FakeCountryLookupRepository(),
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
    }

    @Test
    fun showsOnFirstStartAndNotAgainAfterFinishing() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Willkommen bei CamperLog").assertExists()

            repeat(5) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }
            compose.onNodeWithText("Los geht's").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()
            compose.onNodeWithText("Touren").assertExists()
            assertTrue(IntroductionSettings(context).seen)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun skipMarksSeenAndEntersMainScreen() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Überspringen").performClick()

            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()
            compose.onNodeWithText("Touren").assertExists()
            assertTrue(IntroductionSettings(context).seen)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun backButtonReturnsToThePreviousPage() {
        clearPreferences()
        try {
            start()

            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Bordbuch").assertExists()

            compose.onNodeWithText("Zurück").performClick()
            compose.onNodeWithText("Willkommen bei CamperLog").assertExists()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun settingsPageAppliesThemeModeImmediatelyAndSavesReminderLeadDays() {
        clearPreferences()
        try {
            var appliedThemeMode: ThemeMode? = null
            start(onThemeModeChange = { appliedThemeMode = it })

            repeat(4) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }

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
            start()

            repeat(5) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }

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
            start()

            repeat(5) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }

            compose.onNodeWithText("Wetter & Karte (Internet)").performClick()

            assertTrue(WeatherSettings(context).values.value)
            // Der Standort-Schalter bleibt von der Wetter-Umschaltung unberührt: zwei unabhängige Schalter.
            assertFalse(LocationSettings(context).values.value)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun replayFromAboutShowsTheCurrentLocationSwitchStateOnTheOptInPage() {
        clearPreferences()
        try {
            IntroductionSettings(context).seen = true
            LocationSettings(context).enabled = true

            start()
            compose.onNodeWithContentDescription("Einstellungen").performClick()
            scrollUntilVisible("Über CamperLog")
            compose.onNodeWithText("Über CamperLog").performClick()
            compose.onNodeWithText("Einführung erneut anzeigen").performClick()
            repeat(5) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }

            compose.onNode(isToggleable() and isOn()).assertExists()
            assertTrue(LocationSettings(context).values.value)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun replayFromAboutShowsTheTourAgainWithoutResettingOtherSettings() {
        clearPreferences()
        try {
            IntroductionSettings(context).seen = true
            ReminderSettings(context).reminderLeadDays = 14

            start()

            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()

            compose.onNodeWithContentDescription("Einstellungen").performClick()
            scrollUntilVisible("Über CamperLog")
            compose.onNodeWithText("Über CamperLog").performClick()
            compose.onNodeWithText("Einführung erneut anzeigen").performClick()

            compose.onNodeWithText("Willkommen bei CamperLog").assertExists()
            assertTrue(IntroductionSettings(context).seen)
            assertEquals(14, ReminderSettings(context).reminderLeadDays)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun keepAndroidOpenDialogDoesNotShowWhileTheTourIsVisible() {
        clearPreferences()
        try {
            val entry = LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = LocalDate.now(), createdAt = Instant.EPOCH)

            start(logs = FakeLogRepository(listOf(entry)))

            compose.onNodeWithText("Willkommen bei CamperLog").assertExists()
            compose.onNodeWithText("Android droht die Abschottung").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }
}
