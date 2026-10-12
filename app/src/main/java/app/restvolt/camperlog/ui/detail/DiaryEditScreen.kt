package app.restvolt.camperlog.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryField
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LeaveDraftDialog
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.TimeField
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes

/**
 * Formular zum Anlegen und Bearbeiten eines Tagebucheintrags; jede Eingabe speichert sich implizit
 * selbst. [onDone] ist der einzige Ausstiegspunkt, für beide Fälle - siehe [DiaryEditViewModel].
 * [onDelete] löscht einen bestehenden Eintrag über die Kopfzeile und wird mit ihm aufgerufen.
 */
@Composable
fun DiaryEditScreen(
    viewModel: DiaryEditViewModel,
    onDone: () -> Unit,
    onDelete: (DiaryEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.diary_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = {
        if (state.needsLeaveConfirmation()) showLeaveDialog = true else onDone()
    }
    BackHandler(onBack = requestBack)

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.diary_edit_title_new else R.string.diary_edit_title_existing),
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        state.original?.let { entry ->
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.diary_entry_delete_action)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onDelete(entry)
                                    },
                                )
                            }
                        }
                    }
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
            state.notFound -> EmptyHint(stringResource(R.string.diary_not_found), Modifier.padding(padding))
            else -> DiaryForm(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding(),
            )
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
private fun DiaryForm(state: DiaryEditUiState, viewModel: DiaryEditViewModel, modifier: Modifier) {
    val input = state.input
    val change = viewModel::onInputChange
    val required = stringResource(R.string.edit_required)
    val focus = remember { DiaryEntryField.entries.associateWith { FocusRequester() } }
    fun focusOf(field: DiaryEntryField) = Modifier.focusRequester(focus.getValue(field))
    @Composable fun errorOf(field: DiaryEntryField): String? = state.errors[field]?.messageRes(field)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            DateField(
                stringResource(R.string.field_date),
                input.date,
                errorOf(DiaryEntryField.DATE),
                { date -> change { it.copy(date = date) } },
                modifier = focusOf(DiaryEntryField.DATE),
            )
            TimeField(stringResource(R.string.field_time), input.time) { time -> change { it.copy(time = time) } }
            OutlinedTextField(
                value = input.text,
                onValueChange = { value -> change { it.copy(text = value) } },
                modifier = focusOf(DiaryEntryField.TEXT).fillMaxWidth(),
                label = { Text(stringResource(R.string.diary_field_text)) },
                isError = errorOf(DiaryEntryField.TEXT) != null,
                supportingText = { Text(errorOf(DiaryEntryField.TEXT) ?: required) },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = input.weather,
                onValueChange = { value -> change { it.copy(weather = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.diary_field_weather)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = input.odometerKm,
                onValueChange = { value -> change { it.copy(odometerKm = value) } },
                modifier = focusOf(DiaryEntryField.ODOMETER_KM).fillMaxWidth(),
                label = { Text(stringResource(R.string.field_odometer_km)) },
                trailingIcon = { Text("km", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                isError = errorOf(DiaryEntryField.ODOMETER_KM) != null,
                supportingText = errorOf(DiaryEntryField.ODOMETER_KM)?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
            )
        }
        if (state.errors.isNotEmpty()) {
            // Nennt die betroffenen Felder; ändert sich die Liste, sagt TalkBack sie erneut an.
            val fields = state.errors.keys.sortedBy(DiaryEntryField::ordinal).map { stringResource(it.labelRes) }
            Text(
                stringResource(R.string.edit_check_fields, fields.joinToString(", ")),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
