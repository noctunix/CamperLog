package app.restvolt.camperlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.displayTitle
import app.restvolt.camperlog.domain.formatTimeOfDay
import app.restvolt.camperlog.domain.trackLengthMeters
import app.restvolt.camperlog.tracking.TrackRecordingService
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.detail.formatTrackKm
import kotlinx.coroutines.flow.map

/**
 * Leiste über der unteren Navigation, solange [TrackRecordingSettings.trackedTourId] gesetzt ist:
 * zeigt die markierte Tour mit ihrer aufgezeichneten Länge und erlaubt Pausieren/Fortsetzen und
 * Beenden, ohne erst die Tourdetailseite zu öffnen. Tippen auf die Leiste öffnet sie.
 */
@Composable
internal fun TrackRecordingBar(
    tourId: Long,
    tours: TourRepository,
    tracks: TrackRepository,
    settings: TrackRecordingSettings,
    onOpenTour: (Long) -> Unit,
) {
    val context = LocalContext.current
    val tour by remember(tours, tourId) { tours.observeTour(tourId) }.collectAsStateWithLifecycle(initialValue = null)
    val lengthMeters by remember(tracks, tourId) { tracks.observeForTour(tourId).map { trackLengthMeters(it) } }
        .collectAsStateWithLifecycle(initialValue = 0.0)
    val active by settings.active.collectAsStateWithLifecycle()
    val pausedByReboot by settings.pausedByRebootFlow.collectAsStateWithLifecycle()
    val resumedAfterBoot by settings.resumedAfterBootFlow.collectAsStateWithLifecycle()
    val scheduledResumeAtMillis by settings.scheduledResumeAtMillisFlow.collectAsStateWithLifecycle()
    val resumedAfterTimedPause by settings.resumedAfterTimedPauseFlow.collectAsStateWithLifecycle()
    var showPauseDurationDialog by rememberSaveable { mutableStateOf(false) }
    val isRunning = active?.tourId == tourId
    val locale = currentLocale()
    val fallbackTitle = stringResource(R.string.detail_fallback_title)
    val tourName = tour?.displayTitle(fallbackTitle) ?: fallbackTitle
    val kmText = stringResource(R.string.tour_track_length, formatTrackKm(lengthMeters, locale))

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = { onOpenTour(tourId) })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when {
                            isRunning && resumedAfterTimedPause -> R.string.tours_track_resumed_after_timed_pause
                            isRunning && resumedAfterBoot -> R.string.tours_track_resumed_after_boot
                            isRunning -> R.string.tours_track_recording
                            pausedByReboot -> R.string.tours_track_paused_by_reboot
                            else -> R.string.tours_track_paused
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row {
                    Text(
                        tourName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(" · $kmText", style = MaterialTheme.typography.bodySmall)
                }
                val scheduledResumeAt = scheduledResumeAtMillis
                if (!isRunning && scheduledResumeAt != null) {
                    Text(
                        stringResource(R.string.tour_track_pause_scheduled, formatTimeOfDay(scheduledResumeAt, locale)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (isRunning) {
                IconButton(onClick = { showPauseDurationDialog = true }) {
                    Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.tour_track_pause))
                }
            } else {
                IconButton(onClick = { TrackRecordingService.startForTour(context, tourId) }) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = stringResource(R.string.tour_track_resume))
                }
            }
            IconButton(onClick = { TrackRecordingService.stop(context) }) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.tour_track_stop))
            }
        }
    }

    if (showPauseDurationDialog) {
        PauseDurationDialog(
            onSelect = { minutes ->
                showPauseDurationDialog = false
                TrackRecordingService.pause(context)
                if (minutes > 0) TrackRecordingService.scheduleAutoResume(context, System.currentTimeMillis() + minutes * 60_000L)
            },
            onDismiss = { showPauseDurationDialog = false },
        )
    }
}
