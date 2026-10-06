package app.restvolt.camperlog.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationProvider
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

/** Fahrzeug-Datenblatt: Karte „Panne & Unfall", Restzuladung, Wähl-Intent und "Wo bin ich?". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class VehicleBreakdownFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun clearLocationPreference() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("location", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setLocationEnabled(enabled: Boolean) {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("location", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).commit()
    }

    private fun start(vehicle: Vehicle, locationProvider: LocationProvider = FakeLocationProvider()) {
        val vehicles = FakeVehicleRepository(listOf(vehicle))
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    repository,
                    vehicles,
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    locationProvider = locationProvider,
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

    @Test
    fun whereAmI_hiddenWhenLocationIsOff() {
        clearLocationPreference()
        try {
            start(vehicleWithBreakdownInfo())

            compose.onNodeWithText("Wo bin ich?").assertDoesNotExist()
        } finally {
            clearLocationPreference()
        }
    }

    @Test
    fun whereAmI_showsTheFixLargeAndCopiesItToTheClipboard() {
        clearLocationPreference()
        try {
            setLocationEnabled(true)
            val fix = LocationFix(68.0912, 13.1023, accuracyM = 8)
            start(vehicleWithBreakdownInfo(), locationProvider = FakeLocationProvider(freshFix = fix))
            shadowOf(compose.activity.application).grantPermissions(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
            )

            compose.onNodeWithText("Wo bin ich?").performClick()
            compose.onNodeWithText("68,0912° N · 13,1023° E · GPS ±8 m").assertExists()
            compose.onNodeWithText("Koordinaten kopieren").performClick()

            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals(true, clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.contains("geo:68.0912,13.1023"))
        } finally {
            clearLocationPreference()
        }
    }
}
