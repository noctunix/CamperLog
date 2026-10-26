package app.restvolt.camperlog.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.formatDate
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
import java.util.Locale

/** Abläufe im Bordbuch-Reiter: Schnellerfassung je Kachel, Verlauf, Löschen mit Rückgängig, Fahrzeugtrennung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class LogbookFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
        logs: FakeLogRepository = FakeLogRepository(),
        stations: List<Station> = emptyList(),
    ): FakeLogRepository {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, logs, FakeStationRepository(stations, logs = logs), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }, formDraftStore = FakeFormDraftStore()) { }
            }
        }
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Bordbuch").performClick()
        return logs
    }

    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    @Test
    fun tapToday_recordsEntryOnTheTileAndUndoRemovesItAgain() {
        start()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(8)
        compose.onAllNodesWithText("Heute").assertCountEquals(8)

        // Erste Kachel ist "Kassette geleert".
        compose.onAllNodesWithText("Heute")[0].performClick()

        compose.onNodeWithText("Kassette geleert für heute erfasst · Station verknüpft; im Verlauf öffnen.").assertExists()
        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(7)
        // Sieben Buttons plus der neue Datumstext der Kachel.
        compose.onAllNodesWithText("Heute").assertCountEquals(9)

        compose.onNodeWithText("Rückgängig").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(8)
        compose.onAllNodesWithText("Heute").assertCountEquals(8)
    }

    @Test
    fun otherDate_viaPicker_recordsEntryWithUndoOffer() {
        start()

        // Zweite Kachel ist "Grauwasser abgelassen".
        compose.onAllNodesWithText("Anderes Datum…")[1].performClick()
        compose.onNode(dayCell(1)).performClick()
        compose.onNodeWithText("OK").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(7)
        compose.onNode(hasText("Grauwasser abgelassen für", substring = true)).assertExists()
        compose.onNodeWithText("Rückgängig").assertExists()
    }

    @Test
    fun history_listsEntriesNewestFirstAndDeleteOffersUndo() {
        val today = LocalDate.now()
        val older = LogEntry(1, "log-1", vehicleId = 1, type = LogType.DIESEL_HEATER_RUN, date = today.minusDays(10), createdAt = Instant.EPOCH)
        val newer = LogEntry(2, "log-2", vehicleId = 1, type = LogType.DIESEL_HEATER_RUN, date = today.minusDays(2), createdAt = Instant.EPOCH)
        start(logs = FakeLogRepository(listOf(older, newer)))

        compose.onNodeWithText("Dieselheizung betrieben").performClick()

        val rows = compose.onAllNodes(hasText("vor ", substring = true))
        rows.assertCountEquals(2)
        rows[0].assertTextContains("vor 2", substring = true)
        rows[1].assertTextContains("vor 10", substring = true)

        val deleteNewest = "Eintrag vom ${formatDate(newer.date, Locale.GERMANY)} löschen"
        compose.onNodeWithContentDescription(deleteNewest).performClick()

        compose.onNodeWithText("Eintrag gelöscht").assertExists()
        compose.onAllNodes(hasText("vor ", substring = true)).assertCountEquals(1)

        compose.onNodeWithText("Rückgängig").performClick()

        compose.onAllNodes(hasText("vor ", substring = true)).assertCountEquals(2)
    }

    @Test
    fun history_tapOnEntryShowsReadOnlyFactsOfItsStation() {
        val today = LocalDate.now()
        val station = Station(
            id = 7, vehicleId = 1, tourId = null, type = StationType.FUEL, date = today, name = "Aral Hamburg",
            costs = listOf(StationCost(CostCategory.FUEL, Money(5000, java.util.Currency.getInstance("EUR")))),
            rating = 4, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val linked = LogEntry(1, "log-1", vehicleId = 1, type = LogType.FUELED, date = today, createdAt = Instant.EPOCH, stationId = 7)
        start(logs = FakeLogRepository(listOf(linked)), stations = listOf(station))

        compose.onNodeWithText("Getankt").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Details zu diesem Eintrag anzeigen").performClick()

        compose.onNode(hasText("Kosten: 50,00", substring = true)).assertExists()
        compose.onNodeWithContentDescription("4 von 5 Campern").assertExists()
    }

    @Test
    fun history_tapOnUnlinkedEntryShowsThatThereAreNoFurtherDetails() {
        val today = LocalDate.now()
        val entry = LogEntry(1, "log-1", vehicleId = 1, type = LogType.DIESEL_HEATER_RUN, date = today, createdAt = Instant.EPOCH)
        start(logs = FakeLogRepository(listOf(entry)))

        compose.onNodeWithText("Dieselheizung betrieben").performClick()
        compose.onNode(hasText("Heute")).performClick()

        compose.onNodeWithText("Keine weiteren Angaben zu diesem Eintrag.").assertExists()
    }

    @Test
    fun quickAccessRowOnToursTab_sharesStateWithLogbookScreen() {
        val vehicles = FakeVehicleRepository()
        val logs = FakeLogRepository()
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, logs, FakeStationRepository(logs = logs), FakeExchangeRateRepository(), FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), ThemeMode.SYSTEM, AccentColor.AZURE, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository(), tracks = FakeTrackRepository(), onAccentColorChange = { }, formDraftStore = FakeFormDraftStore()) { }
            }
        }

        // Erste Kachel ist "Kassette geleert", über ihre Schnellzugriffs-Reihe auf dem Touren-Reiter erfasst.
        compose.onNodeWithContentDescription("Kassette geleert für heute erfassen").performClick()
        compose.onNodeWithText("Kassette geleert für heute erfasst · Station verknüpft; im Verlauf öffnen.").assertExists()

        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Bordbuch").performClick()

        // Dasselbe LogbookViewModel: die Kachel zeigt den soeben über den Touren-Reiter erfassten Eintrag.
        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(7)
    }

    @Test
    fun tiles_showOnlyTheCurrentVehiclesEntries() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        val today = LocalDate.now()
        val logs = FakeLogRepository(listOf(LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = today, createdAt = Instant.EPOCH)))
        start(vehicles, logs)

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(7)
        compose.onAllNodesWithText("Heute").assertCountEquals(9)

        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Wohnmobil B").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(8)
        compose.onAllNodesWithText("Heute").assertCountEquals(8)
    }
}
