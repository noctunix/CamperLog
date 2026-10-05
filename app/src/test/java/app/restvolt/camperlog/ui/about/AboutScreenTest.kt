package app.restvolt.camperlog.ui.about

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.restvolt.camperlog.BuildConfig
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

    private fun start(onShowIntroductionAgain: () -> Unit = {}) {
        compose.setContent {
            CamperLogTheme {
                AboutScreen(onBack = {}, onShowIntroductionAgain = onShowIntroductionAgain)
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
    fun showIntroductionAgainRowInvokesCallback() {
        var invoked = false
        start(onShowIntroductionAgain = { invoked = true })

        compose.onNodeWithText("Einführung erneut anzeigen").performScrollTo().performClick()

        assertTrue(invoked)
    }
}
