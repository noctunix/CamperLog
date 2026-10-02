package app.restvolt.camperlog.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.QUICK_CURRENCIES
import java.util.Currency
import java.util.Locale

/** Auswahl aus Kurzliste und allen ISO-4217-Währungen; [excluded] stehen nicht zur Wahl. */
@Composable
internal fun CurrencyPicker(
    selected: Currency,
    excluded: Set<Currency>,
    locale: Locale,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val quick = QUICK_CURRENCIES.filter { it !in excluded }
    val others = ALL_CURRENCIES.filter { it !in excluded && it !in QUICK_CURRENCIES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_cost_pick_currency)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(quick, key = { "quick-" + it.currencyCode }) { CurrencyOption(it, it == selected, locale, onSelect) }
                item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                items(others, key = Currency::getCurrencyCode) { CurrencyOption(it, it == selected, locale, onSelect) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CurrencyOption(currency: Currency, selected: Boolean, locale: Locale, onSelect: (Currency) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(currency) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            "${currency.currencyCode} · ${currency.getDisplayName(locale)}",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
