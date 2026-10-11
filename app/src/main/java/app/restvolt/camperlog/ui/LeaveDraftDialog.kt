package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R

/**
 * Abfrage beim Verlassen eines Formulars, dessen Felder schon implizit (ohne Speichern-Button)
 * persistiert sind: [onDiscard] macht die Eingaben der Sitzung wieder ungültig (je nach Formular
 * löschen oder auf den Ausgangsstand zurücksetzen), [onKeep] verlässt einfach, da schon gespeichert
 * ist, [onCancel] bleibt auf dem Formular.
 */
@Composable
fun LeaveDraftDialog(onCancel: () -> Unit, onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.leave_draft_title)) },
        text = { Text(stringResource(R.string.leave_draft_text)) },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.leave_draft_discard)) }
                Button(onClick = onKeep) { Text(stringResource(R.string.action_save)) }
            }
        },
    )
}
