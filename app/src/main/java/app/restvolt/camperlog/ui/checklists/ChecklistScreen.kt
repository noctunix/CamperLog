package app.restvolt.camperlog.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import app.restvolt.camperlog.domain.checkedCount
import app.restvolt.camperlog.domain.isComplete
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint

/**
 * Eine gestartete Checkliste: jedes Häkchen speichert sofort. [onDelete] löscht die Checkliste ohne
 * Rückfrage; die Herkunftsseite bietet dafür „Rückgängig" an.
 */
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    onBack: () -> Unit,
    onDelete: (Checklist) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = state.checklist?.title ?: stringResource(R.string.checklist_title_fallback),
                onBack = onBack,
                actions = {
                    state.checklist?.let { checklist ->
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.checklist_uncheck_all)) },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.uncheckAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.checklist_delete_action)) },
                                onClick = {
                                    overflowExpanded = false
                                    onDelete(checklist)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> EmptyHint(stringResource(R.string.checklist_not_found), Modifier.padding(padding))
            else -> state.checklist?.let { checklist ->
                ChecklistContent(checklist, onToggle = viewModel::toggleItem, modifier = Modifier.padding(padding).fillMaxSize())
            }
        }
    }
}

@Composable
private fun ChecklistContent(checklist: Checklist, onToggle: (Int) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                stringResource(R.string.checklist_progress, checklist.checkedCount, checklist.items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(checklist.items) { index, item -> ChecklistItemRow(item, onToggle = { onToggle(index) }) }
        if (checklist.isComplete) {
            item {
                Text(
                    stringResource(R.string.checklist_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(item: ChecklistItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) { contentDescription = item.text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = null)
        Text(
            item.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
