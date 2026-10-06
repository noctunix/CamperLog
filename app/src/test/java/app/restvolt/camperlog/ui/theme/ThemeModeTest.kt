package app.restvolt.camperlog.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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

    @Test
    fun chosenThemeSurvivesNewSettingsInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertEquals(ThemeMode.SYSTEM, ThemeSettings(context).mode)
            ThemeSettings(context).mode = ThemeMode.DARK
            assertEquals(ThemeMode.DARK, ThemeSettings(context).mode)
            assertEquals(true, ThemeSettings(context).mode.isDark(systemDark = false))
            ThemeSettings(context).mode = ThemeMode.LIGHT
            assertEquals(false, ThemeSettings(context).mode.isDark(systemDark = true))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
