package app.restvolt.camperlog.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AndroidLevelAudioPlayer
import app.restvolt.camperlog.domain.LevelAudioCue
import app.restvolt.camperlog.domain.LevelAudioFeedback
import app.restvolt.camperlog.domain.LevelAudioScheme
import app.restvolt.camperlog.domain.LevelStatus
import app.restvolt.camperlog.domain.Tilt
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.settings.RadioOptionGroup
import app.restvolt.camperlog.ui.settings.SwitchSettingRow
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Toleranz in Grad, bis zu der der Neigungspunkt in der Anzeige noch innerhalb des Zielkreises bleibt. */
private const val DISPLAY_RANGE_DEGREES = 15.0

private val LevelAudioScheme.label: Int
    get() = when (this) {
        LevelAudioScheme.DOMINANT_AXIS -> R.string.level_audio_scheme_dominant_axis
        LevelAudioScheme.QUADRANT -> R.string.level_audio_scheme_quadrant
    }

private val LevelAudioScheme.hint: Int
    get() = when (this) {
        LevelAudioScheme.DOMINANT_AXIS -> R.string.level_audio_scheme_dominant_axis_hint
        LevelAudioScheme.QUADRANT -> R.string.level_audio_scheme_quadrant_hint
    }

/** Abfrageintervall zwischen zwei Blicken auf [LevelViewModel.audioCue], solange der Dauerton läuft. */
internal const val LEVEL_AUDIO_POLL_INTERVAL_MILLIS = 200L

/** Wasserwaage: zeigt die Geräteneigung über [LevelViewModel] und eine feste Hinweiskarte zu Abfluss und Kühlschrank. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(viewModel: LevelViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val audioEnabled by viewModel.audioEnabled.collectAsStateWithLifecycle()
    val audioScheme by viewModel.audioScheme.collectAsStateWithLifecycle()
    val locale = currentLocale()

    val audioPlayer = remember { AndroidLevelAudioPlayer() }
    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }
    LaunchedEffect(audioEnabled) {
        if (!audioEnabled) return@LaunchedEffect
        runLevelAudioLoop({ viewModel.audioCue.value }, audioPlayer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_level), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> Unit
                !state.sensorAvailable -> Text(stringResource(R.string.level_sensor_unavailable), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    val tilt = state.tilt ?: Tilt(0.0, 0.0)
                    LevelIndicator(tilt, state.status, Modifier.fillMaxWidth().aspectRatio(1f))
                    val numberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
                    Text(stringResource(R.string.level_pitch, numberFormat.format(tilt.pitchDegrees)), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.level_roll, numberFormat.format(tilt.rollDegrees)), style = MaterialTheme.typography.bodyLarge)
                    Text(state.status.text(), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = viewModel::calibrate, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.level_calibrate))
                    }
                    SwitchSettingRow(
                        title = stringResource(R.string.level_audio_feedback),
                        supportingText = stringResource(R.string.level_audio_feedback_hint),
                        checked = audioEnabled,
                        onCheckedChange = viewModel::setAudioEnabled,
                    )
                    if (audioEnabled) {
                        RadioOptionGroup(
                            options = LevelAudioScheme.entries,
                            selected = audioScheme,
                            onSelect = viewModel::setAudioScheme,
                            label = { stringResource(it.label) },
                            supportingText = { stringResource(it.hint) },
                        )
                    }
                }
            }
            MeasuringPointsCard()
        }
    }
}

/**
 * Treibt [audioPlayer] anhand des jeweils aktuellen Werts von [currentCue], bis die Coroutine
 * abgebrochen wird. Läuft nicht über eine Compose-Recomposition pro Neigungsänderung, sondern fragt
 * [currentCue] in einer eigenen Schleife ab, damit ein kurzes Sensor-Zittern keinen laufenden Beep
 * oder Dauerton mittendrin unterbricht.
 */
internal suspend fun runLevelAudioLoop(currentCue: () -> LevelAudioCue?, audioPlayer: LevelAudioFeedback) {
    var tonePlaying = false
    try {
        while (currentCoroutineContext().isActive) {
            when (val cue = currentCue()) {
                is LevelAudioCue.Level -> {
                    if (!tonePlaying) {
                        audioPlayer.startContinuousTone()
                        tonePlaying = true
                    }
                    delay(LEVEL_AUDIO_POLL_INTERVAL_MILLIS)
                }
                is LevelAudioCue.Beep -> {
                    if (tonePlaying) {
                        audioPlayer.stopContinuousTone()
                        tonePlaying = false
                    }
                    audioPlayer.playBeep(cue.direction)
                    delay(cue.beepIntervalMillis)
                }
                null -> delay(LEVEL_AUDIO_POLL_INTERVAL_MILLIS)
            }
        }
    } finally {
        if (tonePlaying) audioPlayer.stopContinuousTone()
    }
}

@Composable
private fun LevelStatus?.text(): String = when (this) {
    LevelStatus.LEVEL -> stringResource(R.string.level_status_level)
    LevelStatus.TILTED -> stringResource(R.string.level_status_tilted)
    null -> ""
}

/** Einfaches Kreuz mit verschobenem Punkt für vorne/hinten und links/rechts; bewusst ohne 3D-Grafik. */
@Composable
private fun LevelIndicator(tilt: Tilt, status: LevelStatus?, modifier: Modifier = Modifier) {
    val dotColor = if (status == LevelStatus.LEVEL) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.9f
        drawLine(lineColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 2.dp.toPx())
        drawLine(lineColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 2.dp.toPx())
        drawCircle(lineColor, radius = radius * 0.2f, center = center, style = Stroke(width = 2.dp.toPx()))
        val clampedRoll = tilt.rollDegrees.coerceIn(-DISPLAY_RANGE_DEGREES, DISPLAY_RANGE_DEGREES)
        val clampedPitch = tilt.pitchDegrees.coerceIn(-DISPLAY_RANGE_DEGREES, DISPLAY_RANGE_DEGREES)
        val dotOffset = Offset(
            center.x + (clampedRoll / DISPLAY_RANGE_DEGREES).toFloat() * radius,
            center.y + (clampedPitch / DISPLAY_RANGE_DEGREES).toFloat() * radius,
        )
        drawCircle(dotColor, radius = max(radius * 0.08f, 6.dp.toPx()), center = dotOffset)
    }
}

/** Nicht wegklickbare Hinweiskarte zu den beiden neuralgischen Messpunkten beim Nivellieren. */
@Composable
private fun MeasuringPointsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.level_measuring_points_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.level_measuring_point_drain), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.level_measuring_point_fridge), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
