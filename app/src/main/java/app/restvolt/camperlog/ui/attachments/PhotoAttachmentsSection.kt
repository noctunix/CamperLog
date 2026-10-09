package app.restvolt.camperlog.ui.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.share.openAttachment
import androidx.compose.foundation.Image
import java.io.File

/** Ob [this] sich als Bild im Vollbild-Betrachter anzeigen lässt statt nur extern geöffnet zu werden. */
private val Attachment.isImage: Boolean get() = mimeType.startsWith("image/")

/**
 * Streifen aus Foto-Vorschaubildern mit Hinzufügen-Knopf (Kamera/Galerie, bei [allowDocuments] auch
 * Dokument), Tippen öffnet den Vollbild-Betrachter. [ownerId] `0` bedeutet ein noch nicht gespeicherter
 * Eintrag: Ohne [pending] zeigt der Streifen dann nur einen Hinweis, Fotos anzuhängen ist erst nach dem
 * ersten Speichern möglich; mit [pending] können Fotos schon jetzt aufgenommen werden und hängen erst
 * nach dem ersten Speichern an der dann bekannten id. [stopLocation] sind die Koordinaten der Station,
 * für "Standort der Station übernehmen" im Betrachter; `null` außerhalb eines Stationskontexts oder
 * ohne Koordinaten.
 */
@Composable
fun PhotoAttachmentsSection(
    ownerType: AttachmentOwnerType,
    ownerId: Long,
    repository: AttachmentRepository,
    fileStore: AttachmentFileStore,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    allowDocuments: Boolean = false,
    stopLocation: Pair<Double, Double>? = null,
    pickers: AttachmentPickers = AndroidAttachmentPickers,
    pending: PendingPhotosState? = null,
) {
    val resources = LocalResources.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.attachments_section_title), style = MaterialTheme.typography.titleSmall)
        if (ownerId == 0L) {
            if (pending != null) {
                PendingPhotoStrip(ownerType, pending, fileStore, stopLocation, pickers)
            } else {
                Text(
                    stringResource(R.string.attachments_save_first_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val viewModel = viewModel(key = "attachments_${ownerType}_$ownerId") {
            AttachmentsViewModel(repository, fileStore, ownerType, ownerId)
        }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val imagePhotos = state.photos.filter { it.isImage }
        var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
        val context = LocalContext.current
        val settings = remember { AttachmentSettings(context) }
        var showGalleryHint by remember { mutableStateOf(!settings.galleryLocationHintShown) }

        LaunchedEffect(state.lastDeleted) {
            val deleted = state.lastDeleted ?: return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.attachments_photo_deleted),
                actionLabel = resources.getString(R.string.action_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.deleteHandled()
        }

        val launchCamera = pickers.rememberCameraLauncher(onPicked = viewModel::importPhoto)
        val launchGallery = pickers.rememberGalleryLauncher(onPicked = viewModel::importPhoto)
        val launchDocument = if (allowDocuments) pickers.rememberDocumentLauncher(onPicked = viewModel::importDocument) else null

        var menuExpanded by remember { mutableStateOf(false) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.photos, key = Attachment::id) { attachment ->
                Thumbnail(
                    attachment = attachment,
                    index = state.photos.indexOf(attachment),
                    total = state.photos.size,
                    file = viewModel.file(attachment),
                    onClick = {
                        if (attachment.isImage) {
                            viewerIndex = imagePhotos.indexOf(attachment)
                        } else {
                            context.openAttachment(viewModel.file(attachment), attachment.mimeType)
                        }
                    },
                )
            }
            item {
                Box {
                    AddButton(onClick = { menuExpanded = true })
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                            settings.galleryLocationHintShown = true
                            showGalleryHint = false
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attachments_add_camera)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null) },
                            onClick = { menuExpanded = false; launchCamera() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attachments_add_gallery)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_image), contentDescription = null) },
                            onClick = { menuExpanded = false; launchGallery() },
                        )
                        if (showGalleryHint) {
                            Text(
                                stringResource(R.string.attachments_gallery_no_location_hint),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (launchDocument != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attachments_add_document)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_description), contentDescription = null) },
                                onClick = { menuExpanded = false; launchDocument() },
                            )
                        }
                    }
                }
            }
        }
        if (state.importing) {
            ImportingRow(stringResource(R.string.attachments_importing))
        }
        state.importError?.let { error ->
            Text(
                stringResource(importErrorTextRes(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(4000)
                viewModel.dismissImportError()
            }
        }

        viewerIndex?.let { index ->
            AttachmentViewerDialog(
                photos = imagePhotos,
                startIndex = index.coerceIn(0, imagePhotos.lastIndex.coerceAtLeast(0)),
                stopLocation = stopLocation,
                fileFor = viewModel::file,
                onDismiss = { viewerIndex = null },
                onDelete = { attachment ->
                    viewModel.delete(attachment)
                    viewerIndex = null
                },
                onUseLocation = viewModel::useLocation,
                onCaptionChange = viewModel::updateCaption,
            )
        }
    }
}

