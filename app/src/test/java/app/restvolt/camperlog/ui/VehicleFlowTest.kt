package app.restvolt.camperlog.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Abläufe rund um den Fahrzeugwechsler, die Fahrzeugfilterung der Touren und die untere Navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class VehicleFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vehicles: FakeVehicleRepository, vararg tours: Tour): FakeTourRepository {
        val repository = FakeTourRepository(tours.toList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM) { }
            }
        }
        return repository
    }

    private fun destinationField() = compose.onNode(hasSetTextAction() and hasText("Ziel"))

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performClick()
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun switcher_isHiddenWithOneVehicle() {
        start(FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Einziges Fahrzeug"))))

        compose.onNodeWithText("Tourenlog").assertExists()
        compose.onNodeWithContentDescription("Einziges Fahrzeug, Fahrzeug wechseln").assertDoesNotExist()
    }

    @Test
    fun switcher_isVisibleWithTwoVehicles() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles)

        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").assertExists()
        compose.onNodeWithText("Tourenlog").assertDoesNotExist()
    }

    @Test
    fun allVehicles_showsToursOfBothVehiclesWithVehicleName() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles, tour(1, "Gardasee", vehicleId = 1), tour(2, "Ostsee", vehicleId = 2))

        // Nur das aktuelle Fahrzeug ist zunächst sichtbar.
        compose.onNodeWithText("Gardasee").assertExists()
        compose.onNodeWithText("Ostsee").assertDoesNotExist()

        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Alle Fahrzeuge").performClick()

        compose.onNodeWithText("Gardasee").assertExists()
        compose.onNodeWithText("Ostsee").assertExists()
        compose.onAllNodesWithText("Wohnmobil A", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("Wohnmobil B", substring = true).assertCountEquals(1)
    }

    @Test
    fun pickingVehicle_filtersToursAndLeavesAllVehiclesMode() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        start(vehicles, tour(1, "Gardasee", vehicleId = 1), tour(2, "Ostsee", vehicleId = 2))

        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Alle Fahrzeuge").performClick()
        compose.onNodeWithContentDescription("Alle Fahrzeuge, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Wohnmobil B").performClick()

        compose.onNodeWithText("Ostsee").assertExists()
        compose.onNodeWithText("Gardasee").assertDoesNotExist()
        assertEquals(2L, vehicles.currentVehicleId)
    }

    @Test
    fun tourForm_vehicleDropdownDefaultsToCurrentVehicleAndCanBeChanged() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 2,
        )
        val repository = start(vehicles)

        compose.onNodeWithText("Neue Tour").performClick()
        compose.onNode(hasText("Wohnmobil B") and hasClickAction()).assertExists()

        compose.onNode(hasText("Wohnmobil B") and hasClickAction()).performClick()
        compose.onNodeWithText("Wohnmobil A").performClick()

        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Harz")
        clickSave()

        assertEquals(1L, repository.tours.single().vehicleId)
    }

    @Test
    fun bottomNavigation_switchesBetweenTabs() {
        start(FakeVehicleRepository())

        compose.onNodeWithText("Bordbuch").performClick()
        compose.onNode(hasText("Bordbuch") and isHeading()).assertExists()

        compose.onNodeWithText("Fahrzeug").performClick()
        compose.onNode(hasText("Fahrzeug") and isHeading()).assertExists()

        compose.onNodeWithText("Touren").performClick()
        compose.onNodeWithText("Tourenlog").assertExists()
    }

    private fun tour(id: Long, destination: String, vehicleId: Long) = Tour(
        id = id,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2025, 6, 1),
        endDate = LocalDate.of(2025, 6, 3),
        destination = destination,
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costs = listOf(Money(8_990, EUR)),
        pitchAssigned = true,
        electricityFlatRate = ElectricityFlatRate.YES,
        lteQuality = LteQuality.GOOD,
        pitchSlope = PitchSlope.LEVEL,
        levelingBlocksUsed = false,
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
