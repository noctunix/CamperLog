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
import app.restvolt.camperlog.reminders.EXTRA_OPEN_DATA
import app.restvolt.camperlog.reminders.EXTRA_OPEN_DOCUMENT_ID
import app.restvolt.camperlog.reminders.EXTRA_OPEN_VEHICLE_ID
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.share.cleanUpExports
import app.restvolt.camperlog.ui.CamperLogNavHost
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import app.restvolt.camperlog.ui.theme.ThemeSettings
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Einzige Activity; hostet die Compose-Navigation. `launchMode="singleTask"` (siehe Manifest) sorgt
 * dafür, dass ein `geo:`-Link auf eine bereits laufende Instanz trifft ([onNewIntent]) statt den
 * bestehenden Rückstapel zu verdoppeln.
 */
class MainActivity : ComponentActivity() {

    private var pendingGeoIntent by mutableStateOf<GeoIntentLocation?>(null)
    private var pendingVehicleId by mutableStateOf<Long?>(null)
    private var pendingDocumentId by mutableStateOf<Long?>(null)
    private var pendingOpenData by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CamperLogApp
        if (savedInstanceState == null) {
            lifecycleScope.launch { cleanUpExports(applicationContext) }
            lifecycleScope.launch { app.attachmentFileStore.sweepOrphanFiles(app.attachments.allFileNames(), Instant.now()) }
            pendingGeoIntent = parseGeoIntent(intent?.dataString)
            pendingVehicleId = intent?.openVehicleIdExtra()
            pendingDocumentId = intent?.openDocumentIdExtra()
            pendingOpenData = intent?.getBooleanExtra(EXTRA_OPEN_DATA, false) == true
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
                    documents = app.vehicleDocuments,
                    diaryEntries = app.diaryEntries,
                    checklists = app.checklists,
                    checklistTemplates = app.checklistTemplates,
                    attachments = app.attachments,
                    attachmentFileStore = app.attachmentFileStore,
                    backupImporter = app.backupImporter,
                    themeMode = themeMode,
                    stations = app.stations,
                    tracks = app.tracks,
                    pendingGeoIntent = pendingGeoIntent,
                    onGeoIntentHandled = { pendingGeoIntent = null },
                    pendingVehicleId = pendingVehicleId,
                    onVehicleIntentHandled = { pendingVehicleId = null },
                    pendingDocumentId = pendingDocumentId,
                    onDocumentIntentHandled = { pendingDocumentId = null },
                    pendingOpenData = pendingOpenData,
                    onOpenDataHandled = { pendingOpenData = false },
                    onThemeModeChange = { selected ->
                        themeSettings.mode = selected
                        themeMode = selected
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Eine durch Prozessende unterbrochene Trackaufzeichnung läuft weiter, sobald die App sichtbar ist.
        TrackRecordingService.resumeIfNeeded(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingGeoIntent = parseGeoIntent(intent.dataString)
        pendingVehicleId = intent.openVehicleIdExtra()
        pendingDocumentId = intent.openDocumentIdExtra()
        pendingOpenData = intent.getBooleanExtra(EXTRA_OPEN_DATA, false)
    }
}

/** Fahrzeug-id einer getippten Wartungs-Benachrichtigung, oder `null` ohne diesen Extra. */
private fun Intent.openVehicleIdExtra(): Long? = getLongExtra(EXTRA_OPEN_VEHICLE_ID, -1L).takeIf { it > 0 }

/** Fahrzeugdokument-id einer getippten Ablauf-Erinnerung, oder `null` ohne diesen Extra. */
private fun Intent.openDocumentIdExtra(): Long? = getLongExtra(EXTRA_OPEN_DOCUMENT_ID, -1L).takeIf { it > 0 }
