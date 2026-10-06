package app.restvolt.camperlog.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.LOCATION_PERMISSIONS
import app.restvolt.camperlog.domain.LocationCaptureController
import app.restvolt.camperlog.domain.LocationCaptureState
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.domain.isApproximateFix
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.share.openAppDetailsSettings
import app.restvolt.camperlog.share.openLocationSourceSettings
import java.util.Locale

/** Von der Oberfläche beigesteuerte Bedienelemente des [LocationCaptureController]. */
data class LocationCaptureHandlers(val onTap: () -> Unit, val onContinueRationale: () -> Unit)

/**
 * Verdrahtet den System-Berechtigungsdialog mit [controller]: ob ein Tastendruck direkt den Dialog
 * auslöst, eine Begründung oder den Hinweis auf eine dauerhafte Ablehnung zeigt, hängt von
 * `shouldShowRequestPermissionRationale` ab, die eine Activity braucht und deshalb hier statt im
 * Controller lebt (kein Activity-Leck in einem ViewModel).
 */
@Composable
fun rememberLocationCaptureHandlers(controller: LocationCaptureController): LocationCaptureHandlers {
    val activity = checkNotNull(LocalActivity.current) { "Standortanfrage braucht eine Activity" }
    fun shouldShowRationale() =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        controller.onPermissionResult(granted = results.values.any { it }, shouldShowRationale = shouldShowRationale())
    }
    val permissionRequests by controller.permissionRequests.collectAsStateWithLifecycle()
    LaunchedEffect(permissionRequests) {
        if (permissionRequests > 0) launcher.launch(LOCATION_PERMISSIONS)
    }
    return remember(controller) {
        LocationCaptureHandlers(
            onTap = { controller.onButtonTapped(::shouldShowRationale) },
            onContinueRationale = controller::onContinueRationale,
        )
    }
}

/**
 * Inhalt der Standortbestimmung außer [LocationCaptureState.Found], das der Aufrufer selbst
 * zeigt (Stationsformular übernimmt den Fix sofort ins Feld, "Wo bin ich?" zeigt ihn groß an).
 * [onEnterManually] blendet bei dauerhafter Ablehnung eine dritte Aktion ein; `null` lässt sie weg.
 */
@Composable
fun LocationCaptureSection(
    controller: LocationCaptureController,
    buttonLabel: String,
    modifier: Modifier = Modifier,
    onEnterManually: (() -> Unit)? = null,
    handlers: LocationCaptureHandlers = rememberLocationCaptureHandlers(controller),
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val current = state) {
        LocationCaptureState.Ready -> {
            FilledTonalButton(onClick = handlers.onTap, modifier = modifier) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(buttonLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
        LocationCaptureState.PermissionRationale -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_rationale_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.location_rationale_not_now)) }
                    TextButton(onClick = handlers.onContinueRationale) { Text(stringResource(R.string.location_rationale_continue)) }
                }
            }
        }
        LocationCaptureState.PermissionPermanentlyDenied -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_permanently_denied_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { context.openAppDetailsSettings() }) { Text(stringResource(R.string.location_action_app_settings)) }
                    onEnterManually?.let { enterManually ->
                        TextButton(onClick = { controller.dismiss(); enterManually() }) { Text(stringResource(R.string.location_action_enter_coordinates)) }
                    }
                    TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
        LocationCaptureState.ServicesOff -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_services_off_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.openLocationSourceSettings() }) { Text(stringResource(R.string.location_action_turn_on)) }
            }
        }
        LocationCaptureState.Searching -> {
            Row(
                modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.location_searching_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        is LocationCaptureState.NotFound -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.location_not_found_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = controller::retry) { Text(stringResource(R.string.location_action_retry)) }
                    current.lastKnownOffer?.let { offer ->
                        TextButton(onClick = { controller.useLastKnown(offer) }) {
                            Text(stringResource(R.string.location_use_last_known, ageLabel(offer.ageMillis), accuracyLabel(offer.accuracyM)))
                        }
                    }
                }
            }
        }
        is LocationCaptureState.Found -> Unit
    }
}

/** Lesbare Zusammenfassung eines gefundenen Fixes: Koordinaten, Genauigkeit, ggf. "zuletzt bekannt". */
@Composable
fun locationFixSummary(fix: LocationFix, locale: Locale): String {
    val coordinates = formatCoordinates(fix.latitude, fix.longitude, locale)
    val accuracy = accuracyLabel(fix.accuracyM)
    return when {
        fix.ageMillis > 0 -> stringResource(R.string.location_fix_summary_last_known, coordinates, accuracy, ageLabel(fix.ageMillis))
        isApproximateFix(fix.accuracyM) -> stringResource(R.string.location_fix_summary_approximate, coordinates, accuracy)
        else -> stringResource(R.string.location_fix_summary, coordinates, accuracy)
    }
}

@Composable
internal fun accuracyLabel(accuracyM: Int?): String = when {
    accuracyM == null -> ""
    accuracyM >= 1_000 -> stringResource(R.string.location_accuracy_km, Math.round(accuracyM / 1000.0))
    else -> stringResource(R.string.location_accuracy_meters, accuracyM)
}

@Composable
internal fun ageLabel(ageMillis: Long): String = stringResource(R.string.location_minutes_ago_short, (ageMillis / 60_000L).toInt().coerceAtLeast(1))
