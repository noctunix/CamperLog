package app.restvolt.camperlog.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.Station
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
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Abläufe rund um den Foto-Streifen einer Station: Hinzufügen, Betrachten, Löschen mit Rückgängig, Standortübernahme. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class AttachmentsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(
        stations: List<Station>,
        tours: List<Tour>,
        attachments: FakeAttachmentRepository = FakeAttachmentRepository(),
    ): FakeAttachmentRepository {
        val stationRepository = FakeStationRepository(stations)
        val tourRepository = FakeTourRepository(tours, stations = stationRepository)
        compose.setContent {
            CamperLogTheme {
                CamperLogNavHost(
                    tourRepository,
                    FakeVehicleRepository(),
                    FakeLogRepository(),
                    stationRepository,
                    FakeExchangeRateRepository(),
                    FakeVehicleDocumentRepository(),
                    FakeDiaryEntryRepository(),
                    attachments,
                    FakeAttachmentFileStore(),
                    FakeBackupImporter(),
                    ThemeMode.SYSTEM,
                    canShowStartDialogs = false,
                    countryLookup = FakeCountryLookupRepository(),
                    attachmentPickers = FakeAttachmentPickers(),
                ) { }
            }
        }
        return attachments
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
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(id: Long, name: String, latitude: Double? = null, longitude: Double? = null) = Station(
        id = id,
        vehicleId = 1,
        tourId = 1,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2020, 7, 4),
        name = name,
        latitude = latitude,
        longitude = longitude,
        nights = 2,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun openStationDetail(name: String) {
        compose.onNodeWithText("Lofoten").performClick()
        compose.onNodeWithText(name).performClick()
    }

    @Test
    fun addPhotoFromGallery_showsItsThumbnail() {
        val existing = station(id = 1, name = "Camping Moskenes")
        val attachments = start(listOf(existing), listOf(lofoten()))
        openStationDetail("Camping Moskenes")

        compose.onNodeWithContentDescription("Foto hinzufügen").performClick()
        compose.onNodeWithText("Aus der Galerie wählen").performClick()

        compose.onNodeWithContentDescription("Foto 1 von 1").assertExists()
        assertEquals(1, attachments.attachments.size)
    }

    @Test
    fun viewAndDeletePhoto_offersUndo() {
        val existing = station(id = 1, name = "Camping Moskenes")
        val attachments = start(listOf(existing), listOf(lofoten()))
        openStationDetail("Camping Moskenes")
        compose.onNodeWithContentDescription("Foto hinzufügen").performClick()
        compose.onNodeWithText("Aus der Galerie wählen").performClick()

        compose.onNodeWithContentDescription("Foto 1 von 1").performClick()
        compose.onNodeWithContentDescription("Foto löschen").performClick()

        compose.onNodeWithText("Foto gelöscht").assertExists()
        assertEquals(0, attachments.attachments.size)

        compose.onNodeWithText("Rückgängig").performClick()
        assertEquals(1, attachments.attachments.size)
    }

    @Test
    fun galleryPhotoWithoutLocation_canUseTheStopsLocation() {
        val existing = station(id = 1, name = "Camping Moskenes", latitude = 68.0912, longitude = 13.1023)
        val attachments = start(listOf(existing), listOf(lofoten()))
        openStationDetail("Camping Moskenes")
        compose.onNodeWithContentDescription("Foto hinzufügen").performClick()
        compose.onNodeWithText("Aus der Galerie wählen").performClick()
        compose.onNodeWithContentDescription("Foto 1 von 1").assertExists()

        compose.onNodeWithContentDescription("Foto 1 von 1").performClick()
        compose.onNodeWithText("Standort der Station übernehmen").performClick()

        assertEquals(68.0912, attachments.attachments.single().latitude)
        assertEquals(13.1023, attachments.attachments.single().longitude)
    }

    @Test
    fun newStation_hidesAddButtonUntilSaved() {
        val attachments = start(emptyList(), listOf(lofoten()))
        compose.onNodeWithText("Lofoten").performClick()
        compose.onAllNodesWithText("Station hinzufügen").onFirst().performClick()
        compose.onNode(hasText("Schlafplatz") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Speichern schaltet Fotos frei.").assertExists()
        compose.onNodeWithContentDescription("Foto hinzufügen").assertDoesNotExist()
        assertEquals(0, attachments.attachments.size)
    }
}
