package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_ID
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_VERSION
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_ID
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_VERSION
import app.restvolt.camperlog.ui.guide.GuideProgressStore
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Geführte Touren über den Einstiegspunkt "Über CamperLog": Rundgang und die Pilot-Tour
 * "Erste Tour anlegen". Die Ersteinrichtung selbst (und ihr automatischer Übergang in den
 * Rundgang) testet [FirstRunSetupFlowTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class GuidedTourNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clearPreferences() {
        context.getSharedPreferences("introduction", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("guide_progress", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /**
     * Überspringt die Ersteinrichtung: Hier geht es nur um die geführten Touren selbst.
     * Ohne eigenes `demoTourSession`-Argument baut [CamperLogNavHost] eine Demo-Tour-Sitzung aus
     * genau denselben Fakes, die hier für Touren/Stationen/Fahrzeuge/Tracks übergeben werden.
     */
    private fun start(
        tours: FakeTourRepository = FakeTourRepository(emptyList()),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
    ): FakeTourRepository {
        IntroductionSettings(context).seen = true
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tours,
                    vehicles,
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
                    onThemeModeChange = { },
                )
            }
        }
        return tours
    }

    /**
     * Scrollt die Einstellungen-Liste so lange nach unten, bis [text] komponiert ist: eine
     * `LazyColumn` komponiert nur sichtbare Einträge, daher reicht ein einzelnes `performScrollTo()`
     * nicht, wenn der gesuchte Knoten noch gar nicht existiert.
     */
    private fun scrollUntilVisible(text: String, maxAttempts: Int = 10) {
        repeat(maxAttempts) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun openAbout() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        scrollUntilVisible("Über CamperLog")
        compose.onNodeWithText("Über CamperLog").performClick()
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
    }

    /** Mittelpunkt des Knotens in Fensterkoordinaten, für einen echten `performTouchInput`-Klick. */
    private fun centerOf(matcher: SemanticsMatcher) =
        compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.let { Offset(it.left + it.width / 2, it.top + it.height / 2) }

    /** Kalenderzelle des Tages; das Datumsfeld im Formular trägt dasselbe Datum. */
    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    /**
     * Setzt das Startdatum über den echten Kalender-Dialog. Das Startdatum-Feld liegt außerhalb
     * jedes Ankers der Pilot-Tour, die Barriere blockiert es also; die Tour wird darum kurz
     * pausiert (blendet die Barriere aus) und danach fortgesetzt - genau der dafür vorgesehene Weg.
     */
    private fun pickStartDateWhilePaused(day: Int) {
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithContentDescription("Startdatum wählen").performClick()
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Fortsetzen").performClick()
    }

    @Test
    fun aboutScreen_listsBothToursAsNotYetCompleted() {
        clearPreferences()
        try {
            start()
            openAbout()

            compose.onNodeWithText("Rundgang").assertExists()
            compose.onNodeWithText("Erste Tour anlegen").assertExists()
            compose.onAllNodesWithText("Starten").assertCountEquals(2)
            compose.onNodeWithText("Abgeschlossen").assertDoesNotExist()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun introductionTour_walkthroughEndsAndMarksProgressComplete() {
        clearPreferences()
        try {
            start()
            openAbout()
            compose.onAllNodesWithText("Starten")[0].performClick()

            compose.onNodeWithText("Willkommen bei CamperLog").assertExists()
            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Bordbuch").assertExists()
            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Fahrzeug").assertExists()
            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Deine Daten").assertExists()
            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Deine Daten").assertDoesNotExist()
            assertTrue(GuideProgressStore(context).isCompleted(INTRODUCTION_TOUR_ID, INTRODUCTION_TOUR_VERSION))

            // Erneut geöffnet (von der Einstellungsseite aus, auf der "Über CamperLog" steht), zeigt
            // "Über CamperLog" jetzt das Häkchen für den Rundgang.
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Über CamperLog").performClick()
            compose.onNodeWithText("Abgeschlossen").assertExists()
            compose.onAllNodesWithText("Wiederholen").assertCountEquals(1)
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun createFirstTourTour_fabAnchorIsReachableThroughTheHole() {
        clearPreferences()
        try {
            start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Zurück auf dem Touren-Reiter zeigt die Karte auf den echten FAB; ein Klick muss
            // durch das Loch der Barriere zum echten Button durchkommen und ins Formular navigieren.
            val fabCenter = centerOf(hasText("Neue Tour") and hasClickAction())
            compose.onRoot().performTouchInput { click(fabCenter) }

            compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun createFirstTourTour_endingEarlyRemovesTheDemoTourAndItsVehicleToo() {
        clearPreferences()
        try {
            val vehicles = FakeVehicleRepository()
            val tours = start(vehicles = vehicles)
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            assertEquals(1, tours.tours.size)
            assertEquals(2, vehicles.vehicles.size)

            // "Beenden" bricht die Pilot-Tour vorzeitig ab; das muss die Demo-Tour genauso aufräumen
            // wie ein regulärer Abschluss.
            compose.onNodeWithText("Beenden").performClick()
            compose.waitForIdle()

            assertEquals(emptyList<Any>(), tours.tours)
            assertEquals(1L, vehicles.vehicles.single().id)
            assertTrue(vehicles.vehicles.none { it.isDemo })
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun createFirstTourTour_weiterStaysDisabledUntilTheRealFabClick() {
        clearPreferences()
        try {
            start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Schritt 1 wartet auf den echten FAB-Klick: Weiter allein darf nicht weiterschalten,
            // sonst denkt die Tour, sie sei im Formular, ohne dass navigiert wurde.
            compose.onNodeWithText("Weiter").assertIsNotEnabled()

            val fabCenter = centerOf(hasText("Neue Tour") and hasClickAction())
            compose.onRoot().performTouchInput { click(fabCenter) }

            compose.onNodeWithText("Weiter").assertIsEnabled()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun createFirstTourTour_realSaveCompletesTheActionStepAndEndsTheTourAfterTheTrackStep() {
        clearPreferences()
        try {
            val tours = start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Die Demo-Tour des Tutorials existiert schon, bevor der Nutzer überhaupt etwas tut.
            assertTrue(tours.tours.single().isDemo)

            // Schritt 1: Anker auf dem echten FAB, ein echter Klick navigiert ins Formular.
            val fabCenter = centerOf(hasText("Neue Tour") and hasClickAction())
            compose.onRoot().performTouchInput { click(fabCenter) }

            // Das Startdatum ist unabhängig von der Tour Pflicht, sonst scheitert das echte Speichern.
            pickStartDateWhilePaused(10)

            // Schritt 2: Weiter auf der Karte wechselt zum Namensfeld, dort wird echter Text eingegeben.
            compose.onNodeWithText("Weiter").performClick()
            compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Gardasee-Rundfahrt")

            // Schritt 3: Weiter zeigt auf den Speichern-Button in der oberen Leiste; ein echter Klick
            // dort speichert die Tour wirklich und löst completeAction über onSaved aus.
            compose.onNodeWithText("Weiter").performClick()
            val saveCenter = centerOf(hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction()))
            compose.onRoot().performTouchInput { click(saveCenter) }
            compose.waitForIdle()

            assertEquals(2, tours.tours.size)
            val savedTour = tours.tours.single { !it.isDemo }
            assertEquals("Gardasee-Rundfahrt", savedTour.name)
            // Schritt 4: der abschließende Hinweis zum GPS-Track, ohne weitere echte Aktion.
            compose.onNodeWithText("GPS-Track").assertExists()
            compose.onNodeWithText("Fertig").performClick()
            compose.waitForIdle()

            assertTrue(GuideProgressStore(context).isCompleted(CREATE_FIRST_TOUR_ID, CREATE_FIRST_TOUR_VERSION))
            // Die Tour ist zu Ende: keine Karte mehr sichtbar, und die Demo-Tour ist wieder weg.
            compose.onNodeWithText("Weiter").assertDoesNotExist()
            compose.onNodeWithText("Fertig").assertDoesNotExist()
            assertEquals(listOf(savedTour), tours.tours)

            openAbout()
            compose.onAllNodesWithText("Abgeschlossen").assertCountEquals(1)
        } finally {
            clearPreferences()
        }
    }

    /** Der primäre Einstiegspunkt liegt jetzt direkt in den Einstellungen, nicht erst eine Ebene tiefer in "Über CamperLog". */
    @Test
    fun createFirstTourTour_reachableDirectlyFromSettingsAndTheFabAnchorIsReal() {
        clearPreferences()
        try {
            start()
            openSettings()
            scrollUntilVisible("Erste Tour anlegen")
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Die Navigation zur Tourenliste muss abgeschlossen sein, bevor die Tour startet: Ein
            // echter Klick muss durch das Loch der Barriere zum echten FAB durchkommen.
            val fabCenter = centerOf(hasText("Neue Tour") and hasClickAction())
            compose.onRoot().performTouchInput { click(fabCenter) }

            compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun createFirstTourTour_demoTourShowsItsMapButtonEvenWithWeatherAndMapSwitchedOff() {
        clearPreferences()
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        try {
            start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Pausiert blendet die Barriere aus (wie beim Startdatum), damit die Demo-Tour abseits
            // des FAB-Ankers geöffnet werden kann, ohne die Tour schon zu beenden. Die Kartenzeile
            // ist eine zusammengeführte Sprechform (Titel, "Beispiel"-Kennzeichnung, Zeitraum, …),
            // daher reicht hier ein Teilstring.
            compose.onNodeWithText("Pause").performClick()
            compose.onNode(hasText("Alpine loop", substring = true)).performClick()

            // "Wetter & Karte" ist standardmäßig aus; die Demo-Tour zeigt den "Karte"-Button trotzdem.
            compose.onNodeWithText("Karte").assertExists()
        } finally {
            clearPreferences()
        }
    }

    @Test
    fun aboutScreen_startingTheCreateFirstTourTourNavigatesToTheToursTab() {
        clearPreferences()
        try {
            start()
            // Von irgendeinem anderen Reiter aus gestartet, damit die Navigation wirklich zählt.
            compose.onNodeWithText("Bordbuch").performClick()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Auf dem Touren-Reiter steht jetzt der echte FAB (zusammen mit der Karte des aktuellen
            // Tutorialschritts gibt es "Neue Tour" zweimal, siehe die anderen Tests dieser Datei),
            // dazu schon die mit "Beispiel" gekennzeichnete Demo-Tour (vor dieser Navigation von
            // DemoTourSession.begin angelegt).
            compose.onAllNodesWithText("Neue Tour").assertCountEquals(2)
            compose.onNode(hasText("Beispiel", substring = true)).assertExists()
        } finally {
            clearPreferences()
        }
    }
}
