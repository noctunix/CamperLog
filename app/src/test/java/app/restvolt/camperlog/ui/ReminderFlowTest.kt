package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Abläufe rund um Erinnerungen: Karte im Fahrzeug-Reiter, Badge der Navigation, Einstellungen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class ReminderFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository): FakeVehicleRepository {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeStationRepository(), FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false) { }
            }
        }
        return vehicles
    }

    private fun vehicleDueInDays(days: Long) = Vehicle(
        id = 1,
        uuid = "vehicle-1",
        name = "Camper",
        nextInspectionDate = LocalDate.now().plusDays(days),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun reminderCard_appearsForADueInspectionAndOpensTheVehicleForm() {
        start(FakeVehicleRepository(listOf(vehicleDueInDays(20))))
        compose.onNodeWithText("Fahrzeug").performClick()

        compose.onNode(hasText("fällig in", substring = true)).performClick()

        compose.onNode(hasText("Name")).assertExists()
    }

    @Test
    fun reminderCard_disappearsOnceTheDateMovesBeyondTheLeadWindow() {
        val vehicles = start(FakeVehicleRepository(listOf(vehicleDueInDays(20))))
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNode(hasText("fällig in", substring = true)).assertExists()

        val vehicle = vehicles.vehicles.single()
        runBlocking { vehicles.save(vehicle.copy(nextInspectionDate = LocalDate.now().plusDays(90))) }

        compose.onNode(hasText("fällig in", substring = true)).assertDoesNotExist()
    }

    @Test
    fun navigationBadge_announcesReminderCountOnTheVehicleTab() {
        start(FakeVehicleRepository(listOf(vehicleDueInDays(20))))

        compose.onNodeWithContentDescription("Fahrzeug, 1 Erinnerung").assertExists()
    }

    @Test
    fun changingReminderLeadDaysInSettings_hidesACardThatIsNowOutOfWindow() {
        start(FakeVehicleRepository(listOf(vehicleDueInDays(20))))
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNode(hasText("fällig in", substring = true)).assertExists()

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Vor Fälligkeit erinnern").performClick()
        compose.onNodeWithText("7 Tage vorher").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNode(hasText("fällig in", substring = true)).assertDoesNotExist()
    }
}
