package app.restvolt.camperlog.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LoadForecast
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadPlan
import app.restvolt.camperlog.domain.LoadTankKind
import app.restvolt.camperlog.domain.LoadTankSettings
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.capacityDlOf
import app.restvolt.camperlog.domain.formatCm
import app.restvolt.camperlog.domain.formatKg
import app.restvolt.camperlog.domain.forecastLoad
import app.restvolt.camperlog.domain.tankSettingsOrDefault
import app.restvolt.camperlog.ui.labelRes
import java.util.Locale
import kotlin.math.roundToInt

private val SIDE_VIEW_HEIGHT = 170.dp
private val MARKER_VISUAL_SIZE = 26.dp
private val MARKER_TOUCH_SIZE = 48.dp
private val WHEEL_RADIUS = 14.dp
private val AXLE_TEXT_WIDTH = 84.dp
private const val LANE_OVERLAP_THRESHOLD_CM = 60
private const val CAB_SHARE_OF_EXTENT = 0.22

/**
 * Seitenansicht des Fahrzeugs mit allen positionierten Gegenständen und Tanks des Profils
 * [profileId]; ohne erfassten Radstand wird nichts gezeichnet, nur die Hinweiszeile dazu in der
 * Ergebniskarte bleibt sichtbar.
 *
 * @param vehicle Fahrzeug, dessen Radstand, Länge und vorderer Überhang den Maßstab bestimmen
 * @param plan vollständiger Beladungsplan des Fahrzeugs
 * @param profileId Profil, dessen Aktivierungen und Füllstände gelten
 * @param forecast Hochrechnung des Profils für die Achslasten und den Schwerpunkt
 * @param locale bestimmt das Zahlenformat der angezeigten Werte
 * @param onItemPositionChange wird beim Ablegen eines gezogenen oder platzierten Gegenstands einmal aufgerufen
 * @param onTankPositionChange wird beim Ablegen eines gezogenen Tanks einmal aufgerufen
 */
