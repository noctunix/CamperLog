package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Abläufe rund um das Tagebuch einer Tour: Anlegen und Löschen mit Rückgängig. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class DiaryFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vararg tours: Tour): FakeTourRepository {
        val repository = FakeTourRepository(tours.toList())
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    repository,
                    FakeVehicleRepository(),
                    FakeLogRepository(),
                    FakeStationRepository(),
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    AccentColor.AZURE,
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                    tracks = FakeTrackRepository(),
                    onAccentColorChange = { },
                ) { }
            }
        }
        return repository
    }

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

    private fun textField() = compose.onNode(hasSetTextAction() and hasText("Text"))

    private fun tour(id: Long = 1, destination: String = "Lofoten") = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 10),
        destination = destination,
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = listOf(Money(8_990, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun diarySectionIsAlwaysVisibleEvenWhenEmpty() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()

        compose.onNodeWithText("Tagebuch").assertExists()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").assertExists()
    }

    @Test
    fun addDiaryEntry_appearsInTheSection() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        textField().performTextInput("Langer Tag am Fjord.")
        clickSave()

        compose.onNodeWithText("Langer Tag am Fjord.").assertExists()
    }

    @Test
    fun textRequired_forNewDiaryEntry() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        clickSave()

        compose.onNodeWithText("Text erforderlich").assertExists()
    }

    @Test
    fun deleteDiaryEntry_undo_restoresIt() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()
        textField().performTextInput("Langer Tag am Fjord.")
        clickSave()

        compose.onNodeWithText("Langer Tag am Fjord.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Tagebucheintrag löschen").performClick()

        compose.onNodeWithText("Tagebucheintrag gelöscht").assertExists()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Langer Tag am Fjord.").assertExists()
    }
}
