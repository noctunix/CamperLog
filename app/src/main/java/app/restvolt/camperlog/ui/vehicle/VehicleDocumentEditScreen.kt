package app.restvolt.camperlog.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LeaveDraftDialog
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.attachments.PhotoAttachmentsSection
import app.restvolt.camperlog.ui.labelRes

/**
 * Formular zum Anlegen und Bearbeiten eines Fahrzeugdokuments; jede Eingabe speichert sich implizit
 * selbst. [onDone] ist der einzige Ausstiegspunkt, für beide Fälle - siehe [VehicleDocumentEditViewModel].
 */
@Composable
fun VehicleDocumentEditScreen(
    viewModel: VehicleDocumentEditViewModel,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.document_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.needsLeaveConfirmation()) showLeaveDialog = true else onDone() }
    BackHandler(onBack = requestBack)

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.document_edit_title_new else R.string.document_edit_title_existing),
                onBack = requestBack,
                actions = {
                    IconButton(onClick = onDone) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
        bottomBar = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.document_not_found), Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard {
                    KindField(state.input.kind) { kind -> viewModel.onInputChange { it.copy(kind = kind) } }
                    OutlinedTextField(
                        value = state.input.title,
                        onValueChange = { value -> viewModel.onInputChange { it.copy(title = value) } },
                        label = { Text(stringResource(R.string.field_document_title)) },
                        isError = state.titleError,
                        supportingText = if (state.titleError) { { Text(stringResource(R.string.edit_required)) } } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(
                        stringResource(R.string.field_document_expiry),
                        state.input.expiryDate,
                        error = null,
                        onDateSelected = { date -> viewModel.onInputChange { it.copy(expiryDate = date) } },
                    )
                }
                PhotoAttachmentsSection(
                    ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
                    ownerId = state.documentId,
                    repository = attachments,
                    fileStore = attachmentFileStore,
                    snackbarHostState = snackbar,
                    allowDocuments = true,
                    pickers = attachmentPickers,
                )
            }
        }
    }

    if (showLeaveDialog) {
        LeaveDraftDialog(
            onCancel = { showLeaveDialog = false },
            onDiscard = {
                showLeaveDialog = false
                viewModel.onDiscardConfirmed()
                onDone()
            },
            onKeep = {
                showLeaveDialog = false
                onDone()
            },
        )
    }
}

@Composable
private fun KindField(selected: DocumentKind, onSelect: (DocumentKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentKind.entries.forEach { kind ->
            FilterChip(selected = kind == selected, onClick = { onSelect(kind) }, label = { Text(stringResource(kind.labelRes)) })
        }
    }
}
