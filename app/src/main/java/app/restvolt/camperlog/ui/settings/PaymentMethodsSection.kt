package app.restvolt.camperlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.MAX_PAYMENT_METHOD_LENGTH
import app.restvolt.camperlog.domain.PaymentMethodRepository
import app.restvolt.camperlog.ui.SectionCard

/** Verwaltung der Zahlungsarten, die das Stationsformular als Vorschläge anbietet: Einträge entfernen, eigene ergänzen, Vorgaben wiederherstellen. */
@Composable
fun PaymentMethodsSection(repository: PaymentMethodRepository) {
    val methods by repository.methods.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (repository.add(draft)) draft = ""
    }
    SectionCard {
        Text(
            stringResource(R.string.settings_payment_methods),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.settings_payment_methods_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        methods.forEach { name ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { repository.remove(name) }) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.settings_payment_methods_remove, name),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_PAYMENT_METHOD_LENGTH).replace("\n", "") },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.settings_payment_methods_add)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            TextButton(onClick = { submit() }, enabled = draft.isNotBlank()) {
                Text(stringResource(R.string.settings_payment_methods_add_action))
            }
        }
        TextButton(onClick = repository::resetToDefaults) {
            Text(stringResource(R.string.settings_payment_methods_reset))
        }
    }
}