@Composable
fun LoadSideView(
    vehicle: Vehicle,
    plan: LoadPlan,
    profileId: Long,
    forecast: LoadForecast,
    locale: Locale,
    onItemPositionChange: (LoadItem, Int) -> Unit,
    onTankPositionChange: (LoadTankSettings, Int) -> Unit,
) {
    val wheelbase = vehicle.wheelbaseCm ?: return
    val extent = remember(wheelbase, vehicle.lengthCm, vehicle.frontOverhangCm) {
        loadSideViewExtent(wheelbase, vehicle.lengthCm, vehicle.frontOverhangCm)
    }
    val usesFallbackExtent = vehicle.lengthCm == null || vehicle.frontOverhangCm == null

    fun isItemActive(item: LoadItem): Boolean =
        plan.itemStates.firstOrNull { it.profileId == profileId && it.itemId == item.id }?.active ?: item.inWeighing

    val positionedItems = plan.items.filter { isItemActive(it) && it.positionCm != null }
    val unpositionedItems = plan.items.filter { it.positionCm == null && isItemActive(it) != it.inWeighing }
    val positionedTanks = LoadTankKind.entries.mapNotNull { kind ->
        kind.capacityDlOf(vehicle) ?: return@mapNotNull null
        val settings = plan.tankSettingsOrDefault(vehicle, kind)
        settings.positionCm?.let { settings }
    }

    var selectedChipItem by remember { mutableStateOf<LoadItem?>(null) }
    var draggingPositions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val displayForecast = if (draggingPositions.isEmpty()) {
        forecast
    } else {
        val draggedPlan = plan.copy(
            items = plan.items.map { item -> draggingPositions["item:${item.id}"]?.let { item.copy(positionCm = it) } ?: item },
            tankSettings = plan.tankSettings.map { settings ->
                draggingPositions["tank:${settings.kind}"]?.let { settings.copy(positionCm = it) } ?: settings
            },
        )
        forecastLoad(vehicle, draggedPlan, profileId)
    }

    val markerPositions = positionedItems.map { item -> "item:${item.id}" to checkNotNull(item.positionCm) } +
        positionedTanks.map { settings -> "tank:${settings.kind}" to checkNotNull(settings.positionCm) }
    val lanes = assignLanes(markerPositions.map { (key, originalCm) -> key to (draggingPositions[key] ?: originalCm) })

    val description = sideViewDescription(displayForecast, markerPositions.size, locale)
    val cgLabel = stringResource(R.string.load_side_view_cg_label)
    val overLabel = stringResource(R.string.load_side_view_axle_over)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(SIDE_VIEW_HEIGHT)
                .semantics { contentDescription = description },
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val scale = remember(extent, widthPx) { LoadSideViewScale(extent, widthPx) }

            fun cmToDp(cm: Int): Dp = with(density) { scale.cmToPx(cm).toDp() }

            val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant
            val cgColor = MaterialTheme.colorScheme.tertiary
            val itemColor = MaterialTheme.colorScheme.secondaryContainer
            val tankColor = MaterialTheme.colorScheme.primaryContainer
            val errorColor = MaterialTheme.colorScheme.error
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedChipItem) {
                        detectTapGestures { offset ->
                            selectedChipItem?.let { item ->
                                onItemPositionChange(item, scale.pxToCm(offset.x))
                                selectedChipItem = null
                            }
                        }
                    },
            ) {
                val bodyTop = size.height * 0.28f
                val bodyBottom = size.height * 0.72f
                val cabTop = size.height * 0.46f
                val cabEndCm = extent.startCm + ((extent.endCm - extent.startCm) * CAB_SHARE_OF_EXTENT).roundToInt()
                val strokeWidth = 3.dp.toPx()

                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(scale.cmToPx(cabEndCm), bodyTop),
                    size = Size(scale.cmToPx(extent.endCm) - scale.cmToPx(cabEndCm), bodyBottom - bodyTop),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(scale.cmToPx(extent.startCm), cabTop),
                    size = Size(scale.cmToPx(cabEndCm) - scale.cmToPx(extent.startCm), bodyBottom - cabTop),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                drawCircle(outlineColor, radius = WHEEL_RADIUS.toPx(), center = Offset(scale.cmToPx(0), bodyBottom))
                drawCircle(outlineColor, radius = WHEEL_RADIUS.toPx(), center = Offset(scale.cmToPx(wheelbase), bodyBottom))

                displayForecast.centerOfGravityCm?.let { cg ->
                    val x = scale.cmToPx(cg.roundToInt())
                    val tipY = bodyTop - 6.dp.toPx()
                    val path = Path().apply {
                        moveTo(x, tipY)
                        lineTo(x - 7.dp.toPx(), tipY - 12.dp.toPx())
                        lineTo(x + 7.dp.toPx(), tipY - 12.dp.toPx())
                        close()
                    }
                    drawPath(path, cgColor)
                }
            }

            displayForecast.centerOfGravityCm?.let { cg ->
                Text(
                    cgLabel,
                    modifier = Modifier
                        .width(AXLE_TEXT_WIDTH)
                        .offset { IntOffset((with(density) { (cmToDp(cg.roundToInt())).toPx() } - with(density) { (AXLE_TEXT_WIDTH / 2).toPx() }).roundToInt(), 0) }
                        .clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    color = cgColor,
                )
            }

            AxleLoadText(
                valueKg = displayForecast.frontAxleKg,
                over = displayForecast.frontAxleCheck?.ok == false,
                overLabel = overLabel,
                locale = locale,
                errorColor = errorColor,
                normalColor = onSurfaceColor,
                xDp = cmToDp(0),
                widthDp = AXLE_TEXT_WIDTH,
            )
            AxleLoadText(
                valueKg = displayForecast.rearAxleKg,
                over = displayForecast.rearAxleCheck?.ok == false,
                overLabel = overLabel,
                locale = locale,
                errorColor = errorColor,
                normalColor = onSurfaceColor,
                xDp = cmToDp(wheelbase),
                widthDp = AXLE_TEXT_WIDTH,
            )

            positionedItems.forEach { item ->
                val key = "item:${item.id}"
                val currentCm = draggingPositions[key] ?: checkNotNull(item.positionCm)
                SideViewDragMarker(
                    xDp = cmToDp(currentCm),
                    laneIndex = lanes[key] ?: 0,
                    label = item.name.take(1).ifBlank { "?" },
                    description = stringResource(R.string.load_side_view_marker_description, item.name, formatCm(currentCm, locale)),
                    backgroundColor = itemColor,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    isDragging = draggingPositions.containsKey(key),
                    onDrag = { totalDeltaPx ->
                        val startPx = scale.cmToPx(checkNotNull(item.positionCm))
                        draggingPositions = draggingPositions + (key to scale.pxToCm(startPx + totalDeltaPx))
                    },
                    onDragEnd = {
                        val finalCm = draggingPositions[key]
                        draggingPositions = draggingPositions - key
                        if (finalCm != null) onItemPositionChange(item, finalCm)
                    },
                )
            }
            positionedTanks.forEach { settings ->
                val key = "tank:${settings.kind}"
                val currentCm = draggingPositions[key] ?: checkNotNull(settings.positionCm)
                SideViewDragMarker(
                    xDp = cmToDp(currentCm),
                    laneIndex = lanes[key] ?: 0,
                    label = stringResource(settings.kind.labelRes).take(1),
                    description = stringResource(
                        R.string.load_side_view_marker_description,
                        stringResource(settings.kind.labelRes),
                        formatCm(currentCm, locale),
                    ),
                    backgroundColor = tankColor,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    isDragging = draggingPositions.containsKey(key),
                    onDrag = { totalDeltaPx ->
                        val startPx = scale.cmToPx(checkNotNull(settings.positionCm))
                        draggingPositions = draggingPositions + (key to scale.pxToCm(startPx + totalDeltaPx))
                    },
                    onDragEnd = {
                        val finalCm = draggingPositions[key]
                        draggingPositions = draggingPositions - key
                        if (finalCm != null) onTankPositionChange(settings, finalCm)
                    },
                )
            }
        }

        if (usesFallbackExtent) {
            Text(
                stringResource(R.string.load_side_view_outline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (unpositionedItems.isNotEmpty()) {
            selectedChipItem?.let {
                Text(
                    stringResource(R.string.load_side_view_placing_hint, it.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unpositionedItems, key = LoadItem::id) { item ->
                    FilterChip(
                        selected = selectedChipItem?.id == item.id,
                        onClick = { selectedChipItem = if (selectedChipItem?.id == item.id) null else item },
                        label = { Text(stringResource(R.string.load_side_view_place_chip, item.name)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AxleLoadText(
    valueKg: Double?,
    over: Boolean,
    overLabel: String,
    locale: Locale,
    errorColor: Color,
    normalColor: Color,
    xDp: Dp,
    widthDp: Dp,
) {
    if (valueKg == null) return
    val density = LocalDensity.current
    val text = formatKg(valueKg.roundToInt(), locale) + if (over) " · $overLabel" else ""
    Text(
        text,
        modifier = Modifier
            .width(widthDp)
            .offset { IntOffset((with(density) { xDp.toPx() } - with(density) { (widthDp / 2).toPx() }).roundToInt(), with(density) { (SIDE_VIEW_HEIGHT - 20.dp).toPx() }.roundToInt()) }
            .clearAndSetSemantics {},
        style = MaterialTheme.typography.labelSmall,
        color = if (over) errorColor else normalColor,
    )
}

/**
 * Eine ziehbare Markierung in der Seitenansicht. [onDrag] erhält die gesamte Verschiebung in px seit
 * Beginn des Ziehens (erst der Aufrufer rastert, sonst gingen kleine Einzelschritte verloren),
 * [onDragEnd] wird genau einmal beim Loslassen aufgerufen.
 */
@Composable
private fun SideViewDragMarker(
    xDp: Dp,
    laneIndex: Int,
    label: String,
    description: String,
    backgroundColor: Color,
    contentColor: Color,
    isDragging: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val laneOffset = (MARKER_VISUAL_SIZE + 4.dp) * laneIndex
    // pointerInput(Unit) startet nicht neu; ohne aktuelle Rückrufe arbeitete das nächste Ziehen mit
    // dem Gegenstand und der Position aus der ersten Komposition.
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (with(density) { xDp.toPx() } - with(density) { (MARKER_TOUCH_SIZE / 2).toPx() }).roundToInt(),
                    y = with(density) { (10.dp + laneOffset).toPx() }.roundToInt(),
                )
            }
            .size(MARKER_TOUCH_SIZE)
            .pointerInput(Unit) {
                var totalDeltaPx = 0f
                detectDragGestures(
                    onDragStart = { totalDeltaPx = 0f },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    totalDeltaPx += dragAmount.x
                    currentOnDrag(totalDeltaPx)
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MARKER_VISUAL_SIZE)
                .background(if (isDragging) backgroundColor.copy(alpha = 0.7f) else backgroundColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

/**
 * Weist jeder Markierung in [positions] (Schlüssel, Position in cm) eine Reihe zu, damit sich
 * horizontal überlappende Markierungen nicht gegenseitig verdecken.
 */
private fun assignLanes(positions: List<Pair<String, Int>>): Map<String, Int> {
    val sorted = positions.sortedBy { it.second }
    val laneLastCm = mutableListOf<Int>()
    val result = mutableMapOf<String, Int>()
    for ((key, cm) in sorted) {
        var lane = laneLastCm.indexOfFirst { cm - it >= LANE_OVERLAP_THRESHOLD_CM }
        if (lane == -1) {
            lane = laneLastCm.size
            laneLastCm.add(cm)
        } else {
            laneLastCm[lane] = cm
        }
        result[key] = lane
    }
    return result
}

@Composable
private fun sideViewDescription(forecast: LoadForecast, placedCount: Int, locale: Locale): String {
    val axleSummary = if (forecast.frontAxleKg != null && forecast.rearAxleKg != null) {
        stringResource(R.string.load_side_view_axle_summary, formatKg(forecast.frontAxleKg.roundToInt(), locale), formatKg(forecast.rearAxleKg.roundToInt(), locale))
    } else {
        stringResource(R.string.load_side_view_no_axle_summary)
    }
    val cgText = forecast.centerOfGravityCm?.let { stringResource(R.string.load_side_view_center_of_gravity, formatCm(it.roundToInt(), locale)) }
    val placedText = pluralStringResource(R.plurals.load_side_view_items_placed, placedCount, placedCount)
    return listOfNotNull(axleSummary, cgText, placedText).joinToString(" ")
}
