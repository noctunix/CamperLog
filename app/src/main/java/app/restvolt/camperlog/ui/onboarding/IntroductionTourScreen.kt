package app.restvolt.camperlog.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.ReminderLeadDaysRow
import app.restvolt.camperlog.ui.settings.SwitchSettingRow
import app.restvolt.camperlog.ui.settings.ThemeModeRadioGroup
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 6
private const val PAGE_SETTINGS = 4
private const val PAGE_FINISH = 5

/**
 * Einführungstour über [PAGE_COUNT] Seiten, erreichbar beim ersten Start und erneut über "Über
 * CamperLog". [reminderSettings] und [locationSettings] sind dieselben Instanzen wie im übrigen
 * Navigationsgraphen; [onThemeModeChange] wirkt sofort wie im Einstellungen-Bildschirm. Die letzte
 * Seite ist die Opt-in-Seite (6.10) mit dem Schalter "Standort"; [onFinished] markiert die Tour als
 * gesehen, egal ob sie zu Ende durchlaufen oder übersprungen wurde.
 */
@Composable
fun IntroductionTourScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    reminderSettings: ReminderSettings,
    locationSettings: LocationSettings,
    onFinished: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val locationEnabled by locationSettings.values.collectAsStateWithLifecycle()

    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onFinished()
        }
    }

    // Surface statt Modifier.background: setzt auch die Inhaltsfarbe, sonst bleibt Text im Dark Mode schwarz.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (pagerState.currentPage < PAGE_FINISH) {
                    TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(R.string.btn_intro_skip))
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> TourPage(R.drawable.ic_map, R.string.title_intro_welcome, R.string.body_intro_welcome)
                    1 -> TourPage(R.drawable.ic_book, R.string.title_intro_logbook, R.string.body_intro_logbook)
                    2 -> TourPage(R.drawable.ic_directions_car, R.string.title_intro_vehicle, R.string.body_intro_vehicle)
                    3 -> TourPage(R.drawable.ic_import_export, R.string.title_intro_data, R.string.body_intro_data)
                    PAGE_SETTINGS -> SettingsPage(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        leadDays = reminderPreferences.leadDays,
                        onLeadDaysChange = { reminderSettings.reminderLeadDays = it },
                    )
                    PAGE_FINISH -> OptInPage(locationEnabled = locationEnabled, onLocationEnabledChange = { locationSettings.enabled = it })
                }
            }

            TourControls(pagerState = pagerState, scope = scope, onFinished = onFinished)
        }
    }
}

@Composable
private fun TourPage(iconRes: Int, titleRes: Int, bodyRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(titleRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsPage(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    leadDays: Int,
    onLeadDaysChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.title_intro_settings),
            modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.body_intro_settings),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Text(
            stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        ThemeModeRadioGroup(themeMode, onThemeModeChange)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_reminders),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        ReminderLeadDaysRow(leadDays, onLeadDaysChange)
    }
}

/**
 * Letzte Seite der Einführungstour (6.10): Opt-in für Standort, ohne dass das Einschalten hier
 * schon einen Berechtigungsdialog auslöst (der kommt erst beim ersten Tastendruck, 6.7). Phase F
 * ergänzt hier den zweiten Schalter "Wetter & Karte".
 */
@Composable
private fun OptInPage(locationEnabled: Boolean, onLocationEnabledChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.title_intro_optin),
            modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.body_intro_optin),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        SwitchSettingRow(
            title = stringResource(R.string.location_switch_title),
            supportingText = stringResource(R.string.location_switch_support_intro),
            checked = locationEnabled,
            onCheckedChange = onLocationEnabledChange,
        )
        Text(
            stringResource(R.string.body_intro_optin_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun TourControls(pagerState: PagerState, scope: CoroutineScope, onFinished: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        if (pagerState.currentPage > 0) {
            TextButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text(stringResource(R.string.btn_intro_back))
            }
        }

        val stepDescription = stringResource(R.string.a11y_intro_step, pagerState.currentPage + 1, PAGE_COUNT)
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .semantics(mergeDescendants = true) { contentDescription = stepDescription },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < PAGE_FINISH) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(
                stringResource(
                    if (pagerState.currentPage < PAGE_FINISH) R.string.btn_intro_next else R.string.btn_intro_get_started,
                ),
            )
        }
    }
}
