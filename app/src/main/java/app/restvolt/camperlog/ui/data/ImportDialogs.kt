package app.restvolt.camperlog.ui.data

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.domain.supportedLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Vorschau einer eingelesenen Sicherung mit Wahl des Modus; „Alles ersetzen“ verlangt eine zweite Bestätigung.
 * [onImport] wird erst nach allen Bestätigungen aufgerufen. Solange [PendingImport.running] gilt,
 * lässt sich der Dialog weder schließen noch erneut bestätigen.
 */
@Composable
fun ImportDialog(pending: PendingImport, onImport: (ImportMode) -> Unit, onCancel: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.MERGE) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val busy = pending.running

    if (confirmReplace) {
        ReplaceConfirmDialog(
            pending = pending,
            onConfirm = { onImport(ImportMode.REPLACE) },
            onBack = { if (!busy) confirmReplace = false },
        )
        return
    }

    val backup = pending.backup
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val repairCount = backup.vehicles.sumOf { it.repairs.size }
                val logEntryCount = backup.vehicles.sumOf { it.logEntries.size }
                Text(
                    stringResource(
                        R.string.import_preview_summary,
                        formatDateTime(backup.exportedAt),
                        pluralStringResource(R.plurals.import_tours, backup.tours.size, backup.tours.size),
                        pluralStringResource(R.plurals.import_vehicles, backup.vehicles.size, backup.vehicles.size),
                        pluralStringResource(R.plurals.import_stops, backup.stations.size, backup.stations.size),
                        pluralStringResource(R.plurals.import_repairs, repairCount, repairCount),
                        pluralStringResource(R.plurals.import_log_entries, logEntryCount, logEntryCount),
                        pluralStringResource(R.plurals.import_rates, backup.rates.size, backup.rates.size),
                        backup.mainCurrency.currencyCode,
                    ),
                )
                Column(Modifier.selectableGroup()) {
                    ModeOption(ImportMode.MERGE, mode, R.string.import_mode_merge, R.string.import_mode_merge_hint) { mode = it }
                    ModeOption(ImportMode.REPLACE, mode, R.string.import_mode_replace, R.string.import_mode_replace_hint, !busy) { mode = it }
                }
                ImportStatus(pending)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (mode == ImportMode.REPLACE) confirmReplace = true else onImport(mode) },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.import_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun formatDateTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(supportedLocale(Locale.getDefault()))
        .format(instant.atZone(ZoneId.systemDefault()))

/** Fortschritt oder Fehler des laufenden Imports; TalkBack kündigt Änderungen an. */
@Composable
private fun ImportStatus(pending: PendingImport) {
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when {
        pending.running -> Row(announce, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.import_running), Modifier.padding(start = 12.dp))
        }
        pending.failed -> Text(
            stringResource(R.string.import_failed),
            modifier = announce,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ModeOption(
    option: ImportMode,
    selected: ImportMode,
    @StringRes label: Int,
    @StringRes hint: Int,
    enabled: Boolean = true,
    onSelect: (ImportMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = option == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = option == selected, onClick = null, enabled = enabled, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
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
private fun ReplaceConfirmDialog(pending: PendingImport, onConfirm: () -> Unit, onBack: () -> Unit) {
    val existingTours = pending.existingTours
    val busy = pending.running
    AlertDialog(
        onDismissRequest = onBack,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.import_replace_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (existingTours == 0) {
                        stringResource(R.string.import_replace_text_no_tours)
                    } else {
                        pluralStringResource(R.plurals.import_replace_text, existingTours, existingTours)
                    },
                )
                ImportStatus(pending)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.import_replace_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
