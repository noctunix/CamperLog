package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import app.restvolt.camperlog.tracking.PAUSE_RESUME_DURATIONS_MINUTES
import app.restvolt.camperlog.tracking.pauseResumeDurationLabel
import app.restvolt.camperlog.ui.settings.IntChoiceDialog

/**
 * Dialog zur Dauer, nach der eine pausierte Trackaufzeichnung automatisch fortgesetzt wird; [onSelect]
 * liefert die gewählte Dauer in Minuten (`0` = ohne automatisches Fortsetzen). Geteilt zwischen
 * Tourdetail und der Aufzeichnungsleiste, da beide einen Pausieren-Button haben.
 */
@Composable
internal fun PauseDurationDialog(onSelect: (minutes: Int) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    IntChoiceDialog(
        title = stringResource(R.string.tour_track_pause_duration_title),
        options = PAUSE_RESUME_DURATIONS_MINUTES,
        selected = 0,
        optionLabel = { minutes -> pauseResumeDurationLabel(resources, minutes) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
