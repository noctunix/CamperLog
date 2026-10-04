package app.restvolt.camperlog.ui.data

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.domain.formatDate
import java.time.ZoneId

/**
 * Vorschau einer eingelesenen Sicherung mit Wahl des Modus; „Alles ersetzen“ verlangt eine zweite Bestätigung.
 * [onImport] wird erst nach allen Bestätigungen aufgerufen.
 */
@Composable
fun ImportDialog(pending: PendingImport, onImport: (ImportMode) -> Unit, onCancel: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.MERGE) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }

    if (confirmReplace) {
        ReplaceConfirmDialog(
            existingTours = pending.existingTours,
            onConfirm = { onImport(ImportMode.REPLACE) },
            onBack = { confirmReplace = false },
        )
        return
    }

    val backup = pending.backup
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.import_preview_summary,
                        formatDate(backup.exportedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                        pluralStringResource(R.plurals.import_tours, backup.tours.size, backup.tours.size),
                        pluralStringResource(R.plurals.import_rates, backup.rates.size, backup.rates.size),
                        backup.mainCurrency.currencyCode,
                    ),
                )
                Column(Modifier.selectableGroup()) {
                    ModeOption(ImportMode.MERGE, mode, R.string.import_mode_merge, R.string.import_mode_merge_hint) { mode = it }
                    ModeOption(ImportMode.REPLACE, mode, R.string.import_mode_replace, R.string.import_mode_replace_hint) { mode = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (mode == ImportMode.REPLACE) confirmReplace = true else onImport(mode) }) {
                Text(stringResource(R.string.import_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ModeOption(option: ImportMode, selected: ImportMode, @StringRes label: Int, @StringRes hint: Int, onSelect: (ImportMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplaceConfirmDialog(existingTours: Int, onConfirm: () -> Unit, onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(stringResource(R.string.import_replace_title)) },
        text = {
            Text(
                if (existingTours == 0) {
                    stringResource(R.string.import_replace_text_no_tours)
                } else {
                    pluralStringResource(R.plurals.import_replace_text, existingTours, existingTours)
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.import_replace_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
