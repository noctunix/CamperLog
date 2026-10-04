package app.restvolt.camperlog.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.ui.theme.ThemeMode
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Erzeugt die Screenshots für die README mit Beispieldaten. Läuft nur, wenn die Umgebungsvariable
 * `CAMPERLOG_SCREENSHOTS` auf das Zielverzeichnis zeigt (siehe `scripts/update-screenshots.sh`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
class ReadmeScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val target = System.getenv("CAMPERLOG_SCREENSHOTS")?.let(::File)

    @Before
    fun setUp() {
        assumeTrue("CAMPERLOG_SCREENSHOTS not set", target != null)
        val rates = listOf(
            ExchangeRate(NOK, BigDecimal("11.485"), LocalDate.of(2026, 9, 1), "ECB"),
            ExchangeRate(CHF, BigDecimal("0.9372"), LocalDate.of(2026, 9, 1), "ECB"),
        )
        compose.setContent {
            CamperLogTheme(darkTheme = false) {
                CamperLogNavHost(FakeTourRepository(sampleTours), FakeExchangeRateRepository(rates), FakeBackupImporter(), ThemeMode.LIGHT) { }
            }
        }
    }

    @Test
    fun captureScreens() {
        capture("tours")
        compose.onNodeWithText("Lofoten").performClick()
        capture("detail")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Overview").performClick()
        capture("overview")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Data").performClick()
        capture("data")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val dir = checkNotNull(target).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        val NOK: Currency = Currency.getInstance("NOK")
        val CHF: Currency = Currency.getInstance("CHF")

        fun sample(
            id: Long,
            start: String,
            days: Int,
            destination: String,
            type: TourType,
            km: Int,
            costs: List<Money>,
            notes: String = "",
        ): Tour {
            val startDate = LocalDate.parse(start)
            val stamp = Instant.parse("${start}T18:00:00Z")
            return Tour(
                id = id,
                uuid = "00000000-0000-4000-8000-00000000000$id",
                startDate = startDate,
                endDate = startDate.plusDays(days - 1L),
                destination = destination,
                tourType = type,
                travelDays = days,
                overnightStays = (days - 1).coerceAtLeast(0),
                distanceKm = km,
                costs = costs,
                pitchAssigned = type == TourType.VACATION,
                electricityFlatRate = ElectricityFlatRate.YES,
                lteQuality = LteQuality.GOOD,
                pitchSlope = PitchSlope.LEVEL,
                levelingBlocksUsed = id % 2 == 0L,
                notes = notes,
                mapLink = null,
                createdAt = stamp,
                updatedAt = stamp,
            )
        }

        val sampleTours = listOf(
            sample(
                1, "2026-07-04", 14, "Lofoten", TourType.VACATION, 3_420,
                listOf(Money(48_650, EUR), Money(612_000, NOK)),
                notes = "Ferry Bodø–Moskenes booked in advance. Quiet pitch right at the beach.",
            ),
            sample(2, "2026-05-29", 3, "Lake Garda", TourType.WEEKEND, 1_180, listOf(Money(21_480, EUR))),
            sample(3, "2026-04-18", 1, "Black Forest", TourType.DAY_TRIP, 240, listOf(Money(1_850, EUR))),
            sample(4, "2025-09-12", 6, "Engadin", TourType.VACATION, 1_560, listOf(Money(9_000, EUR), Money(38_500, CHF))),
            sample(5, "2025-06-20", 3, "Moselle Valley", TourType.WEEKEND, 520, listOf(Money(16_200, EUR))),
        )
    }
}
