package app.restvolt.camperlog.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationInput
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.ui.CountryPicker
import app.restvolt.camperlog.ui.DateField
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes

/** Maut-Abschnitt: Art als Chips, bei einer Vignette Land und Gültigkeit, sonst nur die Zahlweise. */
@Composable
internal fun TollSection(
    input: StationInput,
    errors: Map<StationField, String>,
    change: ((StationInput) -> StationInput) -> Unit,
) {
    SectionCard {
        Text(stringResource(R.string.station_section_toll), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        TollKindField(input.tollKind) { kind -> change { it.copy(tollKind = kind) } }
        if (input.tollKind == TollKind.VIGNETTE) {
            CountryField(input.tollCountry, errors[StationField.TOLL_COUNTRY]) { country -> change { it.copy(tollCountry = country) } }
            DateField(
                stringResource(R.string.field_toll_valid_from),
                input.tollValidFrom,
                error = null,
                onDateSelected = { date -> change { it.copy(tollValidFrom = date) } },
            )
            DateField(
                stringResource(R.string.field_toll_valid_until),
                input.tollValidUntil,
                error = errors[StationField.TOLL_VALID_UNTIL],
                onDateSelected = { date -> change { it.copy(tollValidUntil = date) } },
                initialDate = input.tollValidFrom,
                minDate = input.tollValidFrom,
            )
        }
        FormTextField(
            label = stringResource(R.string.field_toll_payment_method),
            value = input.tollPaymentMethod,
            error = errors[StationField.TOLL_PAYMENT_METHOD],
            onValueChange = { value -> change { it.copy(tollPaymentMethod = value) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
    }
}

/** Fähre-Abschnitt: nur die Buchungsreferenz, die Route steht im Namen. */
@Composable
internal fun FerrySection(input: StationInput, error: String?, change: ((StationInput) -> StationInput) -> Unit) {
    SectionCard {
        Text(stringResource(R.string.station_section_ferry), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        FormTextField(
            label = stringResource(R.string.field_ferry_booking_reference),
            value = input.ferryBookingReference,
            error = error,
            onValueChange = { value -> change { it.copy(ferryBookingReference = value) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun TollKindField(selected: TollKind?, onSelect: (TollKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.field_toll_kind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TollKind.entries.forEach { kind ->
                FilterChip(
                    selected = selected == kind,
                    onClick = { onSelect(kind) },
                    label = { Text(stringResource(kind.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun CountryField(code: String, error: String?, onSelect: (String) -> Unit) {
    val locale = currentLocale()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val display = code.trim().takeIf { it.isNotEmpty() }?.let { countryDisplayName(it, locale) } ?: ""
    val description = stringResource(R.string.edit_pick_date, stringResource(R.string.field_toll_country))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        ) { Text(display.ifEmpty { stringResource(R.string.field_toll_country) }) }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (showPicker) {
        CountryPicker(
            selected = code.trim().ifEmpty { null },
            locale = locale,
            onSelect = {
                onSelect(it.orEmpty())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
