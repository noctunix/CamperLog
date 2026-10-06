package app.restvolt.camperlog.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Deep Links aus getippten Benachrichtigungen: eine Wartungs-Benachrichtigung wählt ihr
 * Fahrzeug aus und öffnet den Fahrzeug-Reiter, eine Sicherungs-Erinnerung öffnet den Daten-Screen.
 * Analog zu `StationFlowTest`s `pendingGeoIntent`-Tests, nur dass hier direkt beim Start statt über
 * ein Formular navigiert wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class NotificationNavigationFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun vehicle(id: Long, name: String) = Vehicle(id = id, uuid = "vehicle-$id", name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun pendingVehicleId_selectsTheVehicleAndOpensItsTab() {
        val vehicles = FakeVehicleRepository(initial = listOf(vehicle(1, "Erstes"), vehicle(2, "Zweites")), currentVehicleId = 1)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(),
                    vehicles,
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    pendingVehicleId = 2,
                ) {}
            }
        }
        compose.waitForIdle()

        assertEquals(2L, vehicles.currentVehicleId)
        compose.onNodeWithText("Zweites").assertExists()
        compose.onNodeWithText("Reparatur hinzufügen").assertExists()
    }

    @Test
    fun pendingOpenData_opensTheDataScreen() {
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    FakeTourRepository(),
                    FakeVehicleRepository(),
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    pendingOpenData = true,
                ) {}
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Sicherung (JSON)").assertExists()
    }
}
