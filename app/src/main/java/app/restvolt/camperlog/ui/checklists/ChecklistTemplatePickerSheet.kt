package app.restvolt.camperlog.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.ui.EmptyHint

/**
 * Vorlagenauswahl vor dem Starten einer Checkliste. Ohne Vorlagen bietet sie zunächst nur an, die
 * Vorschläge anzulegen; [onAddSuggested] legt sie an, die Auswahl bleibt danach offen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistTemplatePickerSheet(
    templates: List<ChecklistTemplate>,
    onSelect: (ChecklistTemplate) -> Unit,
    onAddSuggested: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            stringResource(R.string.checklist_picker_title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (templates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EmptyHint(stringResource(R.string.checklist_picker_empty))
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
