package app.restvolt.camperlog.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/** End-to-end-Abläufe durch Navigation, Screens und ViewModels gegen ein Fake-Repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class TourFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(vararg tours: Tour): FakeTourRepository = start(FakeVehicleRepository(), *tours)

    private fun start(vehicles: FakeVehicleRepository, vararg tours: Tour): FakeTourRepository {
        val repository = FakeTourRepository(tours.toList())
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(repository, vehicles, FakeLogRepository(), FakeExchangeRateRepository(), FakeBackupImporter(), ThemeMode.SYSTEM, canShowStartDialogs = false) { }
            }
        }
        return repository
    }

    private fun destinationField() = compose.onNode(hasSetTextAction() and hasText("Ziel"))

    /** Speichert über den Button am Formularende, nicht über die Aktion in der App-Leiste. */
    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

    /** Kalenderzelle des Tages; das Datumsfeld im Formular trägt dasselbe Datum. */
    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performClick()
        // Tageszellen tragen ihr volles Datum als Text, z. B. "Samstag, 10. Oktober 2026".
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun createTour_appearsInListAndIsStored() {
        val repository = start()
        compose.onNodeWithText("Noch keine Touren. Lege mit „Neue Tour“ die erste Fahrt an.").assertExists()

        compose.onNodeWithText("Neue Tour").performClick()
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
        compose.onNodeWithText("Neue Tour").performClick()
        clickSave()

        compose.onNodeWithText("Ziel erforderlich").assertExists()
        compose.onNodeWithText("Startdatum erforderlich").assertExists()
        destinationField().assertExists()
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
    fun listOffersNoDeleteAction() {
        start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithContentDescription("Tour löschen").assertDoesNotExist()
    }

    @Test
    fun deleteTour_fromDetail_removesItAndOffersUndo() {
        val repository = start(tour(id = 1, destination = "Gardasee"))

        compose.onNodeWithText("Gardasee").performClick()
        compose.onNodeWithContentDescription("Tour löschen").performClick()

        compose.onNodeWithText("Tourenlog").assertExists()
        compose.onNodeWithText("„Gardasee“ gelöscht").assertExists()
        compose.onNodeWithText("Noch keine Touren. Lege mit „Neue Tour“ die erste Fahrt an.").assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun deleteTour_undo_restoresIt() {
        val original = tour(id = 1, destination = "Gardasee")
        val repository = start(original)

        compose.onNodeWithText("Gardasee").performClick()
        compose.onNodeWithContentDescription("Tour löschen").performClick()
        compose.onNodeWithText("Rückgängig").performClick()

        compose.onNodeWithText("Gardasee").assertExists()
        assertEquals(listOf(original), repository.tours)
    }

    @Test
    fun deleteTour_writeFails_keepsItAndReportsError() {
        val repository = start(tour(id = 1, destination = "Gardasee"))
        repository.failWrites = true

        compose.onNodeWithText("Gardasee").performClick()
        compose.onNodeWithContentDescription("Tour löschen").performClick()

        compose.onNodeWithText("Tour konnte nicht gelöscht werden.").assertExists()
        compose.onNodeWithText("Gardasee").assertExists()
        assertEquals(1, repository.tours.size)
    }

    @Test
    fun saveRejected_focusesFirstInvalidFieldAndAnnouncesHint() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        compose.onNode(hasSetTextAction() and hasText("Kosten (€)")).performTextInput("abc")

        clickSave()

        destinationField().assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Bitte diese Felder prüfen: Ziel, Kosten")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun requiredFields_areMarkedUntilAnErrorReplacesTheHint() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()

        compose.onNode(hasText("Startdatum") and hasClickAction()).assert(hasText("Pflichtfeld"))
        compose.onNode(hasText("Enddatum") and hasClickAction()).assert(hasText("Pflichtfeld"))
        destinationField().assert(hasText("Pflichtfeld"))
        compose.onNode(hasSetTextAction() and hasText("Kilometer")).assert(!hasText("Pflichtfeld"))
        compose.onNode(hasSetTextAction() and hasText("Notizen")).assert(!hasText("Pflichtfeld"))

        clickSave()

        destinationField().assert(hasText("Ziel erforderlich")).assert(!hasText("Pflichtfeld"))
        compose.onNodeWithText("Bitte diese Felder prüfen: Startdatum, Enddatum, Ziel").assertExists()
    }

    @Test
    fun screenTitlesAndSections_areHeadings() {
        start(tour(id = 1, destination = "Gardasee").copy(notes = "Ruhiger Platz", mapLink = "https://example.org/karte"))
        compose.onNodeWithText("Tourenlog").assert(isHeading())

        compose.onNodeWithText("Gardasee").performClick()
        compose.onNode(hasText("Gardasee") and isHeading()).assertExists()
        compose.onNodeWithText("Notizen").performScrollTo().assert(isHeading())
        compose.onNodeWithText("Kartenlink").performScrollTo().assert(isHeading())

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Übersicht").performClick()
        compose.onNodeWithText("Übersicht").assert(isHeading())
        compose.onNodeWithText("Gesamt").assert(isHeading())
    }

    @Test
    fun datePicker_usesGermanOnGermanDevice() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()
        compose.onNodeWithContentDescription("Startdatum wählen").performClick()

        compose.onAllNodes(hasText("Montag", substring = true) and hasAnyAncestor(isDialog())).onFirst().assertExists()
        compose.onAllNodes(hasText("Monday", substring = true)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
    fun englishDevice_usesEnglishLabelsAndCalendar() {
        start()
        compose.onNodeWithText("Tour log").assertExists()
        compose.onNodeWithText("New tour").performClick()
        compose.onNodeWithContentDescription("Choose Start date").performClick()
        compose.onAllNodes(hasText("Monday", substring = true) and hasAnyAncestor(isDialog())).onFirst().assertExists()
        compose.onAllNodes(hasText("Montag", substring = true)).assertCountEquals(0)
    }

    @Test
    fun dateField_opensPickerViaScreenReaderClick() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()

        val field = compose.onNode(hasText("Startdatum") and hasClickAction())
        field.assert(
            SemanticsMatcher("OnClick-Label") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Startdatum wählen"
            },
        )
        field.performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("OK").assertExists()
    }

    @Test
    fun dateField_opensPickerViaEnterKey() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()

        val field = compose.onNode(hasText("Startdatum") and hasClickAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithText("OK").assertExists()
    }

    @Test
    fun choiceField_namesGroupInEachOptionAndMarksSelection() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()

        // Gruppenname nur einmal pro Option, nicht als eigener Fokusstopp.
        compose.onAllNodesWithText("Tourart").assertCountEquals(0)
        val vacation = compose.onNodeWithContentDescription("Tourart: Urlaub")
        vacation.performScrollTo().assertIsNotSelected().performClick()

        vacation.assertIsSelected()
        compose.onNodeWithContentDescription("Tourart: Wochenende").assertIsNotSelected()
    }

    @Test
    fun endDatePicker_disablesDaysBeforeStart() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)

        compose.onNodeWithContentDescription("Enddatum wählen").performClick()
        compose.onNode(dayCell(9)).assertIsNotEnabled()
        compose.onNode(dayCell(10)).assertIsEnabled()
        compose.onNode(dayCell(11)).assertIsEnabled()
    }

    @Test
    fun saveRejected_scrollsDownToInvalidField() {
        start()
        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Ostsee")
        compose.onNode(hasSetTextAction() and hasText("Kartenlink")).performTextInput("kein link")
        compose.onNode(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()

        compose.onNode(hasSetTextAction() and hasText("Kartenlink")).assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun saveTour_writeFails_keepsFormAndReportsError() {
        val repository = start()
        repository.failWrites = true

        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Ostsee")
        clickSave()

        compose.onNodeWithText("Tour konnte nicht gespeichert werden.").assertExists()
        compose.onNodeWithText("Neue Tour").assertExists()
        compose.onNodeWithText("Ostsee").assertExists()
        assertEquals(0, repository.tours.size)

        // Der untere Button bleibt neben der Snackbar erreichbar und wird nicht von ihr verdeckt.
        val bottomSave = compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo()
        val snackbarTop = compose.onNodeWithText("Tour konnte nicht gespeichert werden.").getUnclippedBoundsInRoot().top
        val saveBottom = bottomSave.getUnclippedBoundsInRoot().bottom
        assertTrue("Button endet bei $saveBottom, Snackbar beginnt bei $snackbarTop", saveBottom <= snackbarTop)

        repository.failWrites = false
        bottomSave.performClick()

        assertEquals(listOf("Ostsee"), repository.tours.map { it.destination })
        compose.onNodeWithText("Tour gespeichert").assertExists()
    }

    @Test
    fun exportCsv_readFails_reportsError() {
        val repository = start(tour(id = 1, destination = "Gardasee"))
        repository.failExportRead = true

        compose.onNodeWithContentDescription("Daten").performClick()
        compose.onNodeWithText("Als CSV exportieren").performClick()

        compose.onNodeWithText("CSV-Export fehlgeschlagen").assertExists()
        compose.onNodeWithText("Als CSV exportieren").assertIsEnabled()
    }

    @Test
    fun createTour_resetsSearchAndConfirmsSaving() {
        start(tour(id = 1, destination = "Gardasee"))
        compose.onNode(hasSetTextAction() and hasText("Ziel suchen")).performTextInput("garda")

        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Ostsee")
        clickSave()

        compose.onNodeWithText("Tour gespeichert").assertExists()
        compose.onNodeWithText("Ostsee").assertExists()
        compose.onNodeWithText("Gardasee").assertExists()
    }

    @Test
    fun list_offersNewTourFabAndOverviewInTopBar() {
        start(tour(id = 1, destination = "Gardasee"))

        compose.onNode(hasText("Neue Tour") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Übersicht").performClick()

        compose.onNodeWithContentDescription("Zurück").assertExists()
        compose.onNodeWithText("Übersicht").assertExists()
    }

    @Test
    fun leavingDirtyForm_asksBeforeDiscarding() {
        val repository = start()
        compose.onNodeWithText("Neue Tour").performClick()
        destinationField().performTextInput("Ostsee")

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertExists()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        destinationField().assertExists()

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.onNodeWithText("Tourenlog").assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun createTour_withSecondCurrencyFromPicker() {
        val repository = start()
        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Lofoten")
        compose.onNode(hasSetTextAction() and hasText("Kosten (€)")).performScrollTo().performTextInput("12.50")
        compose.onNodeWithContentDescription("Betrag in EUR entfernen").assertDoesNotExist()

        compose.onNodeWithText("Weitere Währung").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Währung: Dänische Krone").performScrollTo().performClick()
        compose.onNodeWithText("EUR · Euro").assertDoesNotExist()
        compose.onNode(hasText("NOK · Norwegische Krone") and isSelectable()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Kosten (NOK)")).performScrollTo().performTextInput("1.450")
        compose.onNodeWithContentDescription("Betrag in NOK entfernen").assertExists()
        clickSave()

        compose.onNodeWithText("Lofoten").assertExists()
        val nok = Currency.getInstance("NOK")
        assertEquals(listOf(Money(1_250, EUR), Money(145_000, nok)), repository.tours.single().costs)
    }

    @Test
    fun createTour_amountTooLarge_showsMaximum() {
        val repository = start()
        compose.onNodeWithText("Neue Tour").performClick()
        pickDay("Startdatum", 10)
        pickDay("Enddatum", 12)
        destinationField().performTextInput("Lofoten")
        compose.onNode(hasSetTextAction() and hasText("Kosten (€)")).performScrollTo().performTextInput("100000000000,01")
        clickSave()

        compose.onNodeWithText("Betrag zu groß, höchstens 100.000.000.000,00", substring = true).assertExists()
        assertEquals(0, repository.tours.size)
    }

    @Test
    fun newTour_startsWithLastUsedCurrency() {
        start(tour(id = 1, destination = "Lofoten").copy(costs = listOf(Money(10_000, Currency.getInstance("NOK")))))
        compose.onNodeWithText("Neue Tour").performClick()

        compose.onNode(hasSetTextAction() and hasText("Kosten (NOK)")).performScrollTo().assertIsDisplayed()
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
        vehicleId = 1,
        startDate = LocalDate.of(2025, 6, 1),
        endDate = LocalDate.of(2025, 6, 3),
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
}
