package app.restvolt.camperlog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.restvolt.camperlog.share.cleanUpExports
import app.restvolt.camperlog.ui.CamperLogNavHost
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeSettings
import kotlinx.coroutines.launch

/** Einzige Activity; hostet die Compose-Navigation. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CamperLogApp
        if (savedInstanceState == null) {
            lifecycleScope.launch { cleanUpExports(applicationContext) }
        }
        setContent {
            val themeSettings = remember { ThemeSettings(this) }
            var themeMode by remember { mutableStateOf(themeSettings.mode) }
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                val barStyle = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            CamperLogTheme(darkTheme = darkTheme) {
                CamperLogNavHost(app.repository, app.vehicles, app.exchangeRates, app.backupImporter, themeMode) { selected ->
                    themeSettings.mode = selected
                    themeMode = selected
                }
            }
        }
    }
}
