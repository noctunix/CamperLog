package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.restvolt.camperlog.R
import java.time.LocalTime

/**
 * Schreibgeschütztes Uhrzeitfeld, das per Tippen oder Screenreader eine Zeitauswahl öffnet.
 * Wenn [time] gesetzt ist, ersetzt eine Löschen-Aktion das Uhrzeitsymbol.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(label: String, time: LocalTime?, modifier: Modifier = Modifier, onTimeSelected: (LocalTime?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.edit_pick_date, label)
    OutlinedTextField(
        value = time?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = pickLabel) {
                    showPicker = true
                    true
                }
            },
        label = { Text(label) },
        trailingIcon = {
            if (time != null) {
                IconButton(onClick = { onTimeSelected(null) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.station_clear_time))
                }
            } else {
                IconButton(onClick = { showPicker = true }) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = pickLabel)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    if (showPicker) {
        val now = LocalTime.now()
        val pickerState = rememberTimePickerState(initialHour = time?.hour ?: now.hour, initialMinute = time?.minute ?: now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = pickerState) },
        )
    }
}
