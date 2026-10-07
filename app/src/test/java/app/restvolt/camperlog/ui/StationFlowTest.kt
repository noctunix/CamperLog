package app.restvolt.camperlog.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.electricityCost
import app.restvolt.camperlog.domain.electricityKwh
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatKwh
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.FakeWeatherProvider
import app.restvolt.camperlog.domain.GeoIntentLocation
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.WeatherProvider
import app.restvolt.camperlog.domain.WeatherResult
import app.restvolt.camperlog.domain.WeatherSnapshot
import java.math.BigDecimal
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.time.LocalDate

/** End-to-End-Abläufe rund um Stationen: Zeitleiste, Formular, Detail und `geo:`-Link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class StationFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(
        tours: List<Tour> = emptyList(),
        stations: List<Station> = emptyList(),
        pendingGeoIntent: GeoIntentLocation? = null,
        logs: FakeLogRepository = FakeLogRepository(),
        locationProvider: LocationProvider = FakeLocationProvider(),
        weatherProvider: WeatherProvider = FakeWeatherProvider(WeatherResult.Error),
    ): Triple<FakeTourRepository, FakeStationRepository, FakeLogRepository> {
        val stationRepository = FakeStationRepository(stations, logs)
        val tourRepository = FakeTourRepository(tours, stations = stationRepository)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tourRepository,
                    FakeVehicleRepository(),
                    logs,
                    stationRepository,
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    pendingGeoIntent = pendingGeoIntent,
                    locationProvider = locationProvider,
                    weatherProvider = weatherProvider,
                ) { }
            }
        }
        return Triple(tourRepository, stationRepository, logs)
    }

    /** Schreibt den Standort-Schalter direkt in die Geräteeinstellungen, wie `ReminderSettingsTest`. */
    private fun setLocationEnabled(enabled: Boolean) {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("location", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).commit()
    }

    /** Schreibt den Wetter-Schalter direkt in die Geräteeinstellungen, wie [setLocationEnabled]. */
    private fun setWeatherEnabled(enabled: Boolean) {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("weather", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).commit()
    }

    private fun lofoten(id: Long = 1) = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(2020, 7, 4),
        endDate = LocalDate.of(2020, 7, 17),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 14,
        overnightStays = 13,
        distanceKm = 3420,
        costs = listOf(Money(48_650, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(
        id: Long = 0,
        tourId: Long? = 1,
        type: StationType = StationType.OVERNIGHT,
        date: LocalDate = LocalDate.of(2020, 7, 4),
        name: String = "",
        place: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        mapLink: String? = null,
        nights: Int? = 2,
        favorite: Boolean = false,
        services: Set<app.restvolt.camperlog.domain.StationService> = emptySet(),
    ) = Station(
        id = id,
        vehicleId = 1,
        tourId = tourId,
        type = type,
        date = date,
        name = name,
        place = place,
        latitude = latitude,
        longitude = longitude,
        mapLink = mapLink,
        nights = nights.takeIf { type == StationType.OVERNIGHT },
        favorite = favorite,
        services = services,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    /** FAB und Leerstands-Textbutton tragen denselben Text; welcher von beiden reicht, beide öffnen die Typauswahl. */
    private fun openTypePicker() {
        compose.onAllNodesWithText("Station hinzufügen").onFirst().performClick()
    }

    /** Eintrag der Typauswahl-Sheet; grenzt gegen eine gleichnamige Zeitleisten-Zeile darunter ab. */
    private fun typePickerItem(label: String) = compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    /** Speichert über den Button am Formularende, nicht über die Aktion in der App-Leiste (siehe TourFlowTest). */
    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

    /** Kalenderzelle des Tages; das Datumsfeld im Formular trägt dasselbe Datum (siehe TourFlowTest). */
    private fun dayCell(day: Int) = hasText(", $day. ", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun pickDay(fieldLabel: String, day: Int) {
        compose.onNodeWithContentDescription("$fieldLabel wählen").performScrollTo().performClick()
        compose.onNode(dayCell(day)).performClick()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun addOvernightStop_withoutExistingStations_defaultsDateToTourStart() {
        val (_, stationRepository) = start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(StationType.OVERNIGHT, saved.type)
        assertEquals(LocalDate.of(2020, 7, 4), saved.date)
        assertEquals(1L, saved.tourId)
        assertEquals("1", saved.nights?.toString())
    }

    @Test
    fun addOvernightStop_afterExistingOvernightStation_defaultsToDayAfterItsNights() {
        val existing = station(id = 1, date = LocalDate.of(2020, 7, 4), nights = 2)
        val (_, stationRepository) = start(listOf(lofoten()), listOf(existing))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        clickSave()

        val saved = stationRepository.stations.first { it.id != 1L }
        assertEquals(LocalDate.of(2020, 7, 6), saved.date)
    }

    @Test
    fun typeSpecificFields_changeWithSelectedType() {
        start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()

        compose.onNodeWithText("Nächte").assertExists()
        compose.onNodeWithText("Getankt").assertDoesNotExist()

        compose.onNode(hasText("Art") and hasClickAction()).performClick()
        compose.onNodeWithText("Tanken & Laden").performClick()

        compose.onNodeWithText("Getankt").assertExists()
        compose.onNodeWithText("Nächte").assertDoesNotExist()
    }

    @Test
    fun pastedCoordinates_areParsedAndShown() {
        start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        compose.onNodeWithText("Koordinaten eingeben").performClick()
        compose.onNode(hasSetTextAction() and hasText("Koordinaten oder Kartenlink")).performTextInput("68.0912, 13.1023")

        compose.onNodeWithText("Erkannt: 68,0912° N · 13,1023° E").assertExists()
    }

    @Test
    fun deleteStop_fromDetail_removesItAndOffersUndo() {
        val existing = station(id = 1, name = "Camping Moskenes")
        val (_, stationRepository, _) = start(listOf(lofoten()), listOf(existing))

        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Camping Moskenes").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Station löschen").performClick()

        compose.onNodeWithText("Noch keine Stationen.").assertExists()
        compose.onNodeWithText("„Camping Moskenes“ gelöscht").assertExists()
        assertEquals(0, stationRepository.stations.size)

        compose.onNodeWithText("Rückgängig").performClick()
        assertEquals(listOf(existing), stationRepository.stations)
    }

    @Test
    fun deleteStop_withLinkedLogEntry_undoRelinksIt() {
        val existing = station(id = 1, name = "Camping Moskenes", services = setOf(StationService.CASSETTE))
        val linked = LogEntry(
            id = 1,
            uuid = "log-1",
            vehicleId = 1,
            type = LogType.CASSETTE_EMPTIED,
            date = existing.date,
            createdAt = Instant.EPOCH,
            stationId = existing.id,
        )
        val logs = FakeLogRepository(listOf(linked))
        val (_, stationRepository, _) = start(listOf(lofoten()), listOf(existing), logs = logs)

        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Camping Moskenes").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Station löschen").performClick()

        assertEquals(null, logs.entries.single { it.id == linked.id }.stationId)

        compose.onNodeWithText("Rückgängig").performClick()

        assertEquals(existing.id, logs.entries.single { it.id == linked.id }.stationId)
    }

    @Test
    fun savingSupplyStopWithCassette_showsSnackbar_andLogbookTileUpdates() {
        val (_, _, logs) = start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Ver-/Entsorgung").performClick()
        compose.onNodeWithText("Kassette").performClick()
        clickSave()

        compose.onNodeWithText("Station gespeichert · Kassette ins Bordbuch eingetragen").assertExists()
        assertEquals(LogType.CASSETTE_EMPTIED, logs.entries.single().type)

        // Die neue Station landet erst auf ihrer Detailseite, dann die Tourdetailseite, die keine
        // untere Navigation hat; erst zurück zu den Touren, dann ins Bordbuch.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Bordbuch").performClick()
        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(4)
    }

    @Test
    fun savingNewStop_landsOnItsDetailWithPhotosEnabled() {
        val (_, stationRepository) = start(listOf(lofoten()))
        val locale = Locale.GERMANY

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        clickSave()

        assertEquals(1, stationRepository.stations.size)
        compose.onNodeWithContentDescription("Foto hinzufügen").assertExists()
        compose.onNodeWithText("Speichern schaltet Fotos frei.").assertDoesNotExist()

        // Zurück führt zur Herkunft des Formulars, hier die Tourdetailseite.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onAllNodesWithText(formatAmount(48_650, EUR, locale)).onFirst().assertExists()
    }

    @Test
    fun openInMaps_withCoordinates_firesGeoIntent() {
        val existing = station(id = 1, name = "Camping Moskenes", latitude = 68.0912, longitude = 13.1023)
        start(listOf(lofoten()), listOf(existing))

        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText("Camping Moskenes").performClick()
        compose.onNodeWithText("In Karten-App öffnen").performScrollTo().performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo", started.data?.scheme)
        assertEquals(true, started.dataString?.contains("68.0912,13.1023"))
    }

    @Test
    fun geoIntent_prefillsNewStopWithParsedLocation() {
        start(pendingGeoIntent = GeoIntentLocation(68.0912, 13.1023, "Camping Moskenes"))

        compose.onNodeWithText("Was für eine Station?").assertExists()
        typePickerItem("Schlafplatz").performClick()

        compose.onNode(hasSetTextAction() and hasText("Ort oder Adresse")).assert(hasText("Camping Moskenes"))
        compose.onNodeWithText("Erkannt: 68,0912° N · 13,1023° E").assertExists()
    }

    @Test
    fun geoIntent_withoutUsableData_showsNoPicker() {
        start(pendingGeoIntent = null)

        compose.onNodeWithText("Was für eine Station?").assertDoesNotExist()
    }

    @Test
    fun locationOff_showsNothingLocationRelatedInThePlaceSection() {
        setLocationEnabled(false)
        start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()

        compose.onNodeWithText("Aktuellen Standort verwenden").assertDoesNotExist()
        compose.onNodeWithText("Koordinaten eingeben").assertExists()
    }

    @Test
    fun locationOn_approximateFix_showsTheOneTimePrecisionHintOnlyOnce() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("location", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        try {
            setLocationEnabled(true)
            val approximateFix = LocationFix(68.0912, 13.1023, accuracyM = 3_000)
            start(listOf(lofoten()), locationProvider = FakeLocationProvider(freshFix = approximateFix))
            shadowOf(compose.activity.application).grantPermissions(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
            )

            compose.onNodeWithText("Lofoten").performClick()
            openTypePicker()
            typePickerItem("Schlafplatz").performClick()
            compose.onNodeWithText("Aktuellen Standort verwenden").performScrollTo().performClick()

            compose.onNodeWithText("68,0912° N · 13,1023° E · GPS ±3 km (ungefähr)").assertExists()
            compose.onNodeWithText("Für genaue Positionen erlaube in den App-Einstellungen „Genauer Standort“.").assertExists()

            assertTrue(LocationSettings(ApplicationProvider.getApplicationContext()).approximateHintShown)
        } finally {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSharedPreferences("location", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun locationOn_useCurrentLocation_fillsCoordinatesWithGpsSource() {
        setLocationEnabled(true)
        val fix = LocationFix(68.0912, 13.1023, accuracyM = 8)
        val (_, stationRepository) = start(listOf(lofoten()), locationProvider = FakeLocationProvider(freshFix = fix))
        shadowOf(compose.activity.application).grantPermissions(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        compose.onNodeWithText("Aktuellen Standort verwenden").performScrollTo().performClick()
        compose.onNodeWithText("68,0912° N · 13,1023° E · GPS ±8 m").assertExists()
        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(68.0912, saved.latitude)
        assertEquals(13.1023, saved.longitude)
        assertEquals(CoordinateSource.GPS, saved.coordinateSource)
        assertEquals(8, saved.accuracyM)
    }

    @Test
    fun weatherOff_showsNothingWeatherRelatedInTheStopForm() {
        setWeatherEnabled(false)
        start()

        compose.onNodeWithText("Stationen").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        compose.onNodeWithText("Koordinaten eingeben").performClick()
        compose.onNode(hasSetTextAction() and hasText("Koordinaten oder Kartenlink")).performTextInput("68.0912, 13.1023")

        compose.onNodeWithText("Wetter").assertDoesNotExist()
        compose.onNodeWithText("Wetter abrufen").assertDoesNotExist()
    }

    @Test
    fun weatherOn_withoutCoordinates_showsTheHintInsteadOfTheCard() {
        setWeatherEnabled(true)
        start()

        compose.onNodeWithText("Stationen").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()

        compose.onNodeWithText("Mit Koordinaten kannst du auch das Wetter abrufen.").assertExists()
        compose.onNodeWithText("Wetter abrufen").assertDoesNotExist()
    }

    @Test
    fun weatherOn_fetchingWeather_attachesSnapshotToTheSavedStop() {
        setWeatherEnabled(true)
        val snapshot = WeatherSnapshot(
            temperatureDeciC = 143,
            weatherCode = 1,
            windKmh = 18,
            gustKmh = 35,
            windDirectionDeg = 270,
            observedAt = Instant.EPOCH,
        )
        val (_, stationRepository) = start(weatherProvider = FakeWeatherProvider(WeatherResult.Success(snapshot)))

        compose.onNodeWithText("Stationen").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()
        compose.onNodeWithText("Koordinaten eingeben").performClick()
        compose.onNode(hasSetTextAction() and hasText("Koordinaten oder Kartenlink")).performTextInput("68.0912, 13.1023")
        compose.onNodeWithText("Wetter abrufen").performScrollTo().performClick()
        compose.onNodeWithText("14 °C · Leicht bewölkt").assertExists()

        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(snapshot, saved.weather)
    }

    @Test
    fun overnightStop_withMeteredElectricity_showsLiveResultAndSavedCost() {
        val (_, stationRepository) = start(listOf(lofoten()))
        val locale = Locale.GERMANY
        val expectedResult = "≈ ${formatAmount(1_000, EUR, locale)} · ${formatKwh(BigDecimal("20"), locale)}"

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Schlafplatz").performClick()

        compose.onNodeWithText("Strom").performScrollTo().performClick()
        compose.onNodeWithText("nach Verbrauch").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Preis je kWh (€)")).performScrollTo().performTextInput("0,50")
        compose.onNode(hasSetTextAction() and hasText("Zählerstand Anfang (kWh)")).performScrollTo().performTextInput("100")
        compose.onNode(hasSetTextAction() and hasText("Zählerstand Ende (kWh)")).performScrollTo().performTextInput("120")

        compose.onNodeWithText(expectedResult).assertExists()

        // Solange die Stromabrechnung schon einen Betrag ergibt, fehlt "Strom" in der Kategorieliste
        // der manuellen Kosten: Das Öffnen der Liste fügt keinen weiteren Knoten mit diesem Text hinzu
        // (er kommt schon von der Abschnittsüberschrift und der Beschriftung über den Abrechnungs-Chips).
        compose.onNodeWithText("Kosten hinzufügen").performScrollTo().performClick()
        val stromNodesBeforeMenu = compose.onAllNodesWithText("Strom").fetchSemanticsNodes().size
        compose.onNode(hasText("Kategorie") and hasClickAction()).performScrollTo().performClick()
        compose.onAllNodesWithText("Strom").assertCountEquals(stromNodesBeforeMenu)
        compose.onAllNodesWithText("Stellplatz").onFirst().performClick()

        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(Money(1_000, EUR), electricityCost(saved))
        assertEquals(BigDecimal("20"), electricityKwh(saved))

        // Die neue Station landet direkt auf ihrer Detailseite.
        compose.onNodeWithText("Strom: nach Verbrauch").assertExists()
        compose.onNodeWithText(expectedResult).assertExists()

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText(formatAmount(1_000, EUR, locale)).assertExists()
        compose.onNodeWithText(formatAmount(49_650, EUR, locale)).assertExists()
    }

    /**
     * Die Länderauswahl selbst (Dialog mit Suche über alle ISO-Codes) wird gesondert in
     * [CountryPickerTest] abgedeckt; hier reicht die Vignetten-Art mit Gültigkeitsdaten und
     * Zahlweise, da das Öffnen des Länder-Dialogs in diesem vollen Navigationsbaum unter Robolectric
     * nicht stabil zur Ruhe kommt.
     */
    @Test
    fun tollVignetteStop_withDatesAndPaymentMethod_isSavedAndShownInDetail() {
        val (_, stationRepository) = start(listOf(lofoten()))

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Maut").performClick()

        compose.onNodeWithText("Vignette").performClick()
        pickDay("Gültig ab", 1)
        pickDay("Gültig bis", 15)
        compose.onNode(hasSetTextAction() and hasText("Zahlungsart")).performScrollTo().performTextInput("App")

        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(TollKind.VIGNETTE, saved.tollKind)
        assertEquals(1, saved.tollValidFrom?.dayOfMonth)
        assertEquals(15, saved.tollValidUntil?.dayOfMonth)
        assertEquals("App", saved.tollPaymentMethod)

        // Die neue Station landet direkt auf ihrer Detailseite.
        compose.onNodeWithText("Zahlungsart: App").assertExists()
    }

    @Test
    fun manualCostLine_isSavedAndCountedInTourTotals() {
        val (_, stationRepository) = start(listOf(lofoten()))
        val locale = Locale.GERMANY

        compose.onNodeWithText("Lofoten").performClick()
        openTypePicker()
        typePickerItem("Essen").performClick()

        compose.onNodeWithText("Kosten hinzufügen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Essen-Kosten (€)").assertExists()

        // Ohne Stromabrechnung steht "Strom" in der Kategorieliste zur Wahl.
        val categoryField = compose.onNode(hasText("Kategorie") and hasClickAction())
        categoryField.performScrollTo().performClick()
        compose.onNodeWithText("Strom").assertExists()
        categoryField.performClick()

        compose.onNode(hasSetTextAction() and hasText("Kosten (€)")).performScrollTo().performTextInput("12,50")

        clickSave()

        val saved = stationRepository.stations.single()
        assertEquals(CostCategory.FOOD, saved.costs.single().category)
        assertEquals(Money(1_250, EUR), saved.costs.single().amount)

        // Die neue Station landet direkt auf ihrer Detailseite.
        compose.onNodeWithText("Essen: ${formatAmount(1_250, EUR, locale)}").assertExists()

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText(formatAmount(1_250, EUR, locale)).assertExists()
        compose.onNodeWithText(formatAmount(49_900, EUR, locale)).assertExists()

        // Die Übersicht rechnet Stationskosten ebenfalls ein und schlüsselt sie nach Kategorie auf.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Übersicht").performClick()
        // Mit nur einer Tour zeigen "Gesamt" und das Jahr 2026 denselben Betrag doppelt an.
        compose.onAllNodesWithText(formatAmount(49_900, EUR, locale)).onFirst().assertExists()
        compose.onAllNodesWithText("Nach Kategorie").onFirst().performScrollTo().performClick()
        compose.onNodeWithText(formatAmount(1_250, EUR, locale)).assertExists()
    }
}
