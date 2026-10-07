package app.restvolt.camperlog.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.LOCATION_PERMISSIONS
import app.restvolt.camperlog.domain.TrackInterval
import app.restvolt.camperlog.share.openAppDetailsSettings
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.tracking.trackIntervalLabel

/**
 * Schalter "Trackaufzeichnung" mit Intervall und Ladeoption. Die Standortberechtigung (und ab
 * Android 13 die Benachrichtigungsberechtigung für die laufende Aufzeichnung) wird erst beim
 * Einschalten angefragt; ohne Standortberechtigung bleibt der Schalter aus. Beim Ausschalten beendet
 * der Dienst eine laufende Aufzeichnung selbst, weil er den Schalter beobachtet.
 */
@Composable
internal fun TrackRecordingSettingsSection(settings: TrackRecordingSettings, onSwitchedOff: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val preferences by settings.values.collectAsStateWithLifecycle()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var pickInterval by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = LOCATION_PERMISSIONS.any { result[it] == true || isGranted(context, it) }
        permissionDenied = !granted
        settings.enabled = granted
    }

    SwitchSettingRow(
        title = stringResource(R.string.settings_track_switch_title),
        supportingText = stringResource(R.string.settings_track_switch_support),
        checked = preferences.enabled,
        onCheckedChange = { wantsEnabled ->
            if (!wantsEnabled) {
                permissionDenied = false
                settings.enabled = false
                onSwitchedOff()
            } else {
                permissionLauncher.launch(trackPermissions())
            }
        },
    )
    if (permissionDenied) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.settings_track_denied_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { context.openAppDetailsSettings() }) {
                Text(stringResource(R.string.settings_track_open_app_settings))
            }
        }
    }
    if (preferences.enabled) {
        ReminderChoiceRow(
            label = stringResource(R.string.settings_track_interval),
            valueText = trackIntervalLabel(resources, preferences.interval),
            onClick = { pickInterval = true },
        )
        SwitchSettingRow(
            title = stringResource(R.string.settings_track_charging_title),
            supportingText = stringResource(R.string.settings_track_charging_support),
            checked = preferences.fasterWhileCharging,
            onCheckedChange = { settings.fasterWhileCharging = it },
        )
    }

    if (pickInterval) {
        IntChoiceDialog(
            title = stringResource(R.string.settings_track_interval),
            options = TrackInterval.entries.map { it.seconds },
            selected = preferences.interval.seconds,
            optionLabel = { seconds -> trackIntervalLabel(resources, TrackInterval.entries.first { it.seconds == seconds }) },
            onSelect = { seconds ->
                settings.interval = TrackInterval.entries.first { it.seconds == seconds }
                pickInterval = false
            },
            onDismiss = { pickInterval = false },
        )
    }
}

private fun trackPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LOCATION_PERMISSIONS + Manifest.permission.POST_NOTIFICATIONS
    else LOCATION_PERMISSIONS

private fun isGranted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
