package de.hannes.camperlog.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import de.hannes.camperlog.domain.ElectricityFlatRate
import de.hannes.camperlog.domain.LteQuality
import de.hannes.camperlog.domain.PitchSlope
import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.TourType
import de.hannes.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** End-to-end-Abläufe durch Navigation, Screens und ViewModels gegen ein Fake-Repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TourFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vararg tours: Tour): FakeTourRepository {
        val repository = FakeTourRepository(tours.toList())
        compose.setContent { CamperLogTheme { CamperLogNavHost(repository) } }
        return repository
    }

    private fun destinationField() = compose.onNode(hasSetTextAction() and hasText("Ziel"))

    /** Speichert über den Button am Formularende, nicht über die Aktion in der App-Leiste. */
    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performClick()
        // Tageszellen tragen ihr volles Datum als Text, z. B. "Saturday, October 10, 2026".
        compose.onNode(hasText(" $day, ", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun createTour_appearsInListAndIsStored() {
        val repository = start()
        compose.onNodeWithText("Noch keine Touren. Lege mit „Eingabe“ die erste Fahrt an.").assertExists()

        compose.onNodeWithText("Eingabe").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Gardasee")
        clickSave()

        compose.onNodeWithText("Gardasee").assertExists()
        val saved = repository.tours.single()
        assertEquals("Gardasee", saved.destination)
        assertEquals(10, saved.startDate.dayOfMonth)
        assertEquals(12, saved.endDate.dayOfMonth)
        assertEquals(3, saved.travelDays)
    }

    @Test
    fun saveWithoutRequiredFields_showsErrorsAndStaysOnForm() {
        val repository = start()
        compose.onNodeWithText("Eingabe").performClick()
        clickSave()

        compose.onNodeWithText("Ziel erforderlich").assertExists()
        compose.onNodeWithText("Startdatum erforderlich").assertExists()
        compose.onNodeWithText("Neue Tour").assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun editTour_updatesDetailAndList() {
        val repository = start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithText("Gardasee").performClick()
        compose.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        destinationField().performTextReplacement("Comer See")
        clickSave()

        // Zurück in der Detailansicht: Titel und Ziel-Zeile zeigen das neue Ziel.
        compose.onNodeWithText("Bearbeiten").assertExists()
        compose.onAllNodesWithText("Comer See").assertCountEquals(2)
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Comer See").assertExists()
        compose.onNodeWithText("Gardasee").assertDoesNotExist()
        assertEquals("Comer See", repository.tours.single().destination)
    }

    @Test
    fun deleteTour_afterConfirmation_removesIt() {
        val repository = start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Tour nach Gardasee löschen").performClick()
        compose.onNodeWithText("Tour löschen?").assertExists()
        compose.onNodeWithText("Löschen").performClick()

        compose.onNodeWithText("Noch keine Touren. Lege mit „Eingabe“ die erste Fahrt an.").assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun deleteTour_cancelled_keepsIt() {
        val repository = start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Tour nach Gardasee löschen").performClick()
        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNodeWithText("Tour löschen?").assertDoesNotExist()
        compose.onNodeWithText("Gardasee").assertExists()
        assertEquals(1, repository.tours.size)
    }

    @Test
    fun leavingDirtyForm_asksBeforeDiscarding() {
        val repository = start()
        compose.onNodeWithText("Eingabe").performClick()
        destinationField().performTextInput("Ostsee")

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertExists()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.onNodeWithText("Neue Tour").assertExists()

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.onNodeWithText("Touren").assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun search_filtersListByDestination() {
        start(tour(id = 1, destination = "Gardasee"), tour(id = 2, destination = "Ostsee"))

        compose.onNode(hasSetTextAction() and hasText("Ziel suchen")).performTextInput("ost")

        compose.onNodeWithText("Ostsee").assertExists()
        compose.onNodeWithText("Gardasee").assertDoesNotExist()
    }

    private fun tour(id: Long, destination: String) = Tour(
        id = id,
        startDate = LocalDate.of(2025, 6, 1),
        endDate = LocalDate.of(2025, 6, 3),
        destination = destination,
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costCents = 8_990,
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
