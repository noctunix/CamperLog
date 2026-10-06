package app.restvolt.camperlog.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.electricityPreview
import app.restvolt.camperlog.domain.formatAmount
import app.restvolt.camperlog.domain.formatKwh
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import java.util.Currency
import java.util.Locale

/**
 * Felder der Stromabrechnung: Abrechnungsart, je Art nur die dazu passenden Beträge, eine
 * gemeinsame Währung und eine live berechnete Vorschau aus [app.restvolt.camperlog.domain.electricityPreview].
 * [error] ist der erste Validierungsfehler der Abrechnungsfelder, unabhängig vom konkreten Feld.
 */
@Composable
internal fun ElectricityFields(
    input: StationInput,
    error: String?,
    change: ((StationInput) -> StationInput) -> Unit,
    onBillingChange: (ElectricityBilling?) -> Unit,
) {
    val locale = currentLocale()
    val billing = input.electricityBilling
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BillingKindField(billing, onBillingChange)
        if (billing != null && billing != ElectricityBilling.NONE && billing != ElectricityBilling.INCLUDED) {
            ElectricityCurrencyField(input.electricityCurrency) { currency -> change { it.copy(electricityCurrency = currency) } }
            when (billing) {
                ElectricityBilling.FLAT_PER_NIGHT, ElectricityBilling.FLAT_PER_STAY -> FormTextField(
                    label = stringResource(R.string.field_electricity_flat_amount, input.electricityCurrency.getSymbol(locale)),
                    value = input.electricityFlatAmount,
                    error = null,
                    onValueChange = { value -> change { it.copy(electricityFlatAmount = value) } },
                    keyboardOptions = decimalKeyboard,
                )
                ElectricityBilling.METERED -> {
                    FormTextField(
                        label = stringResource(R.string.field_electricity_price_per_kwh, input.electricityCurrency.getSymbol(locale)),
                        value = input.electricityPricePerKwh,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityPricePerKwh = value) } },
                        keyboardOptions = decimalKeyboard,
                    )
                    MeterOrKwhFields(input, change)
                }
                ElectricityBilling.BASE_PLUS_METERED -> {
                    FormTextField(
                        label = stringResource(R.string.field_electricity_base_fee, input.electricityCurrency.getSymbol(locale)),
                        value = input.electricityBaseFee,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityBaseFee = value) } },
                        keyboardOptions = decimalKeyboard,
                    )
                    FormTextField(
                        label = stringResource(R.string.field_electricity_price_per_kwh, input.electricityCurrency.getSymbol(locale)),
                        value = input.electricityPricePerKwh,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityPricePerKwh = value) } },
                        keyboardOptions = decimalKeyboard,
                    )
                    MeterOrKwhFields(input, change)
                }
                ElectricityBilling.COIN -> {
                    FormTextField(
                        label = stringResource(R.string.field_electricity_coin_price, input.electricityCurrency.getSymbol(locale)),
                        value = input.electricityCoinPrice,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityCoinPrice = value) } },
                        keyboardOptions = decimalKeyboard,
                    )
                    FormTextField(
                        label = stringResource(R.string.field_electricity_coins_used),
                        value = input.electricityCoinsUsed,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityCoinsUsed = value) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    FormTextField(
                        label = stringResource(R.string.field_electricity_kwh_per_coin),
                        value = input.electricityKwhPerCoin,
                        error = null,
                        onValueChange = { value -> change { it.copy(electricityKwhPerCoin = value) } },
                        keyboardOptions = decimalKeyboard,
                    )
                }
                ElectricityBilling.NONE, ElectricityBilling.INCLUDED -> Unit
            }
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            ElectricityResultLine(input, locale)
        }
    }
}

private val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

@Composable
private fun BillingKindField(selected: ElectricityBilling?, onSelect: (ElectricityBilling?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.field_electricity),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElectricityBilling.entries.forEach { kind ->
                FilterChip(
                    selected = selected == kind,
                    onClick = { onSelect(if (selected == kind) null else kind) },
                    label = { Text(stringResource(kind.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ElectricityCurrencyField(currency: Currency, onSelect: (Currency) -> Unit) {
    val locale = currentLocale()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.edit_cost_currency, currency.getDisplayName(locale))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_electricity_currency),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        ) { Text(currency.currencyCode) }
    }
    if (showPicker) {
        CurrencyPicker(
            selected = currency,
            excluded = emptySet(),
            locale = locale,
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** kWh kommen entweder aus Zählerstart/-ende oder direkt als Menge; ein Umschalter leert jeweils die andere Seite. */
@Composable
private fun MeterOrKwhFields(input: StationInput, change: ((StationInput) -> StationInput) -> Unit) {
    var meterMode by rememberSaveable(input.type) { mutableStateOf(input.electricityKwhUsed.isBlank()) }
    MeterInputToggle(meterMode) { isMeter ->
        meterMode = isMeter
        if (isMeter) {
            change { it.copy(electricityKwhUsed = "") }
        } else {
            change { it.copy(electricityMeterStart = "", electricityMeterEnd = "") }
        }
    }
    if (meterMode) {
        FormTextField(
            label = stringResource(R.string.field_electricity_meter_start),
            value = input.electricityMeterStart,
            error = null,
            onValueChange = { value -> change { it.copy(electricityMeterStart = value) } },
            keyboardOptions = decimalKeyboard,
        )
        FormTextField(
            label = stringResource(R.string.field_electricity_meter_end),
            value = input.electricityMeterEnd,
            error = null,
            onValueChange = { value -> change { it.copy(electricityMeterEnd = value) } },
            keyboardOptions = decimalKeyboard,
        )
    } else {
        FormTextField(
            label = stringResource(R.string.field_electricity_kwh_used),
            value = input.electricityKwhUsed,
            error = null,
            onValueChange = { value -> change { it.copy(electricityKwhUsed = value) } },
            keyboardOptions = decimalKeyboard,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterInputToggle(meterMode: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(true, false).forEachIndexed { index, isMeter ->
            SegmentedButton(
                selected = meterMode == isMeter,
                onClick = { onSelect(isMeter) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) {
                Text(stringResource(if (isMeter) R.string.electricity_input_mode_meter else R.string.electricity_input_mode_direct))
            }
        }
    }
}

/** "≈ 12,40 € · 21,5 kWh", politely angesagt, sobald sich Kosten oder kWh ändern. */
@Composable
private fun ElectricityResultLine(input: StationInput, locale: Locale) {
    val preview = input.electricityPreview(locale)
    val text = if (preview.cost == null && preview.kwh == null) {
        stringResource(R.string.electricity_result_pending)
    } else {
        val parts = listOfNotNull(
            preview.cost?.let { formatAmount(it.minor, it.currency, locale) },
            preview.kwh?.let { formatKwh(it, locale) },
        )
        stringResource(R.string.electricity_result_value, parts.joinToString(" · "))
    }
    Text(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
