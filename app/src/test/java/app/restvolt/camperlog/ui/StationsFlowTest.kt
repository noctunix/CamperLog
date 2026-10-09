package app.restvolt.camperlog.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.AccentColor
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

/** End-to-End-Abläufe auf dem Stationen-Reiter: Liste, Suche, Filter, Fahrzeugumfang, Anlegen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class StationsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
        tours: List<Tour> = emptyList(),
        stations: List<Station> = emptyList(),
    ): Pair<FakeTourRepository, FakeStationRepository> {
        val tourRepository = FakeTourRepository(tours) { vehicles.currentVehicleId }
        val stationRepository = FakeStationRepository(stations)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tourRepository,
                    vehicles,
                    FakeLogRepository(),
                    stationRepository,
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
        return tourRepository to stationRepository
    }

    private fun openStationsTab() {
        compose.onNodeWithText("Stationen").performClick()
    }

    private fun station(
        id: Long,
        type: StationType = StationType.SIGHT,
        date: LocalDate = LocalDate.of(2026, 7, 4),
        vehicleId: Long = 1,
        tourId: Long? = null,
        name: String = "",
        place: String = "",
        notes: String = "",
        favorite: Boolean = false,
    ) = Station(
        id = id,
        vehicleId = vehicleId,
        tourId = tourId,
        type = type,
        date = date,
        name = name,
        place = place,
        notes = notes,
        favorite = favorite,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun emptyList_showsEmptyHint() {
        start()
        openStationsTab()

        compose.onNodeWithText("Noch keine Stationen. Halte fest, wo du übernachtet, entsorgt oder getankt hast – mit „Neue Station“.")
            .assertExists()
    }

    @Test
    fun list_showsStationsGroupedByMonthNewestFirst() {
        val july = station(id = 1, name = "Camping Moskenes", date = LocalDate.of(2026, 7, 4))
        val june = station(id = 2, name = "Tankstelle Flensburg", type = StationType.FUEL, date = LocalDate.of(2026, 6, 26))
        start(stations = listOf(july, june))
        openStationsTab()

        compose.onNodeWithText("Camping Moskenes").assertExists()
        compose.onNodeWithText("Tankstelle Flensburg").assertExists()
        val julyHeaderTop = compose.onNodeWithText("JULI 2026").getUnclippedBoundsInRoot().top
        val juneHeaderTop = compose.onNodeWithText("JUNI 2026").getUnclippedBoundsInRoot().top
        assertTrue("Juli (neuer) soll über Juni stehen", julyHeaderTop < juneHeaderTop)
    }

    @Test
    fun search_filtersByNamePlaceAndNotes() {
        val moskenes = station(id = 1, name = "Camping Moskenes")
        val flensburg = station(id = 2, name = "Tankstelle Flensburg", type = StationType.FUEL)
        start(stations = listOf(moskenes, flensburg))
        openStationsTab()

        compose.onNodeWithText("Station suchen").performTextInput("moske")

        compose.onNodeWithText("Camping Moskenes").assertExists()
        compose.onNodeWithText("Tankstelle Flensburg").assertDoesNotExist()
    }

    @Test
    fun typeFilter_showsOnlyMatchingType() {
        val overnight = station(id = 1, name = "Camping Moskenes", type = StationType.OVERNIGHT)
        val fuel = station(id = 2, name = "Tankstelle Flensburg", type = StationType.FUEL)
        start(stations = listOf(overnight, fuel))
        openStationsTab()

        compose.onNodeWithText("Schlafplatz").performClick()

        compose.onNodeWithText("Camping Moskenes").assertExists()
        compose.onNodeWithText("Tankstelle Flensburg").assertDoesNotExist()
    }

    @Test
    fun favoriteFilter_showsOnlyWouldReturnStations() {
        val liked = station(id = 1, name = "Camping Moskenes", type = StationType.OVERNIGHT, favorite = true)
        val other = station(id = 2, name = "Camping Reine", type = StationType.OVERNIGHT, favorite = false)
        start(stations = listOf(liked, other))
        openStationsTab()

        compose.onNodeWithText("Gerne wieder").performClick()

        compose.onNodeWithText("Camping Moskenes").assertExists()
        compose.onNodeWithText("Camping Reine").assertDoesNotExist()
    }

    @Test
    fun allVehiclesScope_isSharedWithToursTab() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        val stationA = station(id = 1, name = "Platz A", vehicleId = 1)
        val stationB = station(id = 2, name = "Platz B", vehicleId = 2)
        start(vehicles, stations = listOf(stationA, stationB))

        // Auf Touren "Alle Fahrzeuge" wählen gilt dann auch für Stationen.
        compose.onNodeWithContentDescription("Wohnmobil A, Fahrzeug wechseln").performClick()
        compose.onNodeWithText("Alle Fahrzeuge").performClick()
        openStationsTab()

        compose.onNodeWithText("Platz A").assertExists()
        compose.onNodeWithText("Platz B").assertExists()
    }

    @Test
    fun addStop_fromTab_createsStandaloneStation() {
        val (_, stationRepository) = start()
        openStationsTab()

        compose.onNodeWithText("Station hinzufügen").performClick()
        compose.onNode(hasText("Sehenswertes") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()

        val saved = stationRepository.stations.single()
        assertEquals(StationType.SIGHT, saved.type)
        assertEquals(null, saved.tourId)
    }

    @Test
    fun tapStation_opensDetail() {
        val station = station(id = 1, name = "Camping Moskenes", type = StationType.OVERNIGHT)
        start(stations = listOf(station))
        openStationsTab()

        compose.onNodeWithText("Camping Moskenes").performClick()

        compose.onNodeWithText("Schlafplatz").assertExists()
    }

    /** Der Einstellungen-Verweis war aus dem Hinweistext verschwunden und ist zurück. */
    @Test
    fun whatsNewCard_showsTheFullTextAndOpensSettings() {
        val preferences = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("stations_whats_new", android.content.Context.MODE_PRIVATE)
        preferences.edit().putBoolean("pending", true).commit()
        try {
            start()
            openStationsTab()

            compose.onNodeWithText(
                "Neu: Stationen. Deine bisherigen Stellplatz-Angaben stehen jetzt als Schlafplätze in deinen Touren. " +
                    "Standort, Wetter und Karte kannst du in den Einstellungen einschalten.",
            ).assertExists()

            compose.onNodeWithText("Einstellungen").performClick()

            compose.onNodeWithText("Standort & Internet").assertExists()
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
