package app.restvolt.camperlog.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.MAX_AMOUNT_MINOR
import app.restvolt.camperlog.domain.StationCostInput
import app.restvolt.camperlog.domain.StationError
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.messageRes
import java.util.Currency
import java.util.Locale

/**
 * Kostenzeilen des Stationsformulars: je Zeile Kategorie, Betrag mit Währung und optionale Notiz.
 * Anders als bei Touren kann dieselbe Kategorie mehrfach vorkommen (wird beim Speichern je Kategorie
 * und Währung zusammengefasst). [excludeElectricity] blendet die Kategorie "Strom" bei neuen und
 * bestehenden Zeilen aus, solange die Stromabrechnung schon einen Betrag ergibt (siehe [CostCategory]).
 */
@Composable
internal fun StationCostFields(
    costs: List<StationCostInput>,
    errors: Map<Int, StationError>,
    excludeElectricity: Boolean,
    focusRequester: FocusRequester,
    onCategoryChange: (Int, CostCategory) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onCurrencyChange: (Int, Currency) -> Unit,
    onNoteChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val focusIndex = errors.keys.minOrNull() ?: 0
    var pickerFor by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        costs.forEachIndexed { index, cost ->
            StationCostRow(
                cost = cost,
                error = errors[index]?.let { costErrorText(it, cost.currency, locale) },
                locale = locale,
                excludeElectricity = excludeElectricity,
                onCategoryChange = { onCategoryChange(index, it) },
                onAmountChange = { onAmountChange(index, it) },
                onPickCurrency = { pickerFor = index },
                onNoteChange = { onNoteChange(index, it) },
                onRemove = { onRemove(index) },
                modifier = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
        TextButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.station_cost_add), Modifier.padding(start = 8.dp))
        }
    }

    pickerFor?.takeIf { it in costs.indices }?.let { index ->
        CurrencyPicker(
            selected = costs[index].currency,
            excluded = emptySet(),
            locale = locale,
            onSelect = {
                onCurrencyChange(index, it)
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }
}

@Composable
private fun StationCostRow(
    cost: StationCostInput,
    error: String?,
    locale: Locale,
    excludeElectricity: Boolean,
    onCategoryChange: (CostCategory) -> Unit,
    onAmountChange: (String) -> Unit,
    onPickCurrency: () -> Unit,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val currency = cost.currency
    val categoryLabel = stringResource(cost.category.labelRes)
    val amountDescription = stringResource(R.string.station_cost_amount, categoryLabel, currency.getSymbol(locale))
    val currencyDescription = stringResource(R.string.edit_cost_currency, currency.getDisplayName(locale))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryField(cost.category, excludeElectricity, onCategoryChange)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cost.amount,
                onValueChange = onAmountChange,
                modifier = modifier
                    .weight(1f)
                    .semantics { contentDescription = amountDescription },
                label = { Text(stringResource(R.string.edit_cost_amount, currency.getSymbol(locale))) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedButton(
                onClick = onPickCurrency,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = currencyDescription },
            ) { Text(currency.currencyCode) }
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.station_cost_remove, categoryLabel))
            }
        }
        OutlinedTextField(
            value = cost.note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_cost_note)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(selected: CostCategory, excludeElectricity: Boolean, onSelect: (CostCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = CostCategory.entries.filter { it != CostCategory.ELECTRICITY || !excludeElectricity || it == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_cost_category)) },
            leadingIcon = { Icon(painterResource(selected.iconRes), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes)) },
                    leadingIcon = { Icon(painterResource(category.iconRes), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(category)
                    },
                )
            }
        }
    }
}

/** Fehlertext einer Kostenzeile; bei zu großen Beträgen mit dem Höchstwert in [currency]. */
@Composable
private fun costErrorText(error: StationError, currency: Currency, locale: Locale): String =
    if (error == StationError.AMOUNT_TOO_LARGE) {
        stringResource(R.string.error_amount_too_large, formatAmount(MAX_AMOUNT_MINOR, currency, locale))
    } else {
        stringResource(error.messageRes(StationField.COST))
    }
