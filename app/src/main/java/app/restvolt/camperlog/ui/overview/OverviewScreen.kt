package app.restvolt.camperlog.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.currentLocale

/** Kennzahlen über alle Touren und je Jahr, neuestes Jahr zuerst. */
@Composable
fun OverviewScreen(viewModel: OverviewViewModel, onBack: () -> Unit, onOpenRates: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.overview_title),
                onBack = onBack,
                actions = { TextButton(onClick = onOpenRates) { Text(stringResource(R.string.rates_title)) } },
            )
        },
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
            val total = state.total
            when {
                state.isLoading -> Unit
                total == null || total.totals.tours == 0 -> item { EmptyHint(stringResource(R.string.overview_empty)) }
                else -> {
                    item { TotalsCard(stringResource(R.string.overview_total), total) }
                    items(state.years, key = { it.first }) { (year, row) -> TotalsCard(year.toString(), row) }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(title: String, row: TotalsRow) {
    val totals = row.totals
    val locale = currentLocale()
    SectionCard {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LabeledValue(stringResource(R.string.overview_tours), totals.tours.toString())
        LabeledValue(stringResource(R.string.field_distance), stringResource(R.string.distance_km, totals.distanceKm))
        LabeledValue(stringResource(R.string.field_travel_days), totals.travelDays.toString())
        LabeledValue(stringResource(R.string.field_overnight_stays), totals.overnightStays.toString())
        LabeledValue(stringResource(R.string.field_cost), formatAmounts(totals.costs, locale))
        val conversion = row.conversion ?: return@SectionCard
        val converted = conversion.total
        if (converted != null) {
            LabeledValue(
                stringResource(R.string.overview_converted, converted.currency.currencyCode),
                formatAmounts(listOf(converted), locale),
            )
        } else {
            Text(
                stringResource(
                    R.string.overview_missing_rates,
                    conversion.missing.joinToString(", ") { it.currencyCode },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
