package app.restvolt.camperlog.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.restvolt.camperlog.R

/** Zugriffszweck einer freiwilligen Funktion; die Aktivierung startet selbst noch keinen Zugriff. */
enum class OptionalFeature(@param:StringRes val explanation: Int) {
    LOCATION(R.string.feature_location_explanation),
    INTERNET(R.string.feature_internet_explanation),
    TRACK(R.string.feature_track_explanation),
}

/** Sichtbarer Einstieg einer ausgeschalteten Funktion; [onActivate] speichert nur die bewusste Freigabe. */
@Composable
fun InactiveFeatureButton(label: String, feature: OptionalFeature, onActivate: () -> Unit, modifier: Modifier = Modifier) {
    var showActivation by rememberSaveable { mutableStateOf(false) }
    val inactive = stringResource(R.string.feature_inactive)
    TextButton(
        onClick = { showActivation = true },
        modifier = modifier.semantics { stateDescription = inactive },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Text(stringResource(R.string.feature_inactive_action, label))
    }
    if (showActivation) {
        FeatureActivationDialog(feature, onDismiss = { showActivation = false }, onActivate = {
            showActivation = false
            onActivate()
        })
    }
}

/** Kartenaktion mit ausdrücklicher Internet-Freigabe; [onClick] läuft erst bei aktivierter Funktion. */
@Composable
fun OptionalFeatureIconButton(
    enabled: Boolean,
    available: Boolean,
    unavailableReason: String,
    feature: OptionalFeature,
    onActivate: () -> Unit,
    onClick: () -> Unit,
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    var showActivation by rememberSaveable { mutableStateOf(false) }
    var showUnavailable by rememberSaveable { mutableStateOf(false) }
    val inactive = stringResource(R.string.feature_inactive)
    IconButton(
        onClick = { if (!available) showUnavailable = true else if (enabled) onClick() else showActivation = true },
        modifier = if (enabled) modifier else modifier.semantics { stateDescription = inactive },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = if (enabled) label else stringResource(R.string.feature_inactive_action, label),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showActivation) {
        FeatureActivationDialog(feature, onDismiss = { showActivation = false }, onActivate = {
            showActivation = false
            onActivate()
            onClick()
        })
    }
    if (showUnavailable) {
        AlertDialog(
            onDismissRequest = { showUnavailable = false },
            title = { Text(label) },
            text = { Text(unavailableReason) },
            confirmButton = { TextButton(onClick = { showUnavailable = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun FeatureActivationDialog(feature: OptionalFeature, onDismiss: () -> Unit, onActivate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_activate_title)) },
        text = { Text(stringResource(feature.explanation)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = { Button(onClick = onActivate) { Text(stringResource(R.string.feature_activate)) } },
    )
}
