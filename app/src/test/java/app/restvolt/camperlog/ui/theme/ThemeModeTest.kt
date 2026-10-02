package app.restvolt.camperlog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Das Theme folgt dem hellen/dunklen Systemmodus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeModeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun appliedScheme(): ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent { CamperLogTheme { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        return scheme
    }

    @Test
    fun dayMode_usesLightColors() = assertSame(LightColors, appliedScheme())

    @Test
    @Config(qualifiers = "+night")
    fun nightMode_usesDarkColors() = assertSame(DarkColors, appliedScheme())
}
