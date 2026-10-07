package app.restvolt.camperlog.ui.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateField
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.DiscardChangesDialog
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard

/**
 * Formular zum Anlegen und Bearbeiten einer Checklisten-Vorlage. [onDone] verlässt es ohne, [onSaved]
 * nach dem Speichern; [onDelete] löscht eine bestehende Vorlage und wird mit ihr aufgerufen.
 */
@Composable
fun ChecklistTemplateEditScreen(
    viewModel: ChecklistTemplateEditViewModel,
    onDone: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (ChecklistTemplate) -> Unit,
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
            snackbar.showSnackbar(resources.getString(R.string.checklist_template_save_failed), withDismissAction = true)
            viewModel.onSaveFailureShown()
        }
    }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = state.isDirty && !state.isSaved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.checklist_template_title_new else R.string.checklist_template_title_existing),
                onBack = requestBack,
                actions = {
                    if (!state.notFound && !state.isLoading) {
                        state.original?.let { template ->
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.checklist_template_delete_action)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onDelete(template)
                                    },
                                )
                            }
                        }
                        TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.checklist_template_not_found), Modifier.padding(padding))
            else -> TemplateForm(
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
private fun TemplateForm(state: ChecklistTemplateEditUiState, viewModel: ChecklistTemplateEditViewModel, modifier: Modifier) {
    val input = state.input
    val required = stringResource(R.string.edit_required)
    val nameError = state.errors[ChecklistTemplateField.NAME]?.let { stringResource(R.string.checklist_template_error_name_required) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            OutlinedTextField(
                value = input.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_name)) },
                isError = nameError != null,
                supportingText = { Text(nameError ?: required) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        SectionCard {
            Text(stringResource(R.string.checklist_template_items_title), style = MaterialTheme.typography.titleSmall)
            input.items.forEachIndexed { index, text ->
                ItemRow(
                    text = text,
                    canMoveUp = index > 0,
                    canMoveDown = index < input.items.lastIndex,
                    onTextChange = { viewModel.onItemChange(index, it) },
                    onMoveUp = { viewModel.onMoveItemUp(index) },
                    onMoveDown = { viewModel.onMoveItemDown(index) },
                    onRemove = { viewModel.onRemoveItem(index) },
                )
            }
            TextButton(onClick = viewModel::onAddItem) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.checklist_template_add_item), Modifier.padding(start = 8.dp))
            }
        }
        Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
        if (state.errors.isNotEmpty()) {
            val fields = state.errors.keys.map { stringResource(R.string.field_name) }
            Text(
                stringResource(R.string.edit_check_fields, fields.joinToString(", ")),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ItemRow(
    text: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val moveUpLabel = stringResource(R.string.checklist_template_item_move_up)
    val moveDownLabel = stringResource(R.string.checklist_template_item_move_down)
    val removeLabel = stringResource(R.string.checklist_template_item_remove)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = moveUpLabel)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = moveDownLabel)
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = removeLabel)
        }
    }
}
