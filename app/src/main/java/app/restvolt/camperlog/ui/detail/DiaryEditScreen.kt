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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryField
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes

/**
 * Formular zum Anlegen und Bearbeiten eines Tagebucheintrags. [onDone] verlässt es ohne, [onSaved]
 * nach dem Speichern; [onDelete] löscht einen bestehenden Eintrag und wird mit ihm aufgerufen.
 */
@Composable
fun DiaryEditScreen(
    viewModel: DiaryEditViewModel,
    onDone: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (DiaryEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(resources.getString(R.string.diary_edit_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

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
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
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

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
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

    // Nach einem abgelehnten Speichern zum ersten fehlerhaften Feld springen; der Fokus scrollt es ins Bild.
    LaunchedEffect(state.rejectedSaves) {
        if (state.rejectedSaves > 0) {
            state.errors.keys.minByOrNull(DiaryEntryField::ordinal)?.let { focus.getValue(it).requestFocus() }
        }
    }

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
        }
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_save)) }
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
