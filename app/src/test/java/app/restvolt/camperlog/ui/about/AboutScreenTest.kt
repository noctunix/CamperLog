package app.restvolt.camperlog.ui.about

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.restvolt.camperlog.BuildConfig
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Über-Bildschirm: Version, aufklappbare Abschnitte, Lizenztext und externe Links. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class AboutScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(guidedTours: List<GuidedTourEntry> = emptyList()) {
        compose.setContent {
            CamperLogTheme {
                AboutScreen(onBack = {}, guidedTours = guidedTours)
            }
        }
    }

    @Test
    fun showsVersionFromBuildConfig() {
        start()

        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertExists()
    }

    @Test
    fun sourceSectionExpandsAndCollapses() {
        start()

        compose.onNodeWithText("Quellcode").performScrollTo().performClick()
        compose.onNodeWithText("Repository öffnen").assertExists()

        compose.onNodeWithText("Quellcode").performScrollTo().performClick()
        compose.onNodeWithText("Repository öffnen").assertDoesNotExist()
    }

    @Test
    fun licenseTextDialogShowsAssetContent() {
        start()

        compose.onNodeWithText("Lizenz").performScrollTo().performClick()
        compose.onNodeWithText("Lizenztext anzeigen").performScrollTo().performClick()

        compose.onNodeWithText("TERMS AND CONDITIONS FOR USE", substring = true).assertExists()
    }

    @Test
    fun repositoryLinkFiresViewIntent() {
        start()

        compose.onNodeWithText("Quellcode").performScrollTo().performClick()
        compose.onNodeWithText("Repository öffnen").performScrollTo().performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://github.com/noctunix/CamperLog", started.data?.toString())
    }

    @Test
    fun dataSourcesSectionShowsAllAttributionsAndLinksToOpenMeteo() {
        start()

        compose.onNodeWithText("Datenquellen").performScrollTo().performClick()
        compose.onNodeWithText("Kartendaten © OpenStreetMap-Mitwirkende (ODbL)").assertExists()
        compose.onNodeWithText("Landesgrenzen von Natural Earth (public domain)").assertExists()
        compose.onNodeWithText("Wetterdaten von Open-Meteo.com (CC BY 4.0)").performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://open-meteo.com/", started.data?.toString())
    }

    @Test
    fun naturalEarthLinkFiresViewIntent() {
        start()

        compose.onNodeWithText("Datenquellen").performScrollTo().performClick()
        compose.onNodeWithText("Landesgrenzen von Natural Earth (public domain)").performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.naturalearthdata.com/", started.data?.toString())
    }

    @Test
    fun nominatimLinkFiresViewIntent() {
        start()

        compose.onNodeWithText("Datenquellen").performScrollTo().performClick()
        compose.onNodeWithText("Ortssuche von Nominatim (OpenStreetMap-Daten, ODbL)").performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://nominatim.org/", started.data?.toString())
    }

    @Test
    fun privacySectionMentionsTheInternetPermissionHonestly() {
        start()

        compose.onNodeWithText("Datenschutz").performScrollTo().performClick()
        compose.onNodeWithText(
            "Wetter & Karte (optional): CamperLog hat die Internet-Berechtigung, nutzt sie aber nur, wenn du das einschaltest. " +
                "Beim Wetterabruf gehen die Koordinaten der Station, auf etwa 1 km gerundet, an Open-Meteo. " +
                "Die Karte lädt den angezeigten Ausschnitt von OpenStreetMap. Die Ortssuche sendet deinen eingegebenen Text " +
                "an OpenStreetMap Nominatim. Alle drei Dienste sehen deine IP-Adresse.",
        ).assertExists()
    }

    @Test
    fun guidedTourRow_startButtonInvokesCallback() {
        var invoked = false
        start(
            guidedTours = listOf(
                GuidedTourEntry(titleRes = R.string.guide_tour_introduction_title, completed = false, onStart = { invoked = true }),
            ),
        )

        compose.onNodeWithText("Rundgang").performScrollTo().assertExists()
        compose.onNodeWithText("Starten").performScrollTo().performClick()

        assertTrue(invoked)
    }

    @Test
    fun guidedTourRow_completedShowsCheckmarkAndRepeatLabel() {
        start(
            guidedTours = listOf(
                GuidedTourEntry(titleRes = R.string.guide_tour_create_first_title, completed = true, onStart = {}),
            ),
        )

        compose.onNodeWithText("Erste Tour anlegen").performScrollTo().assertExists()
        compose.onNodeWithText("Abgeschlossen").assertExists()
        compose.onNodeWithText("Wiederholen").assertExists()
    }
}
