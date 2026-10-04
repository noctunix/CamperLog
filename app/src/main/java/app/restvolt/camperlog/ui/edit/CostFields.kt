package app.restvolt.camperlog.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ALL_CURRENCIES
import app.restvolt.camperlog.domain.CostInput
import app.restvolt.camperlog.domain.MAX_AMOUNT_MINOR
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.amountToInput
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.fractionDigits
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.messageRes
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Kostenzeilen des Formulars: je Zeile Betrag und Währung, höchstens eine Zeile pro Währung.
 * [focusRequester] fokussiert das Betragsfeld der ersten fehlerhaften Zeile, sonst das der ersten.
 */
@Composable
internal fun CostFields(
    costs: List<CostInput>,
    errors: Map<Int, TourError>,
    focusRequester: FocusRequester,
    onAmountChange: (Int, String) -> Unit,
    onCurrencyChange: (Int, Currency) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val focusIndex = errors.keys.minOrNull() ?: 0
    var pickerFor by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        costs.forEachIndexed { index, cost ->
            CostRow(
                cost = cost,
                error = errors[index]?.let { costErrorText(it, cost.currency, locale) },
                locale = locale,
                removable = costs.size > 1,
                onAmountChange = { onAmountChange(index, it) },
                onPickCurrency = { pickerFor = index },
                onRemove = { onRemove(index) },
                modifier = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
        if (costs.size < ALL_CURRENCIES.size) {
            TextButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.edit_cost_add), Modifier.padding(start = 8.dp))
            }
        }
    }

    pickerFor?.takeIf { it in costs.indices }?.let { index ->
        val taken = costs.filterIndexed { i, _ -> i != index }.map(CostInput::currency).toSet()
        CurrencyPicker(
            selected = costs[index].currency,
            excluded = taken,
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
private fun CostRow(
    cost: CostInput,
    error: String?,
    locale: Locale,
    removable: Boolean,
    onAmountChange: (String) -> Unit,
    onPickCurrency: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = cost.currency
    val currencyDescription = stringResource(R.string.edit_cost_currency, currency.getDisplayName(locale))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = cost.amount,
            onValueChange = onAmountChange,
            modifier = modifier.weight(1f),
            label = { Text(stringResource(R.string.edit_cost_amount, currency.getSymbol(locale))) },
            placeholder = { Text(stringResource(R.string.edit_cost_placeholder, exampleAmount(currency, locale))) },
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
        ) {
            Text(currency.currencyCode)
        }
        if (removable) {
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.edit_cost_remove, currency.currencyCode),
                )
            }
        }
    }
}

/** Beispielbetrag 49,90 mit den Nachkommastellen von [currency], z. B. `49` für ISK. */
private fun exampleAmount(currency: Currency, locale: Locale): String =
    amountToInput(BigDecimal("49.90").movePointRight(currency.fractionDigits).toLong(), currency, locale)

/** Fehlertext einer Kostenzeile; bei zu großen Beträgen mit dem Höchstwert in [currency]. */
@Composable
private fun costErrorText(error: TourError, currency: Currency, locale: Locale): String =
    if (error == TourError.AMOUNT_TOO_LARGE) {
        stringResource(R.string.error_amount_too_large, formatAmount(MAX_AMOUNT_MINOR, currency, locale))
    } else {
        stringResource(error.messageRes(TourField.COST))
    }
