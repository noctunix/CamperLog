package app.restvolt.camperlog.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.checkedCount
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint

/** Checklisten eines Fahrzeugs ohne Tourbezug (z. B. Einwintern), neueste zuerst. */
@Composable
fun VehicleChecklistsScreen(
    viewModel: VehicleChecklistsViewModel,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenChecklist: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val startedChecklistId by viewModel.startedChecklistId.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.section_checklists),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onOpenTemplates) { Text(stringResource(R.string.checklist_templates_action)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showPicker = true }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.checklist_start))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.checklists.isEmpty() -> EmptyHint(stringResource(R.string.checklist_vehicle_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.checklists, key = Checklist::id) { checklist ->
                    ChecklistSummaryRow(checklist, onClick = { onOpenChecklist(checklist.id) })
                }
            }
        }
    }

    if (showPicker) {
        ChecklistTemplatePickerSheet(
            templates = state.templates,
            onSelect = { template ->
                showPicker = false
                viewModel.startChecklist(template)
            },
            onAddSuggested = { viewModel.addSuggestedTemplates(suggestedChecklistTemplates(resources)) },
            onDismiss = { showPicker = false },
        )
    }

    LaunchedEffect(startedChecklistId) {
        val id = startedChecklistId ?: return@LaunchedEffect
        onOpenChecklist(id)
        viewModel.onChecklistStartHandled()
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is VehicleChecklistMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.checklist_deleted),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteChecklist(current.checklist)
            }
            is VehicleChecklistMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun ChecklistSummaryRow(checklist: Checklist, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    val progress = "${checklist.checkedCount}/${checklist.items.size}"
    val description = stringResource(R.string.checklist_progress_cd, checklist.title, checklist.checkedCount, checklist.items.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            checklist.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
