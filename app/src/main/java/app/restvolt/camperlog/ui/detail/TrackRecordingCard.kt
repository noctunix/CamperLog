package app.restvolt.camperlog.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.TrackSummary
import app.restvolt.camperlog.domain.trackLengthMeters
import app.restvolt.camperlog.share.openAppDetailsSettings
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.tracking.trackIntervalLabel
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.settings.SwitchSettingRow
import app.restvolt.camperlog.ui.settings.trackPermissions
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Karte "Track" im Tourdetail. Für offene Touren (kein Enddatum) schaltet ein Schalter die
 * Aufzeichnung dieser Tour ein, dazu Pausieren/Fortsetzen und Löschen des Tracks. Für beendete
 * Touren zeigt sie nur noch die Zusammenfassung und "Track löschen", sofern bereits Punkte
 * aufgezeichnet wurden. Fehlt die Standortberechtigung, wird sie beim Einschalten angefragt. Vor
 * dem ersten Start erscheint einmalig der Hinweis zur Akkuoptimierung, sofern CamperLog nicht
 * schon ausgenommen ist.
 */
@Composable
internal fun TrackRecordingCard(tourId: Long, tracks: TrackRepository, settings: TrackRecordingSettings, hasEndDate: Boolean) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val summaryFlow = remember(tracks, tourId) { tracks.observeSummary(tourId) }
    val summary by summaryFlow.collectAsStateWithLifecycle(initialValue = TrackSummary.EMPTY)
    val lengthFlow = remember(tracks, tourId) { tracks.observeForTour(tourId).map { trackLengthMeters(it) } }
    val lengthMeters by lengthFlow.collectAsStateWithLifecycle(initialValue = 0.0)
    val locale = currentLocale()
    val active by settings.active.collectAsStateWithLifecycle()
    val trackedTourId by settings.tracked.collectAsStateWithLifecycle()
    val preferences by settings.values.collectAsStateWithLifecycle()
    val startFailed by settings.startFailed.collectAsStateWithLifecycle()
    val pausedByReboot by settings.pausedByRebootFlow.collectAsStateWithLifecycle()
    var showBatteryHint by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showStartFailedHint by rememberSaveable { mutableStateOf(false) }
    val isTrackedHere = trackedTourId == tourId
    val isRunning = isTrackedHere && active?.tourId == tourId
    val isPaused = isTrackedHere && !isRunning
    val recordingElsewhere = trackedTourId != null && !isTrackedHere

    if (hasEndDate && summary.points == 0) return

    LaunchedEffect(startFailed) {
        if (startFailed) {
            showStartFailedHint = true
            settings.clearStartFailed()
        }
    }

    fun startRecording() {
        if (!settings.batteryHintShown && !isIgnoringBatteryOptimizations(context)) {
            settings.batteryHintShown = true
            showBatteryHint = true
        }
        TrackRecordingService.startForTour(context, tourId)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = !TrackRecordingService.hasLocationPermission(context)
        if (!permissionDenied) startRecording()
    }

    SectionCard {
        Text(
            stringResource(R.string.tour_track_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (summary.points == 0) {
                stringResource(R.string.tour_track_empty)
            } else {
                stringResource(R.string.tour_track_length, formatTrackKm(lengthMeters, locale)) + " · " +
                    pluralStringResource(R.plurals.tour_track_points, summary.points, summary.points) + " · " +
                    pluralStringResource(R.plurals.tour_track_segments, summary.segments, summary.segments)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!hasEndDate) {
            when {
                isRunning -> Text(
                    stringResource(R.string.tour_track_recording, trackIntervalLabel(resources, preferences.interval)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                isPaused -> Text(
                    stringResource(if (pausedByReboot) R.string.tour_track_paused_by_reboot else R.string.tour_track_paused),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recordingElsewhere -> Text(
                    stringResource(R.string.tour_track_other_tour),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (permissionDenied) {
                Text(
                    stringResource(R.string.settings_track_denied_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { context.openAppDetailsSettings() }) {
                    Text(stringResource(R.string.settings_track_open_app_settings))
                }
            }
            if (showStartFailedHint) {
                Text(
                    stringResource(R.string.tour_track_start_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SwitchSettingRow(
                title = stringResource(R.string.tour_track_switch_title),
                supportingText = stringResource(R.string.tour_track_switch_support),
                checked = isTrackedHere,
                onCheckedChange = { wantsOn ->
                    showStartFailedHint = false
                    if (wantsOn) {
                        if (TrackRecordingService.hasLocationPermission(context)) {
                            permissionDenied = false
                            startRecording()
                        } else {
                            permissionLauncher.launch(trackPermissions())
                        }
                    } else {
                        TrackRecordingService.stop(context)
                    }
                },
            )
            if (isRunning) {
                Button(onClick = { TrackRecordingService.pause(context) }) { Text(stringResource(R.string.tour_track_pause)) }
            } else if (isPaused) {
                Button(onClick = {
                    showStartFailedHint = false
                    if (TrackRecordingService.hasLocationPermission(context)) {
                        startRecording()
                    } else {
                        permissionLauncher.launch(trackPermissions())
                    }
                }) { Text(stringResource(R.string.tour_track_resume)) }
            }
        }
        if (summary.points > 0) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !isRunning) {
                    Text(stringResource(R.string.tour_track_delete))
                }
            }
        }
    }

    if (showBatteryHint) {
        AlertDialog(
            onDismissRequest = { showBatteryHint = false },
            title = { Text(stringResource(R.string.track_battery_title)) },
            text = { Text(stringResource(R.string.track_battery_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryHint = false
                    openBatteryOptimizationSettings(context)
                }) { Text(stringResource(R.string.track_battery_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryHint = false }) { Text(stringResource(R.string.track_battery_later)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tour_track_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.tour_track_delete_message, summary.points, summary.points)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { tracks.deleteForTour(tourId) }
                }) { Text(stringResource(R.string.tour_track_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Öffnet die Liste der Akkuoptimierung; ohne passende Systemseite die App-Einstellungen. */
private fun openBatteryOptimizationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.openAppDetailsSettings()
    }
}

/** Tracklänge in Kilometern mit einer Nachkommastelle im Format der [locale]. */
internal fun formatTrackKm(meters: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(meters / 1000)
