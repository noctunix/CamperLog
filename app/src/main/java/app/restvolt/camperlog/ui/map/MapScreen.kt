package app.restvolt.camperlog.ui.map

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LatLon
import app.restvolt.camperlog.domain.MAP_MIN_ZOOM
import app.restvolt.camperlog.domain.MAP_TILE_SIZE
import app.restvolt.camperlog.domain.MapCamera
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.TileCoord
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.fitBounds
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.latToWorldY
import app.restvolt.camperlog.domain.lonToWorldX
import app.restvolt.camperlog.domain.panCamera
import app.restvolt.camperlog.domain.screenPosition
import app.restvolt.camperlog.domain.stationsForMap
import app.restvolt.camperlog.domain.toLatLon
import app.restvolt.camperlog.domain.trackSegmentsForMap
import app.restvolt.camperlog.domain.visibleTiles
import app.restvolt.camperlog.domain.zoomCamera
import app.restvolt.camperlog.share.tryStart
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Locale

/** Sichtbarer Rand beim Einpassen aller Stationen. */
private val FIT_BOUNDS_PADDING = 48.dp

/** Fester Markenton für Marker und Linie, unabhängig vom Theme (OSM-Kacheln bleiben im Dunkelmodus hell). */
private val MapMarkerColor = Color(0xFF004A86)
private val MapTrackColor = Color(0xFFD84315)

/** Trackpunkte, die auf dem Bildschirm näher als dies am Vorgänger liegen, werden nicht gezeichnet. */
private const val TRACK_MIN_STEP_PX = 2f

