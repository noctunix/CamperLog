package app.restvolt.camperlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.rates.MainCurrencyCard
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: RatesViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    reminderSettings: ReminderSettings,
    onBack: () -> Unit,
    onOpenRates: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var pickMainCurrency by rememberSaveable { mutableStateOf(false) }
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    var pickLeadDays by rememberSaveable { mutableStateOf(false) }
    var pickOilInterval by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.settings_appearance),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.selectableGroup()) {
                        ThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = mode == themeMode, role = Role.RadioButton) { onThemeModeChange(mode) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = mode == themeMode, onClick = null)
                                Text(stringResource(mode.label), modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
            if (!state.isLoading) {
                item {
                    MainCurrencyCard(state.mainCurrency, locale, onChange = { pickMainCurrency = true })
                }
            }
            item {
                OutlinedButton(onClick = onOpenRates, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_rates))
                }
            }
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.settings_reminders),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.settings_reminders_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReminderChoiceRow(
                        label = stringResource(R.string.settings_reminder_lead_days),
                        valueText = pluralStringResource(R.plurals.settings_reminder_lead_days_option, reminderPreferences.leadDays, reminderPreferences.leadDays),
                        onClick = { pickLeadDays = true },
                    )
                    ReminderChoiceRow(
                        label = stringResource(R.string.settings_reminder_oil_interval),
                        valueText = pluralStringResource(
                            R.plurals.settings_reminder_oil_interval_option,
                            reminderPreferences.oilChangeIntervalMonths,
                            reminderPreferences.oilChangeIntervalMonths,
                        ),
                        onClick = { pickOilInterval = true },
                    )
                }
            }
        }
    }

    if (pickLeadDays) {
        IntChoiceDialog(
            title = stringResource(R.string.settings_reminder_lead_days),
            options = REMINDER_LEAD_DAYS_OPTIONS,
            selected = reminderPreferences.leadDays,
            optionLabel = { pluralStringResource(R.plurals.settings_reminder_lead_days_option, it, it) },
            onSelect = {
                reminderSettings.reminderLeadDays = it
                pickLeadDays = false
            },
            onDismiss = { pickLeadDays = false },
        )
    }

    if (pickOilInterval) {
        IntChoiceDialog(
            title = stringResource(R.string.settings_reminder_oil_interval),
            options = REMINDER_OIL_INTERVAL_OPTIONS,
            selected = reminderPreferences.oilChangeIntervalMonths,
            optionLabel = { pluralStringResource(R.plurals.settings_reminder_oil_interval_option, it, it) },
            onSelect = {
                reminderSettings.oilChangeIntervalMonths = it
                pickOilInterval = false
            },
            onDismiss = { pickOilInterval = false },
        )
    }

    if (pickMainCurrency) {
        CurrencyPicker(
            selected = state.mainCurrency,
            excluded = emptySet(),
            locale = locale,
            onSelect = {
                pickMainCurrency = false
                viewModel.setMainCurrency(it)
            },
            onDismiss = { pickMainCurrency = false },
        )
    }

    LaunchedEffect(state.writeFailed) {
        if (state.writeFailed) {
            snackbar.showSnackbar(resources.getString(R.string.rates_write_failed), withDismissAction = true)
            viewModel.onWriteFailureShown()
        }
    }
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private val REMINDER_LEAD_DAYS_OPTIONS = listOf(7, 14, 30, 60, 90)
private val REMINDER_OIL_INTERVAL_OPTIONS = listOf(6, 12, 24)

/** Zeile mit Bezeichnung und aktuellem Wert; das Tippen öffnet die Auswahl. */
@Composable
private fun ReminderChoiceRow(label: String, valueText: String, onClick: () -> Unit) {
    val description = stringResource(R.string.edit_choice_option, label, valueText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(valueText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Dialog mit einer einfachen Auswahl aus [options]; [selected] ist vorausgewählt. */
@Composable
private fun IntChoiceDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    optionLabel: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    val text = optionLabel(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(text, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
