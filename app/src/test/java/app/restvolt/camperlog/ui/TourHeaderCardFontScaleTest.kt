package app.restvolt.camperlog.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die Kopfkarte der Tourdetailseite (Statuszeile, Zeitraum, Kennzahlen-Kacheln) bei 1,3-facher
 * Schriftgröße auf 360 dp Breite: ein langer Fahrzeugname und die Kennzahlen-Kacheln sollen bei
 * Platzmangel umbrechen (FlowRow, siehe [TourDetailScreen]) statt abgeschnitten oder aus dem Bild
 * gedrängt zu werden - keiner der Texte ist hier auf eine Zeile begrenzt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
class TourHeaderCardFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val longVehicleName = "Unser ziemlich langer Wohnmobilname"

    @Test
    fun headerCard_atLargeFontScale_staysFullyDisplayed() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = longVehicleName), defaultVehicle(id = 2, name = "Zweitfahrzeug")),
        )
        val running = tour(id = 1).copy(startDate = LocalDate.now().minusDays(13), endDate = null)
        val overnight = Station(
            id = 1,
            uuid = "station-1",
            vehicleId = 1,
            tourId = running.id,
            type = StationType.OVERNIGHT,
            date = LocalDate.now().minusDays(1),
            nights = 13,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                CamperLogTheme {
                    CamperLogNavHost(
                        FakeTourRepository(listOf(running)),
                        vehicles,
                        FakeLogRepository(),
                        FakeStationRepository(listOf(overnight)),
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
        }

        compose.onNode(hasText("Lofoten") and hasAnyAncestor(hasScrollAction())).performClick()

        // Unzusammengeführt: Jede Kennzahlen-Kachel fasst Wert und Bezeichner zu einer Sprachausgabe
        // zusammen (siehe MetricTile); im unzusammengeführten Baum bleiben beide Texte einzeln prüfbar.
        listOf("Unterwegs · Tag 14", "Reisetage", "Übernachtungen", "Strecke", longVehicleName).forEach { label ->
            compose.onNode(hasText(label, substring = true), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    private fun tour(id: Long) = Tour(
        id = id,
        vehicleId = 1,
        startDate = LocalDate.of(2026, 7, 4),
        endDate = LocalDate.of(2026, 7, 17),
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
}
