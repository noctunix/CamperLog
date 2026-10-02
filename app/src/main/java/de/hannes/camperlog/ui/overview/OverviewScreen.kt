package de.hannes.camperlog.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hannes.camperlog.R
import de.hannes.camperlog.domain.TourTotals
import de.hannes.camperlog.domain.YearTotals
import de.hannes.camperlog.domain.formatEuro
import de.hannes.camperlog.ui.BackTopBar
import de.hannes.camperlog.ui.EmptyHint
import de.hannes.camperlog.ui.LabeledValue
import de.hannes.camperlog.ui.SectionCard

/** Kennzahlen über alle Touren und je Jahr, neuestes Jahr zuerst. */
@Composable
fun OverviewScreen(viewModel: OverviewViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar(title = stringResource(R.string.overview_title), onBack = onBack) }) { padding ->
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
                total == null || total.tours == 0 -> item { EmptyHint(stringResource(R.string.overview_empty)) }
                else -> {
                    item { TotalsCard(stringResource(R.string.overview_total), total) }
                    items(state.years, key = YearTotals::year) { TotalsCard(it.year.toString(), it.totals) }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(title: String, totals: TourTotals) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        LabeledValue(stringResource(R.string.overview_tours), totals.tours.toString())
        LabeledValue(stringResource(R.string.field_distance), stringResource(R.string.distance_km, totals.distanceKm))
        LabeledValue(stringResource(R.string.field_travel_days), totals.travelDays.toString())
        LabeledValue(stringResource(R.string.field_overnight_stays), totals.overnightStays.toString())
        LabeledValue(stringResource(R.string.field_cost), formatEuro(totals.costCents))
    }
}
