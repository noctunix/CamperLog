package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.countryDisplayName
import app.restvolt.camperlog.domain.countryFlagEmoji
import app.restvolt.camperlog.domain.tourCountries
import app.restvolt.camperlog.ui.CollapsibleSection
import app.restvolt.camperlog.ui.CountryChipsRow
import app.restvolt.camperlog.ui.CountryPicker
import app.restvolt.camperlog.ui.SectionCard
import java.util.Locale

/**
 * Länder-Abschnitt der Tourdetailseite: [autoDetected] (aus Stationskoordinaten und Vignetten)
 * zuzüglich [manuallyAdded] und abzüglich [manuallyRemoved] (siehe [tourCountries]), mit einer
 * Aktion zum Bearbeiten. [onSave] erhält die neuen [manuallyAdded]/[manuallyRemoved]-Mengen.
 */
@Composable
internal fun TourCountriesCard(
    autoDetected: Set<String>,
    manuallyAdded: Set<String>,
    manuallyRemoved: Set<String>,
    locale: Locale,
    onSave: (Set<String>, Set<String>) -> Unit,
) {
    var showEdit by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val countries = tourCountries(autoDetected, manuallyAdded, manuallyRemoved)
    val summary = if (countries.isEmpty()) {
        stringResource(R.string.countries_empty)
    } else {
        countries.sortedBy { countryDisplayName(it, locale) }.joinToString(", ") { countryDisplayName(it, locale) }
    }

    SectionCard {
        CollapsibleSection(
            title = stringResource(R.string.tour_section_countries),
            expanded = expanded,
            onToggle = { expanded = !expanded },
            summary = summary,
            actions = {
                TextButton(onClick = { showEdit = true }) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.countries_edit_action), modifier = Modifier.padding(start = 6.dp))
                }
            },
        ) {
            if (countries.isEmpty()) {
                Text(stringResource(R.string.countries_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else {
                CountryChipsRow(countries, locale)
            }
        }
    }

    if (showEdit) {
        EditCountriesDialog(
            autoDetected = autoDetected,
            manuallyAdded = manuallyAdded,
            manuallyRemoved = manuallyRemoved,
            locale = locale,
            onSave = { added, removed ->
                onSave(added, removed)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun EditCountriesDialog(
    autoDetected: Set<String>,
    manuallyAdded: Set<String>,
    manuallyRemoved: Set<String>,
    locale: Locale,
    onSave: (Set<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var added by remember { mutableStateOf(manuallyAdded) }
    var removed by remember { mutableStateOf(manuallyRemoved) }
    var showPicker by remember { mutableStateOf(false) }
    val current = tourCountries(autoDetected, added, removed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countries_edit_action)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current.sortedBy { countryDisplayName(it, locale) }.forEach { code ->
                    val name = countryDisplayName(code, locale)
                    InputChip(
                        selected = false,
                        onClick = {
                            if (code in added) {
                                added = added - code
                            } else {
                                removed = removed + code
                            }
                        },
                        label = { Text("${countryFlagEmoji(code)} $name") },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.countries_remove_country, name),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
                InputChip(
                    selected = false,
                    onClick = { showPicker = true },
                    label = { Text(stringResource(R.string.countries_add_action)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(added, removed) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showPicker) {
        CountryPicker(
            selected = null,
            locale = locale,
            onSelect = { code ->
                showPicker = false
                if (code != null && code !in current) {
                    if (code in removed) removed = removed - code else added = added + code
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}
