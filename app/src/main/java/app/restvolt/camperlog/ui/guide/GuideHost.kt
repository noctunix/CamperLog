package app.restvolt.camperlog.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.guide.GuideController
import app.restvolt.camperlog.domain.guide.GuidePhase
import app.restvolt.camperlog.domain.guide.GuideState
import app.restvolt.camperlog.domain.guide.TourStep

private val ANCHOR_HOLE_PADDING = 8.dp
private val ANCHOR_HOLE_CORNER_RADIUS = 12.dp

/**
 * Sitzt als Geschwister über dem App-Inhalt und führt eine über [controller] laufende Tour:
 * abgedunkelte Barriere mit Loch am aktuellen Anker, Erklärkarte mit Fortschritt und Bedienung.
 * Zeigt nichts, solange keine Tour läuft oder sie beendet ist.
 */
@Composable
fun GuideHost(controller: GuideController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    val step = state.currentStep
    if (step == null || state.phase == GuidePhase.IDLE || state.phase == GuidePhase.ENDED) return

    val anchors by LocalGuideAnchors.current.anchors.collectAsStateWithLifecycle()
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInWindow() },
    ) {
        val anchorRect = step.anchorId?.let { anchors[it] }?.translate(-hostOffset)
        val cardAboveAnchor = anchorRect != null && with(density) { anchorRect.center.y.toDp() } > maxHeight / 2

        if (state.phase != GuidePhase.PAUSED) {
            GuideBarrier(hasAnchor = step.anchorId != null, anchorRect = anchorRect, modifier = Modifier.fillMaxSize())
        }

        GuideCard(
            state = state,
            step = step,
            onNext = controller::next,
            onBack = controller::back,
            onPause = controller::pause,
            onResume = controller::resume,
            onEnd = controller::end,
            modifier = Modifier
                .align(
                    when {
                        step.anchorId == null -> Alignment.Center
                        cardAboveAnchor -> Alignment.TopCenter
                        else -> Alignment.BottomCenter
                    },
                )
                .padding(16.dp)
                .safeDrawingPadding()
                .imePadding(),
        )
    }
}

/**
 * Abgedunkelte Barriere mit einem Loch an [anchorRect]. Ohne Anker ([hasAnchor] `false`) dimmt sie
 * nur leicht, ohne Zeigereingaben abzufangen: Ein reiner Info-Schritt schützt keine konkrete
 * Bedienstelle, daher muss nichts blockiert werden. Mit Anker, aber noch unbekannter Position
 * ([anchorRect] `null`, z. B. kurz nach dem Schrittwechsel), blockiert sie vorsichtshalber den
 * ganzen Bildschirm statt ein Loch an einer falschen Stelle zu zeigen.
 *
 * Die Zeigereingaben blockiert ein Rahmen aus bis zu vier Streifen rund um das Loch statt eines
 * einzelnen bildschirmgroßen `pointerInput`: Compose lässt Zeigereingaben sonst nicht zu einem
 * dahinterliegenden Geschwister durch, selbst wenn dieses Element sie gar nicht konsumiert – das
 * Loch braucht also eine Fläche, auf der schlicht kein eigenes Pointer-Input-Element liegt.
 */
@Composable
private fun GuideBarrier(hasAnchor: Boolean, anchorRect: Rect?, modifier: Modifier = Modifier) {
    val scrimColor = Color.Black.copy(alpha = if (hasAnchor) 0.6f else 0.3f)
    val density = LocalDensity.current
    val holePaddingPx = with(density) { ANCHOR_HOLE_PADDING.toPx() }
    val holeCornerRadiusPx = with(density) { ANCHOR_HOLE_CORNER_RADIUS.toPx() }
    val hole = if (hasAnchor && anchorRect != null) anchorRect.inflate(holePaddingPx) else null

    BoxWithConstraints(modifier) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (hole != null) {
                    val holePath = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(holeCornerRadiusPx))) }
                    clipPath(holePath, ClipOp.Difference) { drawRect(scrimColor) }
                } else {
                    drawRect(scrimColor)
                }
            },
        )

        if (hasAnchor) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            if (hole == null) {
                Box(Modifier.fillMaxSize().consumeAllPointerInput())
            } else {
                val left = hole.left.coerceIn(0f, maxWidthPx)
                val top = hole.top.coerceIn(0f, maxHeightPx)
                val right = hole.right.coerceIn(0f, maxWidthPx)
                val bottom = hole.bottom.coerceIn(0f, maxHeightPx)
                with(density) {
                    if (top > 0f) {
                        Box(Modifier.offset(y = 0.dp).size(maxWidth, top.toDp()).consumeAllPointerInput())
                    }
                    if (bottom < maxHeightPx) {
                        Box(Modifier.offset(y = bottom.toDp()).size(maxWidth, (maxHeightPx - bottom).toDp()).consumeAllPointerInput())
                    }
                    if (left > 0f) {
                        Box(Modifier.offset(y = top.toDp()).size(left.toDp(), (bottom - top).toDp()).consumeAllPointerInput())
                    }
                    if (right < maxWidthPx) {
                        Box(Modifier.offset(x = right.toDp(), y = top.toDp()).size((maxWidthPx - right).toDp(), (bottom - top).toDp()).consumeAllPointerInput())
                    }
                }
            }
        }
    }
}

private fun Modifier.consumeAllPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun GuideCard(
    state: GuideState,
    step: TourStep,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (state.phase == GuidePhase.PAUSED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.guide_end))
                    }
                    Button(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.guide_resume))
                    }
                }
            } else {
                Text(text = stringResource(step.textRes), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.guide_step_counter, state.stepIndex + 1, state.stepCount),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.stepIndex > 0) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.guide_back))
                        }
                    }
                    TextButton(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.guide_pause))
                    }
                    TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.guide_end))
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.phase == GuidePhase.ACTIVE,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        val isLastStep = state.stepIndex == state.stepCount - 1
                        Text(stringResource(if (isLastStep) R.string.guide_finish else R.string.guide_next))
                    }
                }
            }
        }
    }
}