/**
 * Dieselbe Streifen-Optik wie der gespeicherte Fall, aber über [pending] statt über ein
 * [AttachmentsViewModel]: Löschen hat kein Rückgängig, weil nichts in der Datenbank steht.
 */
@Composable
private fun PendingPhotoStrip(
    ownerType: AttachmentOwnerType,
    pending: PendingPhotosState,
    fileStore: AttachmentFileStore,
    stopLocation: Pair<Double, Double>?,
    pickers: AttachmentPickers,
) {
    val context = LocalContext.current
    val settings = remember { AttachmentSettings(context) }
    var showGalleryHint by remember { mutableStateOf(!settings.galleryLocationHintShown) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val attachments = pending.photos.mapIndexed { index, photo -> photo.asAttachment(index, ownerType) }
    fun pendingOf(attachment: Attachment) = pending.photos.first { it.fileName == attachment.fileName }

    val launchCamera = pickers.rememberCameraLauncher(onPicked = pending.onAdd)
    val launchGallery = pickers.rememberGalleryLauncher(onPicked = pending.onAdd)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = Attachment::id) { attachment ->
            Thumbnail(
                attachment = attachment,
                index = attachments.indexOf(attachment),
                total = attachments.size,
                file = fileStore.file(attachment.fileName),
                onClick = { viewerIndex = attachments.indexOf(attachment) },
            )
        }
        item {
            Box {
                AddButton(onClick = { menuExpanded = true })
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                        settings.galleryLocationHintShown = true
                        showGalleryHint = false
                    },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachments_add_camera)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null) },
                        onClick = { menuExpanded = false; launchCamera() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachments_add_gallery)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_image), contentDescription = null) },
                        onClick = { menuExpanded = false; launchGallery() },
                    )
                    if (showGalleryHint) {
                        Text(
                            stringResource(R.string.attachments_gallery_no_location_hint),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (pending.importing) {
        ImportingRow(stringResource(R.string.attachments_importing))
    }
    pending.importError?.let { error ->
        Text(
            stringResource(importErrorTextRes(error)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(4000)
            pending.onDismissImportError()
        }
    }

    viewerIndex?.let { index ->
        AttachmentViewerDialog(
            photos = attachments,
            startIndex = index.coerceIn(0, attachments.lastIndex.coerceAtLeast(0)),
            stopLocation = stopLocation,
            fileFor = { attachment -> fileStore.file(attachment.fileName) },
            onDismiss = { viewerIndex = null },
            onDelete = { attachment ->
                pending.onRemove(pendingOf(attachment))
                viewerIndex = null
            },
            onUseLocation = { attachment, latitude, longitude -> pending.onUseLocation(pendingOf(attachment), latitude, longitude) },
            onCaptionChange = { attachment, caption -> pending.onCaptionChange(pendingOf(attachment), caption) },
        )
    }
}

@Composable
private fun ImportingRow(text: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val description = stringResource(R.string.attachments_add_action)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add_a_photo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Thumbnail(attachment: Attachment, index: Int, total: Int, file: File, onClick: () -> Unit) {
    val hasLocation = attachment.latitude != null
    val description = stringResource(
        if (hasLocation) R.string.attachment_thumbnail_description_with_location else R.string.attachment_thumbnail_description,
        index + 1,
        total,
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.isImage) {
            val bitmap by rememberThumbnail(file)
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Icon(painterResource(R.drawable.ic_picture_as_pdf), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasLocation) {
            Icon(
                painterResource(R.drawable.ic_location_on),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(16.dp),
                tint = Color.White,
            )
        }
    }
}

/** Typisierte Fehlermeldung zu einem gescheiterten Import, siehe [AttachmentImportError]. */
internal fun importErrorTextRes(error: AttachmentImportError): Int = when (error) {
    AttachmentImportError.UNREADABLE -> R.string.attachment_import_error_unreadable
    AttachmentImportError.UNSUPPORTED_TYPE -> R.string.attachment_import_error_unsupported
    AttachmentImportError.TOO_LARGE -> R.string.attachment_import_error_too_large
    AttachmentImportError.DECODE_FAILED -> R.string.attachment_import_error_decode_failed
}
