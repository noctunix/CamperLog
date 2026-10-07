package app.restvolt.camperlog.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard

/** Liste der Checklisten-Vorlagen; leer zunächst mit Hinweis und Vorschlags-Button, sonst mit Formular je Zeile. */
@Composable
fun ChecklistTemplatesScreen(
    viewModel: ChecklistTemplatesViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var overflowExpanded by remember { mutableStateOf(false) }
    val addSuggested = { viewModel.addSuggestedTemplates(suggestedChecklistTemplates(resources)) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.checklist_templates_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.checklist_add_suggested_templates)) },
                            onClick = {
                                overflowExpanded = false
                                addSuggested()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.checklist_template_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.templates.isEmpty() -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyHint(stringResource(R.string.checklist_templates_empty))
                TextButton(onClick = addSuggested) {
                    Text(stringResource(R.string.checklist_add_suggested_templates))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.templates, key = ChecklistTemplate::id) { template ->
                    TemplateRow(template, onClick = { onEdit(template.id) })
                }
            }
        }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is ChecklistTemplateMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.checklist_template_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteTemplate(current.template)
            }
            is ChecklistTemplateMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun TemplateRow(template: ChecklistTemplate, onClick: () -> Unit) {
    val editLabel = stringResource(R.string.checklist_template_edit_action)
    SectionCard(modifier = Modifier.clickable(onClickLabel = editLabel, onClick = onClick)) {
        Text(template.name, style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(R.plurals.checklist_template_item_count, template.items.size, template.items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
