package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
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

    private fun clearPreferences() {
        context.getSharedPreferences("introduction", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
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
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
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
    fun replayFromAboutShowsTheTourAgainWithoutResettingOtherSettings() {
        clearPreferences()
        try {
            IntroductionSettings(context).seen = true
            ReminderSettings(context).reminderLeadDays = 14

            start()

            compose.onNodeWithText("Willkommen bei CamperLog").assertDoesNotExist()

            compose.onNodeWithContentDescription("Einstellungen").performClick()
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
