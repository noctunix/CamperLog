package app.restvolt.camperlog.ui

import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fahrzeug-Datenblatt: Karte „Panne & Unfall", Restzuladung und Wähl-Intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class VehicleBreakdownFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(vehicle: Vehicle) {
        val vehicles = FakeVehicleRepository(listOf(vehicle))
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    repository,
                    vehicles,
                    FakeLogRepository(),
                    FakeExchangeRateRepository(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                ) { }
            }
        }
        compose.onNodeWithText("Fahrzeug").performClick()
    }

    private fun vehicleWithBreakdownInfo() = defaultVehicle(name = "Bluebird").copy(
        grossWeightKg = 3_500,
        measuredEmptyWeightKg = 3_020,
        breakdownProvider = "ADAC",
        breakdownMembershipNumber = "123 456 789",
        breakdownPhone = "+49 89 22 22 22",
    )

    @Test
    fun breakdownCard_hiddenWithoutData() {
        start(defaultVehicle(name = "Bluebird"))

        compose.onNodeWithText("Panne & Unfall").assertDoesNotExist()
    }

    @Test
    fun breakdownCard_shownWithDataAndPayload() {
        start(vehicleWithBreakdownInfo())

        compose.onNodeWithText("Panne & Unfall").assertExists()
        compose.onNodeWithText("Pannenhilfe · ADAC").assertExists()
        compose.onNodeWithText("Mitgliedsnummer 123 456 789").assertExists()
        compose.onNodeWithText("+49 89 22 22 22").assertExists()
        compose.onNodeWithText("480 kg").assertExists()
    }

    @Test
    fun breakdownCard_negativePayloadShowsOverweightWarning() {
        start(vehicleWithBreakdownInfo().copy(measuredEmptyWeightKg = 3_600))

        compose.onNodeWithText("Überladen um 100 kg").assertExists()
    }

    @Test
    fun tappingPhoneRow_firesDialIntent() {
        start(vehicleWithBreakdownInfo())

        compose.onNodeWithText("+49 89 22 22 22").performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("tel:+4989222222", started.data.toString())
    }

    @Test
    fun tappingPhoneRow_withoutDialerApp_offersCopyToClipboard() {
        start(vehicleWithBreakdownInfo())
        shadowOf(compose.activity.application).checkActivities(true)

        compose.onNodeWithText("+49 89 22 22 22").performClick()
        compose.onNodeWithText("Keine Telefon-App gefunden").assertExists()
        compose.onNodeWithText("Nummer kopieren").performClick()

        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        assertEquals("+49 89 22 22 22", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
