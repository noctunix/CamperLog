package app.restvolt.camperlog.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import java.time.LocalDate

/** Schreibgeschützte Ansicht eines Fahrzeugdokuments mit seinen Dateien. */
@Composable
fun VehicleDocumentDetailScreen(
    viewModel: VehicleDocumentDetailViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onBack: () -> Unit,
    onEdit: (VehicleDocument) -> Unit,
    onDelete: (VehicleDocument) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val document = (state as? VehicleDocumentDetailUiState.Loaded)?.document

    Scaffold(
        topBar = {
            BackTopBar(
                title = document?.title.orEmpty().ifBlank { stringResource(R.string.document_edit_title_existing) },
                onBack = onBack,
                actions = {
                    if (document != null) {
                        IconButton(onClick = { onEdit(document) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.document_detail_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.document_detail_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state is VehicleDocumentDetailUiState.Loading -> Unit
            document == null -> EmptyHint(stringResource(R.string.document_not_found), Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(document.kind.iconRes), contentDescription = null)
                        Text(stringResource(document.kind.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                    document.expiryDate?.let { expiry ->
                        val locale = currentLocale()
                        val overdue = expiry.isBefore(LocalDate.now())
                        Text(
                            stringResource(R.string.document_expiry_value, formatDate(expiry, locale)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                PhotoAttachmentsSection(
                    ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
                    ownerId = document.id,
                    repository = attachments,
                    fileStore = attachmentFileStore,
                    snackbarHostState = snackbar,
                    modifier = Modifier.fillMaxWidth(),
                    allowDocuments = true,
                    pickers = attachmentPickers,
                )
            }
        }
    }

    if (confirmDelete && document != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.document_delete_confirm_title)) },
            text = { Text(stringResource(R.string.document_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(document)
                }) { Text(stringResource(R.string.document_detail_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
