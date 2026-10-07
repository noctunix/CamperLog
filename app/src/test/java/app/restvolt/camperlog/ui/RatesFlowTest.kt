package app.restvolt.camperlog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/** Abläufe rund um Wechselkurse: Übersicht, Kursliste, Kursformular und Hauptwährung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class RatesFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val nok = Currency.getInstance("NOK")
    private val dkk = Currency.getInstance("DKK")

    /** Startet auf der Übersicht mit einer Tour über 89,90 € und 500 NOK. */
    private fun startOnOverview(rates: FakeExchangeRateRepository): FakeExchangeRateRepository {
        val tours = FakeTourRepository(listOf(tour(listOf(Money(8_990, EUR), Money(50_000, nok)))))
        compose.setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            CamperLogTheme(darkTheme = mode.isDark(isSystemInDarkTheme())) {
                CamperLogNavHost(tours, FakeVehicleRepository(), FakeLogRepository(), FakeStationRepository(), rates, FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(), mode, canShowStartDialogs = false, countryLookup = FakeCountryLookupRepository()) { mode = it }
            }
        }
        compose.onNodeWithContentDescription("Übersicht").performClick()
        return rates
    }

    private fun startOnRates(rates: FakeExchangeRateRepository = FakeExchangeRateRepository()): FakeExchangeRateRepository {
        startOnOverview(rates)
        compose.onNodeWithText("Wechselkurse").performClick()
        return rates
    }

    private fun rateField() = compose.onNode(hasSetTextAction() and hasText("pro Euro", substring = true))

    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

    private fun rate(currency: Currency, perEuro: String, source: String = "") =
        ExchangeRate(currency, BigDecimal(perEuro), LocalDate.of(2026, 9, 1), source)

    @Test
    fun overview_withoutRate_namesMissingCurrency() {
        startOnOverview(FakeExchangeRateRepository())

        compose.onAllNodesWithText("Kurs fehlt: NOK").onFirst().assertExists()
        compose.onNodeWithText("≈ Summe in EUR").assertDoesNotExist()
    }

    @Test
    fun addMissingRate_overviewShowsConvertedTotal() {
        val rates = startOnRates()

        compose.onNodeWithContentDescription("Kurs für NOK hinzufügen").performClick()
        compose.onNodeWithContentDescription("Währung: NOK · Norwegische Krone").assertExists()
        rateField().performTextInput("10")
        compose.onNode(hasSetTextAction() and hasText("Quelle (optional)")).performTextInput("Bank")
        clickSave()

        compose.onNodeWithText("1 € = 10 NOK").assertExists()
        compose.onNodeWithText("Bank", substring = true).assertExists()
        compose.onNodeWithText("Kurs für NOK hinzufügen", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf(ExchangeRate(nok, BigDecimal("10"), rates.rates.single().date, "Bank")), rates.rates)

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onAllNodesWithText("≈ Summe in EUR").onFirst().assertExists()
        // 89,90 € + 500 NOK / 10 = 139,90 €
        compose.onAllNodesWithText("139,90", substring = true).onFirst().assertExists()
    }

    @Test
    fun newRate_withoutCurrencyAndRate_showsErrorsAndFocusesCurrency() {
        val rates = startOnRates()

        compose.onNodeWithText("Kurs hinzufügen").performClick()
        clickSave()

        compose.onNodeWithText("Bitte eine Währung wählen.").assertExists()
        compose.onNodeWithText("Bitte einen Kurs größer 0 mit höchstens 6 Nachkommastellen eingeben.").assertExists()
        compose.onNodeWithContentDescription("Währung: Währung wählen")
            .assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Bitte eine Währung wählen."))
        assertTrue(rates.rates.isEmpty())
    }

    @Test
    fun newRate_pickerOffersOnlyCurrenciesWithoutRate() {
        startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "11.485"))))

        compose.onNodeWithText("Kurs hinzufügen").performClick()
        compose.onNodeWithContentDescription("Währung: Währung wählen").performClick()

        compose.onNode(hasText("NOK · Norwegische Krone") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNode(hasText("DKK · Dänische Krone") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Währung: DKK · Dänische Krone").assertExists()
    }

    @Test
    fun editRate_prefillsAndKeepsCurrency() {
        val rates = startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "11.4850", "EZB"))))

        compose.onNodeWithText("1 € = 11,4850 NOK").performClick()
        compose.onNodeWithText("Kurs für NOK").assertExists()
        compose.onNodeWithContentDescription("Währung", substring = true).assertDoesNotExist()
        rateField().assert(hasText("11,4850"))
        rateField().performTextReplacement("11.6")
        clickSave()

        compose.onNodeWithText("1 € = 11,6 NOK").assertExists()
        assertEquals(BigDecimal("11.6"), rates.rates.single().perEuro)
        assertEquals("EZB", rates.rates.single().source)
    }

    @Test
    fun invalidRate_isRejected() {
        val rates = startOnRates()

        compose.onNodeWithContentDescription("Kurs für NOK hinzufügen").performClick()
        rateField().performTextInput("0")
        clickSave()

        compose.onNodeWithText("Bitte einen Kurs größer 0 mit höchstens 6 Nachkommastellen eingeben.").assertExists()
        rateField().assertIsFocused()
        assertTrue(rates.rates.isEmpty())
    }

    @Test
    fun leavingDirtyRateForm_asksBeforeDiscarding() {
        val rates = startOnRates()

        compose.onNodeWithContentDescription("Kurs für NOK hinzufügen").performClick()
        rateField().performTextInput("10")
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Änderungen verwerfen?").assertExists()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        rateField().assert(hasText("10"))

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.onNodeWithText("Kurse zum Euro").assertExists()
        assertTrue(rates.rates.isEmpty())
    }

    @Test
    fun deleteRate_canBeUndone() {
        val rates = startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "11.485"))))

        compose.onNodeWithContentDescription("Kurs für NOK löschen").performClick()
        compose.onNodeWithText("Noch keine Kurse erfasst.").assertExists()
        assertTrue(rates.rates.isEmpty())

        compose.onNodeWithText("Rückgängig").performClick()
        compose.onNodeWithText("1 € = 11,485 NOK").assertExists()
        assertEquals(1, rates.rates.size)
    }

    @Test
    fun successiveDeletions_undoRestoresDisplayedRate() {
        val rates = startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "11.485"), rate(dkk, "7.5"))))

        compose.onNodeWithContentDescription("Kurs für NOK löschen").performClick()
        compose.onNodeWithContentDescription("Kurs für DKK löschen").performClick()
        compose.onNodeWithText("Kurs für DKK gelöscht").assertExists()
        compose.onNodeWithText("Rückgängig").performClick()

        assertEquals(listOf(dkk), rates.rates.map(ExchangeRate::currency))
        compose.onNodeWithText("1 € = 7,5 DKK").assertExists()
    }

    @Test
    fun changeMainCurrency_convertsIntoIt() {
        val rates = startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "10"), rate(dkk, "7.5"))))

        compose.onNodeWithContentDescription("Hauptwährung ändern, aktuell Euro").performClick()
        compose.onNode(hasText("NOK · Norwegische Krone") and hasAnyAncestor(isDialog())).performScrollTo().performClick()

        compose.onNodeWithText("NOK · Norwegische Krone").assertExists()
        assertEquals(nok, rates.mainCurrency)

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onAllNodesWithText("≈ Summe in NOK").onFirst().assertExists()
        // 89,90 € * 10 + 500 NOK = 1.399 NOK
        compose.onAllNodesWithText("1.399,00", substring = true).onFirst().assertExists()
    }

    @Test
    fun settings_changeThemeAndMainCurrency_andOpenRates() {
        val rates = startOnOverview(FakeExchangeRateRepository(listOf(rate(nok, "10"), rate(dkk, "7.5"))))
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()

        compose.onNodeWithText("Dunkel").performClick()
        compose.onNodeWithText("Dunkel").assertIsSelected()
        compose.onNodeWithContentDescription("Hauptwährung ändern, aktuell Euro").performClick()
        compose.onNode(hasText("DKK · Dänische Krone") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        assertEquals(dkk, rates.mainCurrency)

        compose.onNodeWithText("Wechselkurse verwalten").performClick()
        compose.onNodeWithText("Kurse zum Euro").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Dunkel").assertIsSelected()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Übersicht").performClick()
        compose.onAllNodesWithText("≈ Summe in DKK").onFirst().assertExists()
    }

    @Test
    fun mainCurrencyWithoutRate_isListedAsMissing() {
        startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "10")), mainCurrency = dkk))

        compose.onNodeWithContentDescription("Kurs für DKK hinzufügen").assertExists()
        compose.onNodeWithContentDescription("Kurs für NOK hinzufügen").assertDoesNotExist()
    }

    @Test
    fun writeFailure_showsMessage() {
        val rates = startOnRates(FakeExchangeRateRepository(listOf(rate(nok, "10"))))
        rates.failWrites = true

        compose.onNodeWithContentDescription("Kurs für NOK löschen").performClick()

        compose.onNodeWithText("Änderung konnte nicht gespeichert werden.").assertExists()
        compose.onNodeWithText("1 € = 10 NOK").assertExists()
    }

    @Test
    fun saveFailure_staysInFormWithMessage() {
        val rates = startOnRates()
        rates.failWrites = true

        compose.onNodeWithContentDescription("Kurs für NOK hinzufügen").performClick()
        rateField().performTextInput("10")
        clickSave()

        compose.onNodeWithText("Kurs konnte nicht gespeichert werden.").assertExists()
        rateField().assertExists()
    }

    private fun tour(costs: List<Money>) = Tour(
        id = 1,
        startDate = LocalDate.of(2025, 6, 1),
        endDate = LocalDate.of(2025, 6, 3),
        destination = "Lofoten",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costs = costs,
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
