package de.hannes.camperlog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.hannes.camperlog.ui.CamperLogNavHost
import de.hannes.camperlog.ui.theme.CamperLogTheme

/** Einzige Activity; hostet die Compose-Navigation. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val repository = (application as CamperLogApp).repository
        setContent {
            CamperLogTheme {
                CamperLogNavHost(repository)
            }
        }
    }
}
