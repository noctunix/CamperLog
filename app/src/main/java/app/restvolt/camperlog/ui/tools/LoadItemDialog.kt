package app.restvolt.camperlog.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LoadCategory
import app.restvolt.camperlog.domain.LoadItem
import app.restvolt.camperlog.domain.LoadItemError
import app.restvolt.camperlog.domain.LoadItemField
import app.restvolt.camperlog.domain.LoadItemInput
import app.restvolt.camperlog.domain.MAX_LOAD_ITEM_MASS_KG
import app.restvolt.camperlog.domain.MAX_LOAD_ITEM_QUANTITY
import app.restvolt.camperlog.domain.MAX_LOAD_POSITION_CM
import app.restvolt.camperlog.domain.MIN_LOAD_POSITION_CM
import app.restvolt.camperlog.domain.defaultInWeighing
import app.restvolt.camperlog.domain.toInput
import app.restvolt.camperlog.domain.toItem
import app.restvolt.camperlog.domain.validate
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import java.text.NumberFormat
import java.util.Locale

/**
 * Formular eines Beladungsgegenstands als Dialog. [original] `null` legt einen neuen Gegenstand an;
 * [onDelete] ist nur beim Bearbeiten gesetzt.
 */
@Composable
fun LoadItemDialog(
    vehicleId: Long,
    original: LoadItem?,
    onDismiss: () -> Unit,
    onSave: (LoadItem) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val locale = currentLocale()
    var input by remember {
        mutableStateOf(original?.toInput(locale) ?: LoadItemInput(inWeighing = defaultInWeighing(LoadCategory.EQUIPMENT)))
    }
    // Nur bei einem neuen Gegenstand folgt der Schalter der Kategorie-Vorgabe, solange der Nutzer ihn
    // nicht selbst verändert hat; ein bestehender Gegenstand behält immer seinen gespeicherten Wert.
    var inWeighingTouched by remember { mutableStateOf(original != null) }
    var touched by remember { mutableStateOf(false) }
    val errors = if (touched) input.validate(locale) else emptyMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.load_item_dialog_title_add else R.string.load_item_dialog_title_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = input.name,
                    onValueChange = { input = input.copy(name = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = errors.containsKey(LoadItemField.NAME),
                    supportingText = errors[LoadItemField.NAME]?.let { { Text(itemErrorText(it, LoadItemField.NAME, locale)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                CategoryDropdown(
                    selected = input.category,
                    onSelect = { category ->
                        input = input.copy(
                            category = category,
                            inWeighing = if (!inWeighingTouched) defaultInWeighing(category) else input.inWeighing,
                        )
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input.massKg,
                        onValueChange = { input = input.copy(massKg = it) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.load_item_mass_label)) },
                        isError = errors.containsKey(LoadItemField.MASS),
                        supportingText = errors[LoadItemField.MASS]?.let { { Text(itemErrorText(it, LoadItemField.MASS, locale)) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = input.quantity,
                        onValueChange = { input = input.copy(quantity = it) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.load_item_quantity_label)) },
                        isError = errors.containsKey(LoadItemField.QUANTITY),
                        supportingText = errors[LoadItemField.QUANTITY]?.let { { Text(itemErrorText(it, LoadItemField.QUANTITY, locale)) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                }
                OutlinedTextField(
                    value = input.positionCm,
                    onValueChange = { input = input.copy(positionCm = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_position_label)) },
                    isError = errors.containsKey(LoadItemField.POSITION),
                    supportingText = {
                        Text(errors[LoadItemField.POSITION]?.let { itemErrorText(it, LoadItemField.POSITION, locale) } ?: stringResource(R.string.load_position_supporting))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = input.inWeighing,
                            role = Role.Switch,
                            onValueChange = {
                                inWeighingTouched = true
                                input = input.copy(inWeighing = it)
                            },
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.load_item_in_weighing_label), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = input.inWeighing, onCheckedChange = null)
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.load_item_delete_action)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                touched = true
                if (input.validate(locale).isEmpty()) {
                    onSave(input.toItem(original, vehicleId, locale))
                    onDismiss()
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: LoadCategory, onSelect: (LoadCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_cost_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LoadCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(category)
                    },
                )
            }
        }
    }
}

/** Fehlertext zu [this] am Feld [field] des Gegenstandsformulars. */
@Composable
private fun itemErrorText(error: LoadItemError, field: LoadItemField, locale: Locale): String = when (error) {
    LoadItemError.REQUIRED -> stringResource(R.string.load_name_required)
    LoadItemError.TOO_LONG -> stringResource(R.string.error_text_too_long)
    LoadItemError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
    LoadItemError.NOT_POSITIVE -> stringResource(R.string.error_must_be_positive, if (field == LoadItemField.MASS) "kg" else "")
    LoadItemError.TOO_LARGE -> {
        val max = if (field == LoadItemField.MASS) {
            "${NumberFormat.getIntegerInstance(locale).format(MAX_LOAD_ITEM_MASS_KG)} kg"
        } else {
            NumberFormat.getIntegerInstance(locale).format(MAX_LOAD_ITEM_QUANTITY)
        }
        stringResource(R.string.error_value_too_large, max)
    }
    LoadItemError.OUT_OF_RANGE -> stringResource(
        R.string.error_out_of_range,
        "${NumberFormat.getIntegerInstance(locale).format(MIN_LOAD_POSITION_CM)} cm",
        "${NumberFormat.getIntegerInstance(locale).format(MAX_LOAD_POSITION_CM)} cm",
    )
}
