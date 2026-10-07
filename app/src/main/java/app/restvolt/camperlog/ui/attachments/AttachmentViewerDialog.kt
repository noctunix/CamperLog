package app.restvolt.camperlog.ui.attachments

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.share.shareAttachment
import kotlinx.coroutines.launch
import java.io.File

/**
 * Vollbild-Betrachter über [photos], beginnend bei [startIndex]: Wischen oder die Pfeile wechseln das
 * Bild, Kneifen zoomt. [stopLocation] blendet "Standort der Station übernehmen" ein, solange das
 * aktuelle Foto keine Koordinaten hat; außerhalb eines Stationskontexts ist er `null`.
 */
@Composable
fun AttachmentViewerDialog(
    photos: List<Attachment>,
    startIndex: Int,
    stopLocation: Pair<Double, Double>?,
    fileFor: (Attachment) -> File,
    onDismiss: () -> Unit,
    onDelete: (Attachment) -> Unit,
    onUseLocation: (Attachment, Double, Double) -> Unit,
    onCaptionChange: (Attachment, String) -> Unit,
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            val pagerState = rememberPagerState(initialPage = startIndex) { photos.size }
            val currentAttachment = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    ViewerTopBar(
                        index = pagerState.currentPage,
                        total = photos.size,
                        onClose = onDismiss,
                        onShare = { context.shareAttachment(fileFor(currentAttachment), currentAttachment.mimeType) },
                        onDelete = { onDelete(currentAttachment) },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        ZoomableImage(file = fileFor(photos[page]))
                    }
                    ViewerFooter(
                        attachment = currentAttachment,
                        stopLocation = stopLocation,
                        onUseLocation = { lat, lon -> onUseLocation(currentAttachment, lat, lon) },
                        onCaptionChange = { caption -> onCaptionChange(currentAttachment, caption) },
                        pagerState = pagerState,
                        total = photos.size,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(index: Int, total: Int, onClose: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = Color.White)
        }
        Row {
            Text(
                stringResource(R.string.attachment_viewer_position, index + 1, total),
                modifier = Modifier.padding(top = 14.dp, end = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.attachment_viewer_share), tint = Color.White)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.attachment_viewer_delete), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    val bitmap by rememberThumbnail(file, maxDimension = 1600)
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX = if (scale <= 1f) 0f else offsetX + pan.x
                    offsetY = if (scale <= 1f) 0f else offsetY + pan.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
        }
    }
}

/** Standortübernahme und Bildunterschrift unter dem Bild; die Pfeile sind die Tastenalternative zum Wischen im Pager. */
@Composable
private fun ViewerFooter(
    attachment: Attachment,
    stopLocation: Pair<Double, Double>?,
    onUseLocation: (Double, Double) -> Unit,
    onCaptionChange: (String) -> Unit,
    pagerState: PagerState,
    total: Int,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                enabled = pagerState.currentPage > 0,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.attachment_viewer_previous), tint = Color.White)
            }
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(total - 1)) } },
                enabled = pagerState.currentPage < total - 1,
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.attachment_viewer_next),
                    tint = Color.White,
                    modifier = Modifier.graphicsLayer(rotationZ = 180f),
                )
            }
        }
        if (stopLocation != null && attachment.latitude == null) {
            TextButton(onClick = { onUseLocation(stopLocation.first, stopLocation.second) }) {
                Text(stringResource(R.string.attachment_use_stop_location))
            }
        }
        var caption by remember(attachment.id) { mutableStateOf(attachment.caption) }
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text(stringResource(R.string.attachment_caption_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state -> if (!state.isFocused && caption != attachment.caption) onCaptionChange(caption) },
        )
    }
}
