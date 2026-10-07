package app.restvolt.camperlog.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.domain.ExpiringVignette
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/** [VignetteWarningsCard] für sich allein: Anzeige der Warnzeile und Weiterleitung per Klick zur Station. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class VignetteWarningsCardFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsAWarningPerVignetteAndOpensItsStationOnClick() {
        var openedStationId: Long? = null
        val warnings = listOf(
            ExpiringVignette(stationId = 7, countryCode = "AT", validUntil = LocalDate.of(2026, 7, 9)),
            ExpiringVignette(stationId = 8, countryCode = null, validUntil = LocalDate.of(2026, 7, 10)),
        )

        compose.setContent {
            CamperLogTheme {
                VignetteWarningsCard(warnings = warnings, locale = Locale.GERMANY, onOpenStation = { openedStationId = it })
            }
        }

        compose.onNodeWithText("Vignette 🇦🇹 Österreich endet am 09.07.2026, vor dem Tourende").assertExists()
        compose.onNodeWithText("Vignette endet am 10.07.2026, vor dem Tourende").assertExists()

        compose.onNodeWithText("Vignette 🇦🇹 Österreich endet am 09.07.2026, vor dem Tourende").performClick()

        assertEquals(7L, openedStationId)
    }

    @Test
    fun showsNothingWithoutWarnings() {
        compose.setContent {
            CamperLogTheme {
                VignetteWarningsCard(warnings = emptyList(), locale = Locale.GERMANY, onOpenStation = {})
            }
        }

        compose.onNodeWithText("endet am", substring = true).assertDoesNotExist()
    }
}
