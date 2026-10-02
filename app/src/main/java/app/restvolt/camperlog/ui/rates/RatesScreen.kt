package app.restvolt.camperlog.ui.rates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatRate
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.CurrencyPicker
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale
import java.util.Currency
import java.util.Locale

/** Hauptwährung wählen und Kurse zum Euro verwalten. */
@Composable
fun RatesScreen(
    viewModel: RatesViewModel,
    onBack: () -> Unit,
    onEditRate: (currencyCode: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var pickMainCurrency by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.rates_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEditRate(null) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.rates_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Platz für den schwebenden Button, damit die letzte Karte nicht verdeckt wird.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isLoading) return@LazyColumn
            item(key = "main") {
                MainCurrencyCard(state.mainCurrency, locale, onChange = { pickMainCurrency = true })
            }
            if (state.missing.isNotEmpty()) {
                item(key = "missing") { MissingRatesCard(state.missing, onAdd = { onEditRate(it.currencyCode) }) }
            }
            item(key = "header") {
                Text(
                    stringResource(R.string.rates_list),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.rates.isEmpty()) {
                item(key = "empty") { EmptyHint(stringResource(R.string.rates_empty)) }
            }
            items(state.rates, key = { it.currency.currencyCode }) { rate ->
                RateCard(
                    rate = rate,
                    locale = locale,
                    onEdit = { onEditRate(rate.currency.currencyCode) },
                    onDelete = { viewModel.delete(rate) },
                )
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

    LaunchedEffect(state.deleted) {
        val deleted = state.deleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = resources.getString(R.string.rates_deleted, deleted.currency.currencyCode),
            actionLabel = resources.getString(R.string.tours_undo),
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.onDeletedShown()
    }
    LaunchedEffect(state.writeFailed) {
        if (state.writeFailed) {
            snackbar.showSnackbar(resources.getString(R.string.rates_write_failed), withDismissAction = true)
            viewModel.onWriteFailureShown()
        }
    }
}

@Composable
private fun MainCurrencyCard(currency: Currency, locale: Locale, onChange: () -> Unit) {
    val name = currency.getDisplayName(locale)
    val description = stringResource(R.string.rates_change_main_currency, name)
    SectionCard {
        Text(
            stringResource(R.string.rates_main_currency),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.rates_main_currency_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.rates_currency_option, currency.currencyCode, name))
        }
    }
}

@Composable
private fun MissingRatesCard(missing: List<Currency>, onAdd: (Currency) -> Unit) {
    SectionCard {
        Text(stringResource(R.string.rates_missing), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            missing.forEach { currency ->
                val description = stringResource(R.string.rates_add_for, currency.currencyCode)
                OutlinedButton(
                    onClick = { onAdd(currency) },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(currency.currencyCode, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RateCard(rate: ExchangeRate, locale: Locale, onEdit: () -> Unit, onDelete: () -> Unit) {
    val code = rate.currency.currencyCode
    val editLabel = stringResource(R.string.rates_edit, code)
    val date = formatDate(rate.date)
    SectionCard(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = editLabel, onClick = onEdit),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.rates_value, formatRate(rate.perEuro, locale), code),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    rate.currency.getDisplayName(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (rate.source.isBlank()) {
                        stringResource(R.string.rates_as_of, date)
                    } else {
                        stringResource(R.string.rates_as_of_source, date, rate.source)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.rates_delete, code))
            }
        }
    }
}