/**
 * Eigene Compose-Slippy-Map einer Tour oder des Stationen-Reiters: Kachelraster mit
 * Ziehen/Kneifzoom/Doppeltipp, Zoomtasten, Einpassen, Markern und gestrichelten Verbindungen in
 * chronologischer Reihenfolge, sowie die Stationsliste im Bottom Sheet als vollwertige
 * barrierefreie Alternative. Ein aufgezeichneter [track] erscheint je Segment als durchgezogene
 * Linie; `null` heißt „lädt noch“, das erste Einpassen wartet dann darauf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    stations: List<Station>,
    title: String,
    viewModel: MapViewModel,
    onBack: () -> Unit,
    onOpenStation: (Long) -> Unit,
    track: List<TrackPoint>? = emptyList(),
) {
    val located = remember(stations) { stationsForMap(stations) }
    val trackSegments = remember(track) { trackSegmentsForMap(track.orEmpty()) }
    val fitPoints = remember(located, trackSegments) { located.mapNotNull { it.toLatLon() } + trackSegments.flatten() }
    val unlocated = remember(stations) { stations.filter { it.latitude == null || it.longitude == null } }
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val showFailureBanner by viewModel.showFailureBanner.collectAsStateWithLifecycle()
    var selectedStationId by remember { mutableStateOf<Long?>(null) }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    val density = LocalDensity.current
    val sheetState = rememberBottomSheetScaffoldState()
    val locale = currentLocale()

    val recenter: (Station) -> Unit = { station ->
        selectedStationId = station.id
        station.toLatLon()?.let { point -> camera = camera?.copy(center = point) }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        topBar = { BackTopBar(title = stringResource(R.string.map_title, title), onBack = onBack) },
        sheetPeekHeight = 112.dp,
        sheetContent = {
            StationListSheet(
                located = located,
                unlocated = unlocated,
                locale = locale,
                onSelectLocated = recenter,
                onOpenStation = onOpenStation,
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val viewportWidthPx = with(density) { maxWidth.roundToPx() }
            val viewportHeightPx = with(density) { maxHeight.roundToPx() }
            val paddingPx = with(density) { FIT_BOUNDS_PADDING.roundToPx() }

            LaunchedEffect(viewportWidthPx, viewportHeightPx, fitPoints, track == null) {
                if (camera == null && track != null && viewportWidthPx > 0 && viewportHeightPx > 0) {
                    camera = if (fitPoints.isEmpty()) {
                        MapCamera(LatLon(0.0, 0.0), MAP_MIN_ZOOM.toDouble())
                    } else {
                        fitBounds(fitPoints, viewportWidthPx, viewportHeightPx, paddingPx)
                    }
                }
            }

            val currentCamera = camera
            if (currentCamera != null && viewportWidthPx > 0 && viewportHeightPx > 0) {
                LaunchedEffect(currentCamera, viewportWidthPx, viewportHeightPx) {
                    viewModel.setVisibleTiles(visibleTiles(currentCamera, viewportWidthPx, viewportHeightPx).toSet())
                }

                MapCanvas(
                    camera = currentCamera,
                    onCameraChange = { camera = it },
                    tiles = tiles,
                    located = located,
                    trackSegments = trackSegments,
                    selectedStationId = selectedStationId,
                    onMarkerTap = { id -> selectedStationId = id },
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    totalStopCount = stations.size,
                    locale = locale,
                )

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { camera = zoomCamera(currentCamera, 1.0) }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.map_zoom_in))
                    }
                    FilledTonalIconButton(onClick = { camera = zoomCamera(currentCamera, -1.0) }) {
                        Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.map_zoom_out))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (fitPoints.isNotEmpty()) camera = fitBounds(fitPoints, viewportWidthPx, viewportHeightPx, paddingPx)
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_fit_screen), contentDescription = stringResource(R.string.map_fit_bounds))
                    }
                }

                if (showFailureBanner) {
                    TileFailureBanner(
                        onRetry = viewModel::retryFailedTiles,
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    )
                }

                AttributionChip(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))

                val selectedStation = located.firstOrNull { it.id == selectedStationId }
                if (selectedStation != null) {
                    SelectedStationCard(
                        station = selectedStation,
                        locale = locale,
                        onDetails = { onOpenStation(selectedStation.id) },
                        onClose = { selectedStationId = null },
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                }
            }
        }
    }
}

/** Kachelraster, Track, gestrichelte Verbindungslinie und Marker, mit Ziehen/Kneifzoom/Doppeltipp. */
@Composable
private fun MapCanvas(
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    tiles: Map<TileCoord, TileState>,
    located: List<Station>,
    trackSegments: List<List<LatLon>>,
    selectedStationId: Long?,
    onMarkerTap: (Long) -> Unit,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    totalStopCount: Int,
    locale: Locale,
) {
    // Beim Verschieben ändert sich die Kachelmenge ständig; ohne diesen Zwischenspeicher würden bei
    // jeder Änderung alle sichtbaren PNGs erneut auf dem UI-Thread dekodiert.
    val decoded = remember { HashMap<TileCoord, Pair<ByteArray, ImageBitmap>>() }
    val bitmaps = remember(tiles) {
        decoded.keys.retainAll(tiles.keys)
        buildMap {
            tiles.forEach { (coord, state) ->
                val loaded = state as? TileState.Loaded ?: return@forEach
                val image = decoded[coord]?.takeIf { it.first === loaded.pngBytes }?.second
                    ?: decodeTile(loaded.pngBytes)?.also { decoded[coord] = loaded.pngBytes to it }
                if (image != null) put(coord, image)
            }
        }
    }
    // pointerInput(Unit) läuft über die gesamte Lebensdauer des Canvas weiter (kein Neustart der
    // Gestenerkennung bei jedem Kamerawechsel); die Callbacks brauchen daher den aktuellen Wert
    // über rememberUpdatedState statt über die zum Startzeitpunkt eingefangene Kamera.
    val liveCamera by rememberUpdatedState(camera)
    val canvasDescription = pluralStringResource(R.plurals.map_canvas_description, totalStopCount, located.size, totalStopCount)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val panned = panCamera(liveCamera, pan.x.toDouble(), pan.y.toDouble())
                    onCameraChange(zoomCamera(panned, ln(zoom.toDouble()) / ln(2.0)))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onCameraChange(zoomCamera(liveCamera, 1.0)) })
            }
            .semantics { contentDescription = canvasDescription },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cameraWorldX = lonToWorldX(camera.center.longitude, camera.zoom)
            val cameraWorldY = latToWorldY(camera.center.latitude, camera.zoom)
            for ((coord, bitmap) in bitmaps) {
                val scale = 2.0.pow(camera.zoom - coord.zoom)
                val sizePx = (MAP_TILE_SIZE * scale).roundToInt()
                val left = viewportWidthPx / 2.0 + (coord.x * MAP_TILE_SIZE * scale - cameraWorldX)
                val top = viewportHeightPx / 2.0 + (coord.y * MAP_TILE_SIZE * scale - cameraWorldY)
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(sizePx, sizePx),
                )
            }

            val trackWidth = 3.dp.toPx()
            for (segment in trackSegments) {
                val path = trackPath(segment, camera, viewportWidthPx, viewportHeightPx) ?: continue
                drawPath(path, color = Color.White, style = Stroke(width = trackWidth + 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color = MapTrackColor, style = Stroke(width = trackWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val points = located.mapNotNull { station -> station.toLatLon()?.let { screenPosition(it, camera, viewportWidthPx, viewportHeightPx) } }
            for (i in 0 until points.size - 1) {
                val (x0, y0) = points[i]
                val (x1, y1) = points[i + 1]
                val start = Offset(x0.toFloat(), y0.toFloat())
                val end = Offset(x1.toFloat(), y1.toFloat())
                drawLine(color = Color.White, start = start, end = end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    color = MapMarkerColor,
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
            }
        }

        located.forEachIndexed { index, station ->
            val point = station.toLatLon() ?: return@forEachIndexed
            val (screenX, screenY) = screenPosition(point, camera, viewportWidthPx, viewportHeightPx)
            val selected = station.id == selectedStationId
            val visualSize = if (selected) 40.dp else 32.dp
            val detailsLabel = stringResource(R.string.map_details_button)
            val typeLabel = stringResource(station.type.labelRes)
            val description = stringResource(
                R.string.map_marker_content_description,
                index + 1,
                located.size,
                typeLabel,
                station.name.ifBlank { typeLabel },
                formatDate(station.date, locale),
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset((screenX - 24.dp.toPx()).roundToInt(), (screenY - 24.dp.toPx()).roundToInt()) }
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(visualSize)
                        .clip(CircleShape)
                        .background(MapMarkerColor)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(onClickLabel = detailsLabel) { onMarkerTap(station.id) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(station.type.iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun decodeTile(pngBytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)?.asImageBitmap()

/** "© OpenStreetMap contributors", immer sichtbar und nicht von anderen Elementen verdeckt. */
@Composable
private fun AttributionChip(modifier: Modifier = Modifier) {
    val uri = stringResource(R.string.about_osm_copyright_uri)
    val context = LocalContext.current
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.small) {
        TextButton(onClick = { context.tryStart(Intent(Intent.ACTION_VIEW, uri.toUri())) }) {
            Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TileFailureBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.map_tile_failure_banner),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
        }
    }
}

@Composable
private fun SelectedStationCard(station: Station, locale: Locale, onDetails: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(station.type.iconRes), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name.ifBlank { stringResource(station.type.labelRes) }, style = MaterialTheme.typography.titleSmall)
                Text(formatDate(station.date, locale), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDetails) { Text(stringResource(R.string.map_details_button)) }
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

/** Barrierefreie Alternative zum Kartencanvas: vollständige Stationsliste im Bottom Sheet. */
@Composable
private fun StationListSheet(
    located: List<Station>,
    unlocated: List<Station>,
    locale: Locale,
    onSelectLocated: (Station) -> Unit,
    onOpenStation: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val summary = buildString {
            append(pluralStringResource(R.plurals.map_stops_located, located.size, located.size))
            if (unlocated.isNotEmpty()) {
                append(" · ")
                append(pluralStringResource(R.plurals.map_stops_unlocated, unlocated.size, unlocated.size))
            }
        }
        Text(summary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
        val detailsLabel = stringResource(R.string.map_details_button)
        LazyColumn(Modifier.height(320.dp)) {
            items(located, key = Station::id) { station ->
                ListItem(
                    headlineContent = { Text(station.name.ifBlank { stringResource(station.type.labelRes) }) },
                    supportingContent = { Text(formatDate(station.date, locale)) },
                    leadingContent = { Icon(painterResource(station.type.iconRes), contentDescription = null) },
                    modifier = Modifier.clickable(onClickLabel = detailsLabel) { onSelectLocated(station) },
                )
            }
            items(unlocated, key = Station::id) { station ->
                ListItem(
                    headlineContent = { Text(station.name.ifBlank { stringResource(station.type.labelRes) }) },
                    supportingContent = { Text("${formatDate(station.date, locale)} · ${stringResource(R.string.map_unlocated_hint)}") },
                    leadingContent = { Icon(painterResource(station.type.iconRes), contentDescription = null) },
                    modifier = Modifier.clickable { onOpenStation(station.id) },
                )
            }
        }
    }
}

/**
 * Bildschirmpfad eines Tracksegments; Punkte dichter als [TRACK_MIN_STEP_PX] am zuletzt
 * gezeichneten werden übersprungen, der letzte Punkt immer übernommen. `null` bei weniger als
 * zwei Punkten.
 */
private fun trackPath(segment: List<LatLon>, camera: MapCamera, viewportWidthPx: Int, viewportHeightPx: Int): Path? {
    if (segment.size < 2) return null
    val path = Path()
    var lastX = 0f
    var lastY = 0f
    segment.forEachIndexed { index, point ->
        val (x, y) = screenPosition(point, camera, viewportWidthPx, viewportHeightPx)
        val fx = x.toFloat()
        val fy = y.toFloat()
        when {
            index == 0 -> path.moveTo(fx, fy)
            index == segment.lastIndex || abs(fx - lastX) + abs(fy - lastY) >= TRACK_MIN_STEP_PX -> path.lineTo(fx, fy)
            else -> return@forEachIndexed
        }
        lastX = fx
        lastY = fy
    }
    return path
}
