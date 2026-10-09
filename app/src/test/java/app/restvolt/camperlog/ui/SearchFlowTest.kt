package app.restvolt.camperlog.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Volltextsuche über den Hauptreiter-Weg: öffnen, eintippen, Treffer antippen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class SearchFlowTest {

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

    private fun tour(id: Long, destination: String) = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(2026, 6, 1),
        endDate = LocalDate.of(2026, 6, 3),
        destination = destination,
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costs = listOf(Money(8_990, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun openFromToursTab_typeQuery_tapResult_opensTourDetail() {
        start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Suchen").performClick()
        compose.onNodeWithText("Mindestens 2 Zeichen eingeben").assertExists()

        compose.onNode(hasSetTextAction() and hasText("Suchen")).performTextInput("garda")

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Gardasee").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Touren (1)").assertExists()

        compose.onNodeWithText("Gardasee").performClick()

        compose.onNodeWithContentDescription("Bearbeiten").assertExists()
    }

    @Test
    fun shortQuery_showsHint_andClearingResetsIt() {
        start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Suchen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Suchen")).performTextInput("g")

        compose.onNodeWithText("Mindestens 2 Zeichen eingeben").assertExists()

        compose.onNodeWithContentDescription("Suche leeren").assertExists()
    }

    @Test
    fun noMatch_showsNoResultsHint() {
        start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Suchen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Suchen")).performTextInput("ostsee")

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Keine Treffer").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
