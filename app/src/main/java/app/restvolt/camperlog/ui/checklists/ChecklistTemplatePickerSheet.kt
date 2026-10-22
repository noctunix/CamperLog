package app.restvolt.camperlog.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate

/**
 * Vorlagenauswahl vor dem Starten einer Checkliste. Ohne Vorlagen bietet sie zunächst nur an, die
 * Vorschläge anzulegen; [onAddSuggested] legt sie an, die Auswahl bleibt danach offen. Das
 * Zahnrad neben dem Titel führt immer zur Vorlagenverwaltung, unabhängig vom Inhalt der Liste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistTemplatePickerSheet(
    templates: List<ChecklistTemplate>,
    onSelect: (ChecklistTemplate) -> Unit,
    onAddSuggested: () -> Unit,
    onManageTemplates: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.checklist_picker_title),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onManageTemplates) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.checklist_manage_templates_action))
            }
        }
        if (templates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val departure = suggestedChecklistTemplates(LocalResources.current).first()
                Text(stringResource(R.string.checklist_quick_start_hint), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Button(onClick = { onSelect(departure) }) {
                    Text(stringResource(R.string.checklist_quick_start_departure))
                }
                TextButton(onClick = onAddSuggested) {
                    Text(stringResource(R.string.checklist_add_suggested_templates))
                }
            }
        } else {
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(templates, key = ChecklistTemplate::id) { template ->
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.checklist_template_item_count, template.items.size, template.items.size))
                        },
                        modifier = Modifier.clickable { onSelect(template) },
                    )
                }
            }
        }
    }
}
