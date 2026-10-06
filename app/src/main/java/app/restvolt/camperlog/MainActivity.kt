package app.restvolt.camperlog

import android.content.Intent
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
import app.restvolt.camperlog.domain.GeoIntentLocation
import app.restvolt.camperlog.domain.parseGeoIntent
import app.restvolt.camperlog.share.cleanUpExports
import app.restvolt.camperlog.ui.CamperLogNavHost
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeSettings
import kotlinx.coroutines.launch

/**
 * Einzige Activity; hostet die Compose-Navigation. `launchMode="singleTask"` (siehe Manifest) sorgt
 * dafür, dass ein `geo:`-Link auf eine bereits laufende Instanz trifft ([onNewIntent]) statt den
 * bestehenden Rückstapel zu verdoppeln (13.5 Nr. 4).
 */
class MainActivity : ComponentActivity() {

    private var pendingGeoIntent by mutableStateOf<GeoIntentLocation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CamperLogApp
        if (savedInstanceState == null) {
            lifecycleScope.launch { cleanUpExports(applicationContext) }
            pendingGeoIntent = parseGeoIntent(intent?.dataString)
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
                CamperLogNavHost(
                    repository = app.repository,
                    vehicles = app.vehicles,
                    logbook = app.logbook,
                    exchangeRates = app.exchangeRates,
                    backupImporter = app.backupImporter,
                    themeMode = themeMode,
                    stations = app.stations,
                    pendingGeoIntent = pendingGeoIntent,
                    onGeoIntentHandled = { pendingGeoIntent = null },
                    onThemeModeChange = { selected ->
                        themeSettings.mode = selected
                        themeMode = selected
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingGeoIntent = parseGeoIntent(intent.dataString)
    }
}
