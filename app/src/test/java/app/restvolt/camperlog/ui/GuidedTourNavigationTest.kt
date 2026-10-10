package app.restvolt.camperlog.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_ID
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_VERSION
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_ID
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_VERSION
import app.restvolt.camperlog.tracking.TrackRecordingSettings
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
        TrackRecordingSettings.resetShared()
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

    /**
     * Das Hauptfenster, nicht ein eventuell gerade offenes zusätzliches (z. B. ein Bottom Sheet):
     * `onRoot()` verlangt genau einen Treffer, kurz nach dem Öffnen oder Schließen eines solchen
     * Fensters existieren aber kurzzeitig mehrere - das mit der größten Fläche ist immer das Hauptfenster.
     */
    private fun mainRoot(): SemanticsNodeInteraction {
        val roots = compose.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val index = nodes.indices.maxBy { nodes[it].boundsInRoot.let { bounds -> bounds.width * bounds.height } }
        return roots[index]
    }

    /** Echter Klick durch ein eventuelles Loch der Barriere, statt der reinen Semantik-Aktion von `performClick()`. */
    private fun clickThrough(matcher: SemanticsMatcher) = mainRoot().performTouchInput { click(centerOf(matcher)) }

    /** Kalenderzelle des Tages; das Datumsfeld im Formular trägt dasselbe Datum. */
    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    /** Eintrag der Stationstyp-Auswahl (eigenes Blatt über der Barriere). */
    private fun typePickerItem(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())

    /** Der echte "Speichern"-Button in der oberen Leiste, nicht der am Formularende oder der Kartentitel. */
    private fun topBarSaveButton() = hasText("Speichern") and hasClickAction() and !hasAnyAncestor(hasScrollAction())

    private fun clickWeiter() {
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
    }

    /**
     * Setzt das Startdatum über den echten Kalender-Dialog, mit einem echten Klick durch das Loch
     * der Barriere (das Datumsfeld liegt innerhalb des "period"-Ankers).
     */
    private fun pickStartDate(day: Int) {
        clickThrough(hasContentDescription("Startdatum wählen"))
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
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
            clickWeiter()
            compose.onNodeWithText("Bordbuch").assertExists()
            clickWeiter()
            compose.onNodeWithText("Fahrzeug").assertExists()
            clickWeiter()
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
            clickThrough(hasText("Neue Tour") and hasClickAction())

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

            clickThrough(hasText("Neue Tour") and hasClickAction())

            compose.onNodeWithText("Weiter").assertIsEnabled()
        } finally {
            clearPreferences()
        }
    }

    /**
     * Vollständiger Durchlauf mit echtem Fahrzeug und beiden "Beim Speichern"-Schaltern aus
     * (Standardeinstellungen): jedes Formularfeld einzeln, Speichern, eine Station anlegen, zurück
     * zum GPS-Track-Abschnitt. Die Fahrzeug-Inline-Anlage und die vorgezogene Berechtigungsabfrage
     * haben eigene, fokussierte Tests.
     */
    @Test
    fun createFirstTourTour_fullWalkthroughCreatesTheTourAndAStationAndEndsOnTheTrackStep() {
        clearPreferences()
        try {
            val tours = start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            // Die Demo-Tour des Tutorials existiert schon, bevor der Nutzer überhaupt etwas tut.
            assertTrue(tours.tours.single().isDemo)

            // Schritt "fab": Anker auf dem echten FAB, ein echter Klick navigiert ins Formular; ein
            // zusätzliches Weiter schaltet danach vom (jetzt freigeschalteten) FAB-Schritt weiter.
            clickThrough(hasText("Neue Tour") and hasClickAction())
            clickWeiter()

            // Schritt "name": echter Text im Namensfeld.
            compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Gardasee-Rundfahrt")
            clickWeiter()

            // Schritt "tourtype": reine Erklärung, keine Pflichtaktion.
            compose.onNodeWithText("Tourart").assertExists()
            clickWeiter()

            // Schritt "vehicle": nur ein echtes Fahrzeug vorhanden, die Auswahl bleibt also
            // unsichtbar, stattdessen zeigt die reine Infozeile das zugeordnete Fahrzeug an.
            compose.onNodeWithText("Mein Wohnmobil").assertExists()
            clickWeiter()

            // Schritt "period": das Startdatum ist unabhängig von der Tour Pflicht, sonst scheitert
            // das echte Speichern.
            pickStartDate(10)
            clickWeiter()

            // Schritt "track-switch": der globale Schalter ist standardmäßig aus, die Checkbox bleibt
            // also unsichtbar; der Schritt erklärt das nur, statt sich zu verstecken.
            clickWeiter()

            // Schritt "home-switch": ohne gesetzten Zuhause-Ort bleibt die Checkbox ebenfalls
            // unsichtbar, der Schritt erklärt stattdessen, wo man ihn einstellt.
            compose.onNodeWithText("Zuhause-Ort").assertExists()
            clickWeiter()

            // Schritt "other-costs" und "advanced": reine Erklärungen der eingeklappten Abschnitte
            // (ihr Kartentitel steht zusätzlich schon im eingeklappten Formularabschnitt selbst).
            clickWeiter()
            clickWeiter()

            // Schritt "save": ein echter Klick auf den Speichern-Button speichert die Tour wirklich
            // und schaltet über onSaved weiter - die Pilot-Tour führt danach direkt zur Tourdetailseite.
            clickThrough(topBarSaveButton())
            compose.waitForIdle()

            assertEquals(2, tours.tours.size)
            val savedTour = tours.tours.single { !it.isDemo }
            assertEquals("Gardasee-Rundfahrt", savedTour.name)

            // Schritt "add-station": Anker auf dem echten FAB der jetzt sichtbaren Tourdetailseite.
            // Ohne Stationen zeigt die Seite zusätzlich einen gleichlautenden Leerzustand-Button in
            // der (scrollbaren) Liste, daher der Ausschluss darauf.
            clickThrough(hasText("Station hinzufügen") and hasClickAction() and !hasAnyAncestor(hasScrollAction()))
            compose.onNode(typePickerItem("Schlafplatz")).performClick()
            compose.waitForIdle()
            // Die Typauswahl schaltet den "add-station"-Schritt nur frei (wie der FAB-Schritt der
            // Tour); ein zusätzliches Weiter schaltet erst zum nächsten Schritt weiter.
            clickWeiter()

            // Schritt "station-fields": reine Erklärung auf dem jetzt echten Stationsformular.
            compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
            clickWeiter()

            // Schritt "station-save": ein echter Klick speichert die Station wirklich.
            clickThrough(topBarSaveButton())
            compose.waitForIdle()

            // Schritt "track": abschließender Hinweis auf den jetzt echten GPS-Track-Abschnitt,
            // ohne weitere echte Aktion.
            compose.onNodeWithText("GPS-Track").assertExists()
            compose.onNodeWithText("Fertig").performClick()
            compose.waitForIdle()

            assertTrue(GuideProgressStore(context).isCompleted(CREATE_FIRST_TOUR_ID, CREATE_FIRST_TOUR_VERSION))
            compose.onNodeWithText("Weiter").assertDoesNotExist()
            compose.onNodeWithText("Fertig").assertDoesNotExist()
            // Die Demo-Tour ist wieder weg, die echte Tour mit ihrer neuen Station bleibt.
            assertEquals(listOf(savedTour), tours.tours)
        } finally {
            clearPreferences()
        }
    }

    /**
     * Besteht noch kein echtes Fahrzeug (nur das Demo-Fahrzeug des Tutorials zählt nicht), bietet der
     * Fahrzeug-Schritt das Anlegen inline an. Das Tourformular pausiert dafür, das angelegte Fahrzeug
     * ist echt (keine Demo-Markierung) und bleibt bestehen; die dauerhaft laufende Vorbelegung des
     * Tourformulars greift es danach automatisch auf.
     */
    @Test
    fun createFirstTourTour_offersInlineVehicleCreation_whenOnlyTheDemoVehicleExists() {
        clearPreferences()
        try {
            // Nur ein als Demo markiertes Fahrzeug vorhanden; demoTourSession.begin() räumt es beim
            // Start auf und legt ein frisches Demo-Fahrzeug an - zu keinem Zeitpunkt existiert ein
            // echtes Fahrzeug.
            val leftoverDemo = defaultVehicle(id = 99, name = "Alter Demo-Wagen").copy(isDemo = true)
            val vehicles = FakeVehicleRepository(listOf(leftoverDemo), currentVehicleId = 99)
            val tours = start(vehicles = vehicles)
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            clickThrough(hasText("Neue Tour") and hasClickAction())
            clickWeiter() // fab
            compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Alpentour")
            clickWeiter() // name
            clickWeiter() // tourtype

            // Schritt "vehicle": kein echtes Fahrzeug vorhanden, der Schritt bietet die Anlage an.
            // "Fahrzeug hinzufügen" steht doppelt (Kartentitel und echter Button), daher der
            // Klickbarkeits-Zusatz zum Unterscheiden.
            val addVehicleButton = hasText("Fahrzeug hinzufügen") and hasClickAction()
            compose.onNode(addVehicleButton).assertExists()
            compose.onNodeWithText("Weiter").assertIsNotEnabled()
            val vehiclesBeforeCreate = vehicles.vehicles.size

            compose.onNode(addVehicleButton).performClick()

            // Das Tourformular ist pausiert (keine Barriere mehr, "Fortsetzen" statt "Weiter"); das
            // jetzt echte, ungeführte Fahrzeugformular ist offen.
            compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Hymer B-Klasse")
            clickThrough(topBarSaveButton())
            compose.waitForIdle()

            // Zurück im Tourformular ist die Pilot-Tour fortgesetzt und freigeschaltet.
            compose.onNodeWithText("Weiter").assertIsEnabled()
            assertEquals(vehiclesBeforeCreate + 1, vehicles.vehicles.size)
            val createdVehicle = vehicles.vehicles.single { !it.isDemo }
            assertEquals("Hymer B-Klasse", createdVehicle.name)
            clickWeiter()

            // Die weiterlaufende Vorbelegung hat das neue Fahrzeug übernommen, sichtbar am Ergebnis
            // nach dem echten Speichern.
            pickStartDate(10)
            clickWeiter() // period
            clickWeiter() // track-switch
            clickWeiter() // home-switch
            clickWeiter() // other-costs
            clickWeiter() // advanced
            clickThrough(topBarSaveButton())
            compose.waitForIdle()

            val savedTour = tours.tours.single { !it.isDemo }
            assertEquals("Alpentour", savedTour.name)
            assertEquals(createdVehicle.id, savedTour.vehicleId)

            // Die Pilot-Tour läuft weiter (jetzt auf der Tourdetailseite); zum Abschluss reicht hier
            // ein vorzeitiges Beenden, der vollständige Rest-Ablauf ist bereits anderswo abgedeckt.
            compose.onNodeWithText("Beenden").performClick()
        } finally {
            clearPreferences()
        }
    }

    /**
     * Innerhalb der Pilot-Tour fragt das Ankreuzen von "GPS-Track aufzeichnen" die Standort-
     * berechtigung direkt an, statt erst beim Speichern - dafür pausiert und setzt sie die Tour
     * automatisch rund um die Abfrage fort. Ohne Berechtigung in diesem Test bleibt die Checkbox
     * zwar unverändert, aber die Tour läuft danach normal weiter.
     */
    @Test
    fun createFirstTourTour_checkingTheTrackSwitchRequestsThePermissionEarlyAndKeepsTheTourUsable() {
        clearPreferences()
        context.getSharedPreferences("track_recording", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        try {
            start()
            openAbout()
            compose.onAllNodesWithText("Starten")[1].performClick()
            compose.waitForIdle()

            clickThrough(hasText("Neue Tour") and hasClickAction())
            clickWeiter() // fab
            compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Nordkap")
            clickWeiter() // name
            clickWeiter() // tourtype
            clickWeiter() // vehicle
            pickStartDate(10)
            clickWeiter() // period

            // Schritt "track-switch": der globale Schalter ist jetzt an, die Checkbox ist also real
            // und über ihren eigenen Anker erreichbar.
            val trackRow = compose.onNode(hasText("GPS-Track aufzeichnen") and hasClickAction())
            trackRow.performClick()
            compose.waitForIdle()
            trackRow.assertIsOn()

            // Die Standortberechtigung wird jetzt angefragt statt erst beim Speichern; dafür pausiert
            // die Tour automatisch ("Fortsetzen" statt "Weiter"). Die Systemabfrage selbst lässt sich
            // in diesem Testaufbau nicht real beantworten (kein echter Berechtigungsdialog unter
            // Robolectric), das Fortsetzen danach aber genauso wie bei jeder anderen manuellen Pause.
            compose.onNodeWithText("Fortsetzen").assertExists()
            compose.onNodeWithText("Fortsetzen").performClick()
            compose.waitForIdle()

            // Die Tour bleibt bedienbar, egal ob die Berechtigung erteilt wurde: Weiter funktioniert
            // unverändert weiter.
            clickWeiter()
            compose.onNodeWithText("Zuhause-Ort").assertExists()

            compose.onNodeWithText("Beenden").performClick()
        } finally {
            clearPreferences()
            context.getSharedPreferences("track_recording", Context.MODE_PRIVATE).edit().clear().commit()
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
            clickThrough(hasText("Neue Tour") and hasClickAction())

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
