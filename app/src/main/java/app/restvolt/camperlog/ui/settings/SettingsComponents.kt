package app.restvolt.camperlog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.TileHttpCache
import app.restvolt.camperlog.domain.tileCacheMegabytes
import app.restvolt.camperlog.share.openNotificationSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val REMINDER_LEAD_DAYS_OPTIONS = listOf(7, 14, 30, 60, 90)

internal val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

/** Auswahl des Erscheinungsbilds; geteilt zwischen den Einstellungen und der Einführungstour. */
@Composable
internal fun ThemeModeRadioGroup(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
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

/** Zeile mit Bezeichnung und aktuellem Wert; das Tippen öffnet die Auswahl. */
@Composable
internal fun ReminderChoiceRow(label: String, valueText: String, onClick: () -> Unit) {
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

/** Vorlauf-Auswahl mit eigenem Dialogzustand; geteilt zwischen den Einstellungen und der Einführungstour. */
@Composable
internal fun ReminderLeadDaysRow(leadDays: Int, onLeadDaysChange: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ReminderChoiceRow(
        label = stringResource(R.string.settings_reminder_lead_days),
        valueText = pluralStringResource(R.plurals.settings_reminder_lead_days_option, leadDays, leadDays),
        onClick = { picking = true },
    )
    if (picking) {
        IntChoiceDialog(
            title = stringResource(R.string.settings_reminder_lead_days),
            options = REMINDER_LEAD_DAYS_OPTIONS,
            selected = leadDays,
            optionLabel = { pluralStringResource(R.plurals.settings_reminder_lead_days_option, it, it) },
            onSelect = {
                onLeadDaysChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Ganze Zeile als Schalter (Role.Switch), Titel und Unterzeile links, [Switch] rechts; geteilt
 * zwischen den Einstellungen (6.11) und der Einführungstour (6.10).
 */
@Composable
internal fun SwitchSettingRow(title: String, supportingText: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Hinweis nach abgelehnter Benachrichtigungsberechtigung (Android 13+), mit Link zu den Systemeinstellungen. */
@Composable
internal fun NotificationPermissionDeniedHint() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.settings_notifications_denied_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { context.openNotificationSettings() }) {
            Text(stringResource(R.string.settings_notifications_open_settings))
        }
    }
}

/** Dialog mit einer einfachen Auswahl aus [options]; [selected] ist vorausgewählt. */
@Composable
internal fun IntChoiceDialog(
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

/** "Was übertragen wird ▸" (6.11): klappt den genauen Datenschutztext (9) ein/aus. */
@Composable
internal fun WeatherTransferDetailRow() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.settings_weather_detail_toggle)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = { expanded = !expanded })
                .semantics { role = Role.Button },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = stringResource(if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section, title),
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (expanded) {
            Text(
                stringResource(R.string.about_privacy_bullet_weather_map),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Kartenspeicher: N MB [Leeren]" (6.11): löscht den HTTP-Kachel-Cache und den Session-Zwischenspeicher. */
@Composable
internal fun MapStorageRow(snackbar: SnackbarHostState, scope: CoroutineScope) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var sizeMb by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { sizeMb = tileCacheMegabytes(TileHttpCache.sizeBytes(context)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_map_storage, sizeMb), style = MaterialTheme.typography.bodyLarge)
        TextButton(
            onClick = {
                TileHttpCache.clear(context)
                sizeMb = 0
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_map_storage_cleared_hint)) }
            },
        ) { Text(stringResource(R.string.settings_map_storage_clear)) }
    }
}
