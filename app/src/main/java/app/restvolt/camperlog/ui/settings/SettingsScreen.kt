package app.restvolt.camperlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.restvolt.camperlog.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: RatesViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenRates: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var pickMainCurrency by rememberSaveable { mutableStateOf(false) }

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
        }
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
