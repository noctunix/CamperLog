package app.restvolt.camperlog.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.FakeTileLoader
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.TrackPoint
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

/**
 * Kartenbildschirm: Einstiegspunkt in der Tourdetailseite nur bei eingeschaltetem
 * Schalter und vorhandenen Koordinaten, Marker- und Listensemantik als barrierefreie Alternative.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class MapFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setWeatherEnabled(enabled: Boolean) {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("weather", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).commit()
    }

    private fun lofoten() = Tour(
        id = 1,
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

    private fun station(id: Long, name: String, date: LocalDate, latitude: Double? = null, longitude: Double? = null) = Station(
        id = id,
        vehicleId = 1,
        tourId = 1,
        type = StationType.SIGHT,
        date = date,
        name = name,
        latitude = latitude,
        longitude = longitude,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun start(tours: List<Tour>, stations: List<Station>, track: List<TrackPoint> = emptyList()): FakeTileLoader {
        val tileLoader = FakeTileLoader()
        val tourRepository = FakeTourRepository(tours)
        val stationRepository = FakeStationRepository(stations)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tourRepository,
                    FakeVehicleRepository(),
                    FakeLogRepository(),
                    stationRepository,
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(), FakeDiaryEntryRepository(), FakeChecklistRepository(), FakeChecklistTemplateRepository(), FakeAttachmentRepository(), FakeAttachmentFileStore(), FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    AccentColor.AZURE,
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                    tileLoader = tileLoader,
                    tracks = FakeTrackRepository(track),
                    onAccentColorChange = { },
                    formDraftStore = FakeFormDraftStore(),
                ) { }
            }
        }
        return tileLoader
    }

    private fun trackPoint(second: Long, latitude: Double, longitude: Double) = TrackPoint(
        tourId = 1,
        segment = 1,
        recordedAt = Instant.EPOCH.plusSeconds(second),
        latitude = latitude,
        longitude = longitude,
    )

    @Test
    fun mapButton_isHiddenOnTourDetailWhenTheSwitchIsOff() {
        setWeatherEnabled(false)
        start(listOf(lofoten()), listOf(station(1, "Camping Moskenes", LocalDate.of(2020, 7, 4), 68.0912, 13.1023)))

        compose.onNodeWithText("Lofoten").performClick()

        compose.onNodeWithContentDescription("Karte").assertDoesNotExist()
    }

    @Test
    fun mapButton_isHiddenOnTourDetailWithoutAnyCoordinates() {
        setWeatherEnabled(true)
        start(listOf(lofoten()), listOf(station(1, "Camping Moskenes", LocalDate.of(2020, 7, 4))))

        compose.onNodeWithText("Lofoten").performClick()

        compose.onNodeWithContentDescription("Karte").assertDoesNotExist()
    }

    @Test
    fun mapButton_opensTheMapWithTheAccessibleStopListAsAnAlternative() {
        setWeatherEnabled(true)
        val stations = listOf(
            station(1, "Camping Moskenes", LocalDate.of(2020, 7, 4), 68.0912, 13.1023),
            station(2, "Ohne Koordinaten", LocalDate.of(2020, 7, 5)),
        )
        start(listOf(lofoten()), stations)

        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithContentDescription("Karte").performClick()

        // Kartencanvas als Bild mit Zusammenfassung: "Karte mit 1 von 2 Stationen".
        compose.onNodeWithContentDescription("Karte mit 1 von 2 Stationen").assertExists()

        // Marker tragen eine eigene, vollständige Sprechform.
        compose.onNode(hasContentDescription("Camping Moskenes", substring = true)).assertExists()

        // Die Liste im Bottom Sheet ist die vollständige barrierefreie Alternative: beide Stationen
        // erscheinen, die unverortete mit dem Hinweis "nicht auf der Karte".
        compose.onNodeWithText("Camping Moskenes").assertExists()
        compose.onNodeWithText("Ohne Koordinaten").assertExists()
        compose.onNode(hasText("Nicht auf der Karte", substring = true)).assertExists()

        // Zusammenfassung "1 Station · 1 ohne Koordinaten" aus den Plural-Strings.
        compose.onNodeWithText("1 Station · 1 ohne Koordinaten").assertExists()

        // Attribution ist immer sichtbar und tappbar.
        compose.onNodeWithText("© OpenStreetMap-Mitwirkende").assertExists()
    }

    @Test
    fun mapButton_rendersTheRecordedTrackWithoutCrashing() {
        setWeatherEnabled(true)
        val stations = listOf(station(1, "Camping Moskenes", LocalDate.of(2020, 7, 4), 68.0912, 13.1023))
        val track = listOf(
            trackPoint(0, 68.09, 13.10),
            trackPoint(60, 68.091, 13.101),
            trackPoint(120, 68.092, 13.102),
            trackPoint(180, 68.093, 13.103),
        )
        start(listOf(lofoten()), stations, track)

        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithContentDescription("Karte").performClick()

        // Punkte und Zeit-Labels sind reine Canvas-Zeichnung ohne eigene Semantik; der Smoke-Test
        // prüft nur, dass die Karte mit mehreren Trackpunkten ohne Absturz rendert.
        compose.onNodeWithContentDescription("Karte mit 1 von 1 Station").assertExists()
    }

    @Test
    fun stationsTab_mapIcon_isHiddenWhenNoStationHasCoordinates() {
        setWeatherEnabled(true)
        start(emptyList(), listOf(station(1, "Camping Moskenes", LocalDate.of(2020, 7, 4))))

        compose.onNodeWithText("Stationen").performClick()

        compose.onNodeWithContentDescription("Karte").assertDoesNotExist()
    }
}
