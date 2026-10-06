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
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.GeoIntentLocation
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
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
    ): Triple<FakeTourRepository, FakeStationRepository, FakeLogRepository> {
        val tourRepository = FakeTourRepository(tours)
        val stationRepository = FakeStationRepository(stations, logs)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tourRepository,
                    FakeVehicleRepository(),
                    logs,
                    stationRepository,
                    FakeExchangeRateRepository(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    pendingGeoIntent = pendingGeoIntent,
                    locationProvider = locationProvider,
                ) { }
            }
        }
        return Triple(tourRepository, stationRepository, logs)
    }

    /** Schreibt den Standort-Schalter direkt in die Geräteeinstellungen (6.11), wie `ReminderSettingsTest`. */
    private fun setLocationEnabled(enabled: Boolean) {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("location", android.content.Context.MODE_PRIVATE)
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

    /** FAB und Leerstands-Textbutton tragen denselben Text (8.1); welcher von beiden reicht, beide öffnen die Typauswahl. */
    private fun openTypePicker() {
        compose.onAllNodesWithText("Station hinzufügen").onFirst().performClick()
    }

    /** Eintrag der Typauswahl-Sheet; grenzt gegen eine gleichnamige Zeitleisten-Zeile darunter ab. */
    private fun typePickerItem(label: String) = compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    /** Speichert über den Button am Formularende, nicht über die Aktion in der App-Leiste (siehe TourFlowTest). */
    private fun clickSave() =
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

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

        // Die Tourdetailseite hat keine untere Navigation; erst zurück zu den Touren, dann ins Bordbuch.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Bordbuch").performClick()
        compose.onAllNodesWithText("Noch nicht erfasst").assertCountEquals(4)
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
}
