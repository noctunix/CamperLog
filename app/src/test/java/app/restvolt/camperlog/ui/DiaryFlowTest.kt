package app.restvolt.camperlog.ui

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

/** Abläufe rund um das Tagebuch einer Tour: implizites Anlegen, Verwerfen/Speichern beim Verlassen, Löschen mit Rückgängig. */
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
                    formDraftStore = FakeFormDraftStore(),
                ) { }
            }
        }
        return repository
    }

    private fun textField() = compose.onNode(hasSetTextAction() and hasText("Text"))

    /** Verlässt das (implizit schon automatisch gespeicherte) Formular ohne jede Rückfrage. */
    private fun backWithoutDialog() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForIdle()
    }

    /** Verlässt über die Verwerfen/Speichern-Abfrage und behält die Änderungen. */
    private fun backKeepingChanges() {
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Formular verlassen?").assertExists()
        compose.onNodeWithText("So lassen").performClick()
        compose.waitForIdle()
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

    /**
     * Der Haken in der Kopfzeile verlässt sofort, ohne die Verwerfen/Speichern-Abfrage zu zeigen -
     * Autosave hat die Eingabe schon gespeichert.
     */
    @Test
    fun saveCheckmark_leavesImmediatelyWithoutLeaveDialog() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()
        textField().performTextInput("Langer Tag am Fjord.")

        compose.onNodeWithContentDescription("Speichern").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Langer Tag am Fjord.").assertExists()
    }

    @Test
    fun diarySectionIsAlwaysVisibleEvenWhenEmpty() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()

        compose.onNodeWithText("Tagebucheintrag hinzufügen").assertExists()
    }

    @Test
    fun addDiaryEntry_appearsInTheSection() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        textField().performTextInput("Langer Tag am Fjord.")
        backKeepingChanges()

        compose.onNodeWithText("Langer Tag am Fjord.").assertExists()
    }

    /** Eine ungültige Eingabe zeigt den Fehler sofort inline, ohne jeden Speichern-Versuch. */
    @Test
    fun invalidOdometer_forNewDiaryEntry_showsError() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        compose.onNode(hasSetTextAction() and hasText("km-Stand")).performScrollTo().performTextInput("abc")

        compose.onNodeWithText("Ungültige Zahl").assertExists()
    }

    /** Schon die erste Eingabe in ein beliebiges anderes Feld macht den Pflichtfeld-Fehler des Texts sichtbar. */
    @Test
    fun textRequired_isShownAsSoonAsAnyOtherFieldIsTouched() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        compose.onNode(hasSetTextAction() and hasText("Wetter")).performScrollTo().performTextInput("Sonne")

        compose.onNodeWithText("Text erforderlich").assertExists()
    }

    /** Ein neuer Eintrag speichert sich schon laufend selbst - ohne jede Eingabe bleibt also keine Karteileiche zurück. */
    @Test
    fun leavingNewDiaryEntryWithoutAnyInput_discardsItSilentlyWithoutAsking() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()

        backWithoutDialog()

        compose.onNodeWithText("Formular verlassen?").assertDoesNotExist()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").assertExists()
    }

    @Test
    fun deleteDiaryEntry_undo_restoresIt() {
        start(tour())
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Tagebuch").performScrollTo().performClick()
        compose.onNodeWithText("Tagebucheintrag hinzufügen").performScrollTo().performClick()
        textField().performTextInput("Langer Tag am Fjord.")
        backKeepingChanges()

        compose.onNodeWithText("Langer Tag am Fjord.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Tagebucheintrag löschen").performClick()

        compose.onNodeWithText("Tagebucheintrag gelöscht").assertExists()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Langer Tag am Fjord.").assertExists()
    }
}
