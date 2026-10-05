package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Abläufe der Fahrzeugverwaltung: Anlegen, als aktuell festlegen, Löschen (zugelassen und abgelehnt). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class VehiclesFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository) {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM) { }
            }
        }
    }

    private fun openManageVehicles() {
        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Fahrzeuge verwalten").performClick()
    }

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

    @Test
    fun addVehicle_appearsInListButDoesNotBecomeCurrent() {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Wohnmobil A")))
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithText("Fahrzeug hinzufügen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wohnmobil B")
        clickSave()

        compose.onNodeWithText("Wohnmobil B").assertExists()
        assertEquals(2, vehicles.vehicles.size)
        assertEquals(1L, vehicles.currentVehicleId)
    }

    @Test
    fun setAsCurrent_updatesCurrentVehicleAndHidesItsOwnEntry() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithContentDescription("Weitere Aktionen für Wohnmobil B").performClick()
        compose.onNodeWithText("Als aktuell festlegen").performClick()

        assertEquals(2L, vehicles.currentVehicleId)

        compose.onNodeWithContentDescription("Weitere Aktionen für Wohnmobil B").performClick()
        compose.onNodeWithText("Als aktuell festlegen").assertDoesNotExist()
    }

    @Test
    fun deleteVehicle_confirmed_removesIt() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithContentDescription("Weitere Aktionen für Wohnmobil B").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Fahrzeug löschen?").assertExists()
        compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Wohnmobil B").assertDoesNotExist()
        assertEquals(1, vehicles.vehicles.size)
    }

    @Test
    fun deleteVehicle_refusedBecauseOfTours_explainsReasonAndKeepsIt() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
            tourCount = { id -> if (id == 2L) 1 else 0 },
        )
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithContentDescription("Weitere Aktionen für Wohnmobil B").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Fahrzeug kann nicht gelöscht werden").assertExists()
        compose.onNodeWithText("Wohnmobil B").assertExists()
        assertEquals(2, vehicles.vehicles.size)
    }

    @Test
    fun deleteVehicle_refusedAsLastVehicle_explainsReason() {
        val vehicles = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Einziges Fahrzeug")))
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithContentDescription("Weitere Aktionen für Einziges Fahrzeug").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Das ist das letzte verbleibende Fahrzeug. CamperLog benötigt mindestens eines.").assertExists()
        assertEquals(1, vehicles.vehicles.size)
    }

    @Test
    fun rowTap_opensEditFormInsteadOfMakingItCurrent() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles)
        openManageVehicles()

        compose.onNodeWithText("Wohnmobil B").performClick()

        compose.onNodeWithText("Fahrzeug bearbeiten").assertExists()
        assertEquals(1L, vehicles.currentVehicleId)
    }
}
