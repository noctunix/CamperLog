package app.restvolt.camperlog.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.about.GuidedTourEntry
import app.restvolt.camperlog.ui.about.GuidedTourRow
import app.restvolt.camperlog.ui.about.openExternalLink
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.rates.MainCurrencyCard
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: RatesViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentColor: AccentColor,
    onAccentColorChange: (AccentColor) -> Unit,
    reminderSettings: ReminderSettings,
    notificationSettings: NotificationSettings,
    locationSettings: LocationSettings,
    weatherSettings: WeatherSettings,
    trackSettings: TrackRecordingSettings,
    homeLocationSettings: HomeLocationSettings,
    homeLocationViewModel: HomeLocationViewModel,
    guidedTours: List<GuidedTourEntry>,
    onBack: () -> Unit,
    onOpenRates: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickMainCurrency by rememberSaveable { mutableStateOf(false) }
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val notificationsEnabled by notificationSettings.values.collectAsStateWithLifecycle()
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val locationEnabled by locationSettings.values.collectAsStateWithLifecycle()
    val weatherEnabled by weatherSettings.values.collectAsStateWithLifecycle()
    var pickOilInterval by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionDenied = !granted
        notificationSettings.enabled = granted
    }

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
                    ThemeModeRadioGroup(themeMode, onThemeModeChange)
                    Text(
                        stringResource(R.string.settings_accent_color),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AccentColorSwatchRow(accentColor, onAccentColorChange)
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
                    ReminderLeadDaysRow(reminderPreferences.leadDays) { reminderSettings.reminderLeadDays = it }
                    ReminderChoiceRow(
                        label = stringResource(R.string.settings_reminder_oil_interval),
                        valueText = pluralStringResource(
                            R.plurals.settings_reminder_oil_interval_option,
                            reminderPreferences.oilChangeIntervalMonths,
                            reminderPreferences.oilChangeIntervalMonths,
                        ),
                        onClick = { pickOilInterval = true },
                    )
                    SwitchSettingRow(
                        title = stringResource(R.string.settings_notifications_switch_title),
                        supportingText = stringResource(R.string.settings_notifications_switch_support),
                        checked = notificationsEnabled,
                        onCheckedChange = { wantsEnabled ->
                            when {
                                !wantsEnabled -> {
                                    notificationPermissionDenied = false
                                    notificationSettings.enabled = false
                                }
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> notificationSettings.enabled = true
                            }
                        },
                    )
                    if (notificationPermissionDenied) {
                        NotificationPermissionDeniedHint()
                    }
                }
            }
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.settings_location_section),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LocationSwitchSection(locationSettings) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_location_revoked_hint)) }
                    }
                    SwitchSettingRow(
                        title = stringResource(R.string.weather_switch_title),
                        supportingText = stringResource(R.string.settings_weather_switch_support),
                        checked = weatherEnabled,
                        onCheckedChange = { enabled ->
                            weatherSettings.enabled = enabled
                            if (!enabled) {
                                scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_weather_off_hint)) }
                            }
                        },
                    )
                    TrackRecordingSettingsSection(trackSettings) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_track_off_hint)) }
                    }
                    if (weatherEnabled) {
                        WeatherTransferDetailRow()
                        MapStorageRow(snackbar, scope)
                    }
                }
            }
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.settings_home_section),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.settings_home_section_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HomeLocationSection(homeLocationSettings, locationEnabled, homeLocationViewModel.locationCapture)
                }
            }
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.guide_tutorials_section_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    guidedTours.forEach { tour -> GuidedTourRow(tour) }
                }
            }
            item {
                val donateUri = stringResource(R.string.about_donate_uri)
                val noAppAvailable = stringResource(R.string.about_no_app_available)
                OutlinedButton(
                    onClick = { openExternalLink(scope, context, snackbar, noAppAvailable, donateUri) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.about_action_support))
                }
            }
            item {
                OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_about))
                }
            }
        }
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

private val REMINDER_OIL_INTERVAL_OPTIONS = listOf(6, 12, 24)
