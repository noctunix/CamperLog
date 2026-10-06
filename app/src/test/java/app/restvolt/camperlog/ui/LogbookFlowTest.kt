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
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.theme.CamperLogTheme
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
    ): FakeLogRepository {
        val repository = FakeTourRepository(emptyList()) { vehicles.currentVehicleId }
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, logs, FakeStationRepository(), FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false) { }
            }
        }
        compose.onNodeWithText("Bordbuch").performClick()
        return logs
    }

    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    @Test
    fun tapToday_recordsEntryOnTheTileAndUndoRemovesItAgain() {
        start()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(5)
        compose.onAllNodesWithText("Heute").assertCountEquals(5)

        // Erste Kachel ist "Kassette geleert".
        compose.onAllNodesWithText("Heute")[0].performClick()

        compose.onNodeWithText("Kassette geleert für heute erfasst").assertExists()
        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(4)
        // Fünf Buttons plus der neue Datumstext der Kachel.
        compose.onAllNodesWithText("Heute").assertCountEquals(6)

        compose.onNodeWithText("Rückgängig").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(5)
        compose.onAllNodesWithText("Heute").assertCountEquals(5)
    }

    @Test
    fun otherDate_viaPicker_recordsEntryWithUndoOffer() {
        start()

        // Zweite Kachel ist "Grauwasser abgelassen".
        compose.onAllNodesWithText("Anderes Datum…")[1].performClick()
        compose.onNode(dayCell(1)).performClick()
        compose.onNodeWithText("OK").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(4)
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
    fun tiles_showOnlyTheCurrentVehiclesEntries() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        val today = LocalDate.now()
        val logs = FakeLogRepository(listOf(LogEntry(1, "log-1", vehicleId = 1, type = LogType.CASSETTE_EMPTIED, date = today, createdAt = Instant.EPOCH)))
        start(vehicles, logs)

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(4)
        compose.onAllNodesWithText("Heute").assertCountEquals(6)

        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Wohnmobil B").performClick()

        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(5)
        compose.onAllNodesWithText("Heute").assertCountEquals(5)
    }
}
