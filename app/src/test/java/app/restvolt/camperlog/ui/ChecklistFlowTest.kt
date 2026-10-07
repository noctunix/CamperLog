package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Checklisten-Abläufe an einer Tour: Vorschläge einfügen, Checkliste starten, Punkt abhaken. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class ChecklistFlowTest {

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
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                ) { }
            }
        }
        return repository
    }

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
    fun addSuggestedTemplates_startChecklistOnATour_tickItem_updatesProgress() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Checkliste starten").performScrollTo().performClick()

        // Ohne Vorlagen bietet die Auswahl zuerst die Vorschläge an.
        compose.onNodeWithText("Vorlagen-Vorschläge hinzufügen").performClick()
        compose.onNodeWithText("Abfahrt").performClick()

        compose.onNodeWithText("0 von 8 erledigt").assertExists()
        compose.onNodeWithText("Dachluken und Fenster schließen").performClick()

        compose.onNodeWithText("1 von 8 erledigt").assertExists()
    }

    @Test
    fun startedChecklistAppearsInTheTourSectionWithItsProgress() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Checkliste starten").performScrollTo().performClick()
        compose.onNodeWithText("Vorlagen-Vorschläge hinzufügen").performClick()
        compose.onNodeWithText("Ankunft").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNode(hasText("Ankunft") and hasText("0/6")).assertExists()
    }
}
