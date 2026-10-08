package app.restvolt.camperlog.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.LOCATION_PERMISSIONS
import app.restvolt.camperlog.share.openAppDetailsSettings

/**
 * Schalter „Aktuellen Standort nutzen“. Das Einschalten fragt direkt die Standortberechtigung an; der
 * Tipp auf den Schalter ist der Kontext dafür. Ohne Berechtigung bleibt er aus und ein Hinweis führt in
 * die App-Einstellungen. [onSwitchedOff] läuft nach dem Ausschalten (Hinweis zum Widerruf).
 */
@Composable
internal fun LocationSwitchSection(settings: LocationSettings, onSwitchedOff: () -> Unit) {
    val context = LocalContext.current
    val enabled by settings.values.collectAsStateWithLifecycle()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = LOCATION_PERMISSIONS.any { result[it] == true || isGranted(context, it) }
        permissionDenied = !granted
        settings.enabled = granted
    }

    SwitchSettingRow(
        title = stringResource(R.string.location_switch_title),
        supportingText = stringResource(R.string.settings_location_switch_support),
        checked = enabled,
        onCheckedChange = { wantsEnabled ->
            if (!wantsEnabled) {
                permissionDenied = false
                settings.enabled = false
                onSwitchedOff()
            } else if (LOCATION_PERMISSIONS.any { isGranted(context, it) }) {
                permissionDenied = false
                settings.enabled = true
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        },
    )
    if (permissionDenied) PermissionDeniedHint(stringResource(R.string.settings_location_denied_hint))
}

/** Hinweis nach einer verweigerten Berechtigung mit Sprung in die App-Einstellungen des Systems. */
@Composable
internal fun PermissionDeniedHint(text: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { context.openAppDetailsSettings() }) {
            Text(stringResource(R.string.settings_track_open_app_settings))
        }
    }
}
