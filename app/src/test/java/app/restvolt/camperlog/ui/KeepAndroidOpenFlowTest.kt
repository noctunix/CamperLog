package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.ui.about.KeepAndroidOpenSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Startablauf des „Keep Android Open“-Hinweises in [CamperLogNavHost]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class KeepAndroidOpenFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clearPreferences() {
        context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start(logs: FakeLogRepository, vehicles: FakeVehicleRepository = FakeVehicleRepository()) {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, logs, FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM) { }
            }
        }
    }

    @Test
    fun eligibleOnceDataExistsAndSupportingSuppressesItPermanently() {
        clearPreferences()
        try {
            val entry = LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = LocalDate.now(), createdAt = Instant.EPOCH)
            start(FakeLogRepository(listOf(entry)))

            compose.onNodeWithText("Android droht die Abschottung").assertExists()

            compose.onNodeWithText("Ich habe die Petition unterstützt").performClick()

            compose.onNodeWithText("Android droht die Abschottung").assertDoesNotExist()
            assertTrue(KeepAndroidOpenSettings(context).state.supported)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun freshInstallWithoutDataDoesNotShowItYet() {
        clearPreferences()
        try {
            start(FakeLogRepository())

            compose.onNodeWithText("Android droht die Abschottung").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun oncePermanentlySupportedItStaysSuppressedEvenWithData() {
        clearPreferences()
        try {
            KeepAndroidOpenSettings(context).markSupported()
            val entry = LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = LocalDate.now(), createdAt = Instant.EPOCH)

            start(FakeLogRepository(listOf(entry)))

            compose.onNodeWithText("Android droht die Abschottung").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }
}
