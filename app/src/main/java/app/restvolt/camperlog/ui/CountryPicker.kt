package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ALL_COUNTRY_CODES
import app.restvolt.camperlog.domain.countryDisplayName
import java.util.Locale

/** Durchsuchbare Auswahl eines ISO-3166-1-alpha-2-Ländercodes; [selected] `null` zeigt keinen markiert an. */
@Composable
internal fun CountryPicker(selected: String?, locale: Locale, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(locale) { ALL_COUNTRY_CODES.map { it to countryDisplayName(it, locale) }.sortedBy { it.second } }
    val filtered = if (query.isBlank()) {
        options
    } else {
        options.filter { (code, name) -> name.contains(query, ignoreCase = true) || code.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.station_toll_pick_country)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.station_toll_country_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.medium,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item(key = "none") { CountryOption(null, selected == null, locale, onSelect) }
                    items(filtered, key = { it.first }) { (code, _) -> CountryOption(code, code == selected, locale, onSelect) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CountryOption(code: String?, selected: Boolean, locale: Locale, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(code) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            code?.let { "$it · ${countryDisplayName(it, locale)}" } ?: stringResource(R.string.station_toll_country_none),
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
